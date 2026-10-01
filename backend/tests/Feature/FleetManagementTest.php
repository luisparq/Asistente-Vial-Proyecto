<?php

namespace Tests\Feature;

use App\Models\AccessInvitation;
use App\Models\AdministrativeAudit;
use App\Models\Company;
use App\Models\DocumentType;
use App\Models\Driver;
use App\Models\DriverDocument;
use App\Models\EmergencyContact;
use App\Models\User;
use App\Models\Vehicle;
use App\Models\VehicleAssignment;
use App\Services\AccessInvitations;
use App\Services\VehicleAssignments;
use Illuminate\Database\QueryException;
use Illuminate\Foundation\Http\Middleware\ValidateCsrfToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Tests\TestCase;

/** TEST ONLY: every fixture lives in phpunit's isolated SQLite database. */
class FleetManagementTest extends TestCase
{
    use RefreshDatabase;

    public function test_new_mutations_require_csrf_and_guest_cannot_read_personal_pages(): void
    {
        $this->app->bind(ValidateCsrfToken::class, fn ($app) => new class($app, $app['encrypter']) extends ValidateCsrfToken
        {
            protected function runningUnitTests()
            {
                return false;
            }
        });
        foreach (['/dashboard/drivers', '/dashboard/vehicles', '/platform/users', '/platform/companies', '/access'] as $url) {
            $this->post($url, [])->assertStatus(419);
        }
        foreach (['/dashboard/drivers', '/dashboard/vehicles', '/dashboard/documents/1'] as $url) {
            $this->get($url)->assertRedirect('/dashboard/login');
        }
    }

    public function test_platform_provision_is_explicit_and_preserves_existing_accounts(): void
    {
        $company = Company::factory()->create();
        $this->artisan('platform:provision', ['--company-id' => $company->id])
            ->expectsQuestion('Nombre', 'TEST Platform')
            ->expectsQuestion('Email', 'platform-test@example.test')
            ->expectsQuestion('Contraseña (mínimo 12 caracteres)', 'TEST platform password')
            ->expectsQuestion('Repite la contraseña', 'TEST platform password')
            ->assertSuccessful();
        $this->assertDatabaseHas('users', ['email' => 'platform-test@example.test', 'role' => 'platform_admin', 'company_id' => $company->id]);
        $this->assertDatabaseCount('personal_access_tokens', 0);
        $this->assertDatabaseHas('administrative_audits', ['action' => 'platform_admin.provisioned']);
        $this->artisan('platform:provision', ['--company-id' => 999])->assertFailed();
    }

    private function admin(?Company $company = null, string $role = 'fleet_admin'): User
    {
        return User::factory()->create(['company_id' => ($company ?? Company::factory()->create())->id, 'role' => $role]);
    }

    private function driver(User $admin): Driver
    {
        $user = $this->admin($admin->company, 'driver');
        $d = new Driver;
        $d->forceFill(['company_id' => $admin->company_id, 'user_id' => $user->id, 'full_name' => 'TEST conductor', 'status' => 'ACTIVE'])->save();

        return $d;
    }

    private function vehicle(User $admin, string $plate = 'TEST-ONLY'): Vehicle
    {
        $v = new Vehicle;
        $v->forceFill(['company_id' => $admin->company_id, 'plate' => $plate, 'brand' => 'TEST', 'model' => 'TEST', 'status' => 'ACTIVE'])->save();

        return $v;
    }

    private function token(User $user): string
    {
        return $user->createToken('TEST', $user->capabilities())->plainTextToken;
    }

    public function test_platform_authentication_and_separate_areas(): void
    {
        $platform = $this->admin(null, 'platform_admin');
        $fleet = $this->admin();
        $this->get('/platform')->assertRedirect('/platform/login');
        $this->post('/platform/login', ['email' => $fleet->email, 'password' => 'password'])->assertSessionHasErrors();
        $this->post('/platform/login', ['email' => $platform->email, 'password' => 'password'])->assertRedirect('/platform');
        foreach (['/platform', '/platform/companies', '/platform/users', '/platform/reports', '/platform/audits'] as $url) {
            $this->get($url)->assertOk();
        }
        $this->get('/dashboard')->assertForbidden();
        $this->postJson('/api/v1/auth/token', ['email' => $platform->email, 'password' => 'password'])->assertUnauthorized();
        $this->actingAs($fleet)->get('/platform')->assertForbidden();
    }

    public function test_platform_global_company_reads_and_creation_are_audited(): void
    {
        $p = $this->admin(null, 'platform_admin');
        $other = $this->admin();
        $d = $this->driver($other);
        $v = $this->vehicle($other);
        $this->actingAs($p)->get('/platform/companies/'.$other->company_id)->assertOk()->assertSee($d->full_name)->assertSee($v->plate)->assertSee($other->email);
        $this->post('/platform/companies', ['name' => 'TEST additional company'])->assertRedirect();
        $this->assertDatabaseHas('administrative_audits', ['action' => 'company.created', 'actor_id' => $p->id]);
    }

    public function test_platform_can_create_fleet_admin_but_cannot_escalate_role(): void
    {
        $p = $this->admin(null, 'platform_admin');
        $c = Company::factory()->create();
        $this->actingAs($p)->post('/platform/users', ['company_id' => $c->id, 'name' => 'TEST admin', 'email' => 'test-admin@example.test', 'role' => 'platform_admin'])->assertRedirect()->assertSessionHas('activation_code');
        $this->assertDatabaseHas('users', ['email' => 'test-admin@example.test', 'role' => 'fleet_admin', 'access_status' => 'PENDING']);
        $this->post('/platform/users/'.$p->id.'/access', ['action' => 'disable'])->assertForbidden();
    }

    public function test_company_disabled_blocks_existing_tokens_and_web_without_deleting_data(): void
    {
        $p = $this->admin(null, 'platform_admin');
        $a = $this->admin();
        $token = $this->token($a);
        $this->actingAs($p)->post('/platform/companies/'.$a->company_id.'/state', ['status' => 'INACTIVE'])->assertRedirect();
        $this->actingAs($a)->get('/dashboard')->assertForbidden();
        auth()->forgetGuards();
        $this->withToken($token)->getJson('/api/v1/me')->assertForbidden();
        $this->assertDatabaseCount('personal_access_tokens', 1);
        $this->assertDatabaseCount('users', 2);
    }

    public function test_create_driver_contact_vehicle_and_single_use_activation(): void
    {
        $a = $this->admin();
        $v = $this->vehicle($a);
        $response = $this->actingAs($a)->post('/dashboard/drivers', ['full_name' => 'TEST Driver', 'email' => 'driver@example.test', 'vehicle_id' => $v->id, 'contact_name' => 'TEST contact', 'contact_relationship' => 'TEST relationship', 'contact_phone' => 'TEST phone', 'role' => 'platform_admin', 'company_id' => 999]);
        $response->assertRedirect()->assertSessionHas('activation_code');
        $code = session('activation_code');
        $d = Driver::firstOrFail();
        $this->assertSame($a->company_id, $d->company_id);
        $this->assertSame('driver', $d->user->role);
        $this->assertSame('PENDING', $d->user->access_status);
        $this->assertSame($v->id, $d->currentAssignment->vehicle_id);
        $this->assertSame('TEST contact', $d->emergencyContact->name);
        $this->assertNotSame($code, AccessInvitation::first()->token_hash);
        $this->post('/access', ['activation_code' => $code, 'password' => 'TEST password 12345', 'password_confirmation' => 'TEST password 12345'])->assertRedirect('/access')->assertSessionHasNoErrors();
        $this->assertTrue(Hash::check('TEST password 12345', $d->user->fresh()->password));
        $this->post('/access', ['activation_code' => $code, 'password' => 'TEST other password', 'password_confirmation' => 'TEST other password'])->assertSessionHasErrors('activation_code');
        $this->postJson('/api/v1/auth/token', ['email' => 'driver@example.test', 'password' => 'TEST password 12345'])->assertCreated();
        $audits = AdministrativeAudit::all()->toJson();
        $this->assertStringNotContainsString($code, $audits);
        $this->assertStringNotContainsString('TEST password', $audits);
        $this->assertStringNotContainsString('TEST phone', $audits);
    }

    public function test_activation_expiry_and_disabled_account_are_rejected_and_codes_not_flashed(): void
    {
        $a = $this->admin();
        $d = $this->driver($a);
        $code = app(AccessInvitations::class)->issue($a, $d->user);
        $this->travel(25)->hours();
        $this->post('/access', ['activation_code' => $code, 'password' => 'TEST long password', 'password_confirmation' => 'TEST long password'])->assertSessionHasErrors('activation_code');
        $this->assertNull(session('_old_input.activation_code'));
        $this->travelBack();
        $d->user->forceFill(['access_status' => 'DISABLED'])->save();
        $this->post('/access', ['activation_code' => $code, 'password' => 'TEST long password', 'password_confirmation' => 'TEST long password'])->assertSessionHasErrors();
    }

    public function test_password_reset_revokes_tokens_and_existing_web_session(): void
    {
        $a = $this->admin();
        $this->token($a);
        $code = app(AccessInvitations::class)->issue($a, $a);
        $this->post('/dashboard/login', ['email' => $a->email, 'password' => 'password'])->assertRedirect();
        $this->get('/dashboard')->assertOk();
        $this->post('/access', ['activation_code' => $code, 'password' => 'TEST replacement password', 'password_confirmation' => 'TEST replacement password'])->assertRedirect();
        $this->assertDatabaseCount('personal_access_tokens', 0);
        auth()->forgetGuards();
        $this->get('/dashboard')->assertRedirect('/dashboard/login');
    }

    public function test_driver_management_and_company_isolation(): void
    {
        $a = $this->admin();
        $d = $this->driver($a);
        $other = $this->admin();
        $this->actingAs($a);
        foreach (['/dashboard/drivers', '/dashboard/drivers/create', '/dashboard/drivers/'.$d->id, '/dashboard/drivers/'.$d->id.'/edit'] as $url) {
            $this->get($url)->assertOk();
        }
        $this->put('/dashboard/drivers/'.$d->id, ['full_name' => 'TEST edited', 'status' => 'ACTIVE', 'contact_name' => 'TEST emergency', 'contact_relationship' => 'TEST', 'contact_phone' => '123'])->assertRedirect()->assertSessionHasNoErrors();
        $this->assertDatabaseHas('emergency_contacts', ['driver_id' => $d->id, 'name' => 'TEST emergency']);
        $this->actingAs($other)->get('/dashboard/drivers/'.$d->id)->assertNotFound();
        $this->put('/dashboard/drivers/'.$d->id, ['full_name' => 'intrusion', 'status' => 'ACTIVE'])->assertNotFound();
        $this->post('/dashboard/drivers/'.$d->id.'/access', ['action' => 'invite'])->assertNotFound();
        $this->actingAs($d->user)->get('/dashboard/drivers')->assertForbidden();
    }

    public function test_inactive_driver_closes_assignment_and_disables_access(): void
    {
        $a = $this->admin();
        $d = $this->driver($a);
        $v = $this->vehicle($a);
        app(VehicleAssignments::class)->assign($a, $d, $v->id);
        $this->actingAs($a)->put('/dashboard/drivers/'.$d->id, ['full_name' => $d->full_name, 'status' => 'INACTIVE'])->assertRedirect();
        $this->assertNull($d->fresh()->currentAssignment);
        $this->assertSame('DISABLED', $d->user->fresh()->access_status);
        $this->post('/dashboard/drivers/'.$d->id.'/access', ['action' => 'enable'])->assertSessionHasErrors();
    }

    public function test_vehicle_crud_uniqueness_and_tenant_isolation(): void
    {
        $a = $this->admin();
        $b = $this->admin();
        $this->actingAs($a);
        $this->post('/dashboard/vehicles', ['plate' => 'test-01', 'brand' => 'TEST', 'model' => 'TEST', 'status' => 'ACTIVE', 'company_id' => $b->company_id])->assertRedirect();
        $v = Vehicle::first();
        $this->assertSame('TEST-01', $v->plate);
        $this->assertSame($a->company_id, $v->company_id);
        foreach (['/dashboard/vehicles', '/dashboard/vehicles/create', '/dashboard/vehicles/'.$v->id, '/dashboard/vehicles/'.$v->id.'/edit'] as $url) {
            $this->get($url)->assertOk();
        }
        $this->post('/dashboard/vehicles', ['plate' => 'TEST-01', 'brand' => 'TEST', 'model' => 'TEST', 'status' => 'ACTIVE'])->assertSessionHasErrors('plate');
        $this->put('/dashboard/vehicles/'.$v->id, ['plate' => 'TEST-02', 'brand' => 'TEST changed', 'model' => 'TEST', 'status' => 'INACTIVE'])->assertRedirect();
        $this->actingAs($b)->get('/dashboard/vehicles/'.$v->id)->assertNotFound();
        $this->put('/dashboard/vehicles/'.$v->id, [])->assertNotFound();
    }

    public function test_assignment_reassignment_preserves_history_and_rejects_conflicts(): void
    {
        $a = $this->admin();
        $d = $this->driver($a);
        $d2 = $this->driver($a);
        $v = $this->vehicle($a);
        $v2 = $this->vehicle($a, 'TEST-2');
        $other = $this->vehicle($this->admin());
        $this->actingAs($a)->post('/dashboard/drivers/'.$d->id.'/assignment', ['vehicle_id' => $v->id])->assertRedirect()->assertSessionHasNoErrors();
        $this->post('/dashboard/drivers/'.$d2->id.'/assignment', ['vehicle_id' => $v->id])->assertSessionHasErrors('vehicle_id');
        $this->post('/dashboard/drivers/'.$d->id.'/assignment', ['vehicle_id' => $other->id])->assertNotFound();
        $this->post('/dashboard/drivers/'.$d->id.'/assignment', ['vehicle_id' => $v2->id])->assertRedirect();
        $this->assertDatabaseCount('vehicle_assignments', 2);
        $this->assertNotNull(VehicleAssignment::where('vehicle_id', $v->id)->first()->ended_at);
        $this->post('/dashboard/drivers/'.$d->id.'/assignment', ['vehicle_id' => $v2->id])->assertRedirect();
        $this->assertDatabaseCount('vehicle_assignments', 2);
        $this->put('/dashboard/vehicles/'.$v2->id, ['plate' => $v2->plate, 'brand' => 'TEST', 'model' => 'TEST', 'status' => 'INACTIVE'])->assertSessionHasErrors('status');
    }

    public function test_assignment_database_constraint_is_a_second_defence(): void
    {
        $a = $this->admin();
        $d = $this->driver($a);
        $d2 = $this->driver($a);
        $v = $this->vehicle($a);
        app(VehicleAssignments::class)->assign($a, $d, $v->id);
        $this->expectException(QueryException::class);
        VehicleAssignment::create(['company_id' => $a->company_id, 'driver_id' => $d2->id, 'vehicle_id' => $v->id, 'started_at' => now(), 'active_slot' => 1]);
    }

    public function test_documents_are_private_validated_reviewed_and_tenant_scoped(): void
    {
        Storage::fake('driver_documents');
        $a = $this->admin();
        $d = $this->driver($a);
        $type = DocumentType::first();
        $file = UploadedFile::fake()->createWithContent('test.pdf', "%PDF-1.4\nTEST ONLY\n%%EOF");
        $this->actingAs($a)->post('/dashboard/drivers/'.$d->id.'/documents', ['document_type_id' => $type->id, 'document' => $file, 'expires_on' => '2020-01-01'])->assertRedirect()->assertSessionHasNoErrors();
        $doc = DriverDocument::firstOrFail();
        Storage::disk('driver_documents')->assertExists($doc->path);
        $this->assertTrue($doc->expired());
        $this->get('/dashboard/documents/'.$doc->id)->assertOk()->assertDownload('documento-'.$doc->id.'.pdf');
        $this->post('/dashboard/documents/'.$doc->id.'/review', ['review_status' => 'VERIFIED'])->assertRedirect();
        $this->assertSame('VERIFIED', $doc->fresh()->review_status);
        $this->post('/dashboard/drivers/'.$d->id.'/documents', ['document_type_id' => $type->id, 'document' => UploadedFile::fake()->create('test.php', 1, 'text/x-php')])->assertSessionHasErrors('document');
        $this->post('/dashboard/drivers/'.$d->id.'/documents', ['document_type_id' => $type->id, 'document' => UploadedFile::fake()->create('large.pdf', 5121, 'application/pdf')])->assertSessionHasErrors('document');
        $this->actingAs($this->admin())->get('/dashboard/documents/'.$doc->id)->assertNotFound();
        $this->post('/dashboard/documents/'.$doc->id.'/review', ['review_status' => 'REJECTED'])->assertNotFound();
        $this->get('/storage/'.$doc->path)->assertForbidden();
        $this->actingAs($d->user)->get('/dashboard/documents/'.$doc->id)->assertForbidden();
    }

    public function test_profile_contains_only_own_contact_documents_vehicle_and_no_private_paths(): void
    {
        $a = $this->admin();
        $d = $this->driver($a);
        $other = $this->driver($a);
        $v = $this->vehicle($a);
        app(VehicleAssignments::class)->assign($a, $d, $v->id);
        $contact = new EmergencyContact;
        $contact->forceFill(['company_id' => $a->company_id, 'driver_id' => $d->id, 'name' => 'TEST own contact', 'relationship' => 'TEST', 'phone' => '123'])->save();
        $doc = new DriverDocument;
        $doc->forceFill(['company_id' => $a->company_id, 'driver_id' => $d->id, 'document_type_id' => DocumentType::first()->id, 'path' => 'PRIVATE_TEST_PATH', 'mime' => 'application/pdf', 'size' => 10, 'review_status' => 'PENDING'])->save();
        $this->withToken($this->token($d->user))->getJson('/api/v1/me/driver?user_id='.$other->user_id)->assertOk()->assertJsonPath('data.email', $d->user->email)->assertJsonPath('data.vehicle.plate', $v->plate)->assertJsonPath('data.emergency_contact.name', 'TEST own contact')->assertJsonCount(1, 'data.documents')->assertDontSee('PRIVATE_TEST_PATH')->assertDontSee($other->user->email)->assertHeader('Cache-Control', 'no-store, private');
        $this->getJson('/api/v1/drivers/'.$other->id)->assertNotFound();
    }

    public function test_profile_empty_fields_and_missing_profile_and_authentication(): void
    {
        $a = $this->admin();
        $d = $this->driver($a);
        $this->getJson('/api/v1/me/driver')->assertUnauthorized();
        $this->withToken($this->token($d->user))->getJson('/api/v1/me/driver')->assertOk()->assertJsonPath('data.vehicle', null)->assertJsonPath('data.emergency_contact', null)->assertJsonCount(0, 'data.documents');
        auth()->forgetGuards();
        $member = $this->admin(null, 'member');
        $this->withToken($this->token($member))->getJson('/api/v1/me/driver')->assertNotFound();
        auth()->forgetGuards();
        $this->withToken($this->token($a))->getJson('/api/v1/me/driver')->assertForbidden();
    }

    public function test_profile_company_foreign_key_prevents_cross_company_user(): void
    {
        $a = $this->admin();
        $b = $this->admin();
        $this->expectException(QueryException::class);
        DB::table('drivers')->insert(['company_id' => $a->company_id, 'user_id' => $b->id, 'full_name' => 'TEST invalid', 'status' => 'ACTIVE']);
    }

    public function test_legacy_roles_and_nullable_trip_context_remain_compatible(): void
    {
        $member = $this->admin(null, 'member');
        $this->assertSame(['trips:read', 'trips:write'], $member->capabilities());
        $token = $this->token($member);
        $uuid = (string) Str::uuid();
        $this->withToken($token)->putJson('/api/v1/trips/'.$uuid, ['started_at' => '2026-09-30T02:00:00.000Z', 'ended_at' => null, 'status' => 'ACTIVE', 'client_revision' => 1])->assertCreated();
        $this->assertDatabaseHas('trips', ['client_trip_id' => $uuid, 'driver_id' => null, 'vehicle_id' => null]);
        $this->getJson('/api/v1/me')->assertOk()->assertJsonPath('data.user.id', $member->id)->assertJsonPath('data.capabilities', ['trips:read', 'trips:write']);
    }

    public function test_reports_filter_actual_activity_and_reject_invalid_dates(): void
    {
        $p = $this->admin(null, 'platform_admin');
        $this->actingAs($p)->get('/platform/reports?from=2026-01-01&to=2026-12-31')->assertOk()->assertSee('Sin eventos en el período');
        $this->get('/platform/reports?from=2026-12-31&to=2026-01-01')->assertSessionHasErrors('to');
    }

    public function test_driver_access_disable_and_reenable_require_activation(): void
    {
        $a = $this->admin();
        $d = $this->driver($a);
        $token = $this->token($d->user);
        $this->actingAs($a)->post('/dashboard/drivers/'.$d->id.'/access', ['action' => 'disable'])->assertRedirect();
        auth()->forgetGuards();
        $this->withToken($token)->getJson('/api/v1/me')->assertForbidden();
        $this->actingAs($a, 'web')->post('/dashboard/drivers/'.$d->id.'/access', ['action' => 'enable'])->assertRedirect()->assertSessionHasNoErrors();
        $this->assertSame('PENDING', $d->user->fresh()->access_status);
    }
}
