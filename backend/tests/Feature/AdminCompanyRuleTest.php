<?php

namespace Tests\Feature;

use App\Models\Company;
use App\Models\User;
use Illuminate\Database\QueryException;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Tests\TestCase;

/** TEST ONLY: fixtures exist only in PHPUnit's isolated, in-memory SQLite. */
class AdminCompanyRuleTest extends TestCase
{
    use RefreshDatabase;

    public function test_database_rejects_companyless_enterprise_roles_and_tenant_bound_platform_admin(): void
    {
        $company = Company::factory()->create();
        foreach (['member', 'driver', 'fleet_admin', 'platform_admin'] as $role) {
            try {
                DB::table('users')->insert(['name' => 'TEST ONLY', 'email' => $role.'@example.test',
                    'password' => 'TEST ONLY non-login value', 'role' => $role,
                    'company_id' => $role === 'platform_admin' ? $company->id : null]);
                $this->fail('Invalid company/role accepted: '.$role);
            } catch (QueryException $error) {
                $this->assertStringContainsString('company_id must match the user role', $error->getMessage());
            }
        }
        $this->assertDatabaseCount('users', 0);
    }

    public function test_database_rejects_removing_company_from_existing_enterprise_user(): void
    {
        $user = User::factory()->create(['role' => 'fleet_admin']);
        $this->expectException(QueryException::class);
        DB::table('users')->where('id', $user->id)->update(['company_id' => null]);
    }

    public function test_model_validates_company_requirement_before_saving(): void
    {
        $this->expectException(ValidationException::class);
        User::factory()->create(['role' => 'driver', 'company_id' => null]);
    }

    public function test_driver_company_api_can_sync_trip_and_simulated_event_without_contract_changes(): void
    {
        $driver = User::factory()->create(['role' => 'driver']);
        $login = $this->postJson('/api/v1/auth/token', ['email' => $driver->email, 'password' => 'password'])->assertCreated();
        auth()->forgetGuards();
        $this->withToken($login->json('data.access_token'));
        $this->getJson('/api/v1/me')->assertOk()->assertJsonPath('data.company.id', $driver->company_id);
        $trip = (string) Str::uuid();
        $event = (string) Str::uuid();
        $payload = ['started_at' => '2026-10-05T10:00:00.000Z', 'ended_at' => null, 'status' => 'ACTIVE', 'client_revision' => 1];
        $this->putJson('/api/v1/trips/'.$trip, $payload)->assertCreated();
        $this->putJson('/api/v1/trips/'.$trip.'/events/'.$event, ['type' => 'HARSH_BRAKING',
            'occurred_at' => '2026-10-05T10:00:10.000Z', 'origin' => 'SIMULATED'])->assertCreated();
        $payload['ended_at'] = '2026-10-05T10:01:00.000Z';
        $payload['status'] = 'COMPLETED';
        $payload['client_revision'] = 2;
        $this->putJson('/api/v1/trips/'.$trip, $payload)->assertOk();
        $this->assertDatabaseHas('trips', ['client_trip_id' => $trip, 'company_id' => $driver->company_id, 'status' => 'COMPLETED']);
        $this->assertDatabaseHas('driving_events', ['client_event_id' => $event, 'origin' => 'SIMULATED']);
    }
}
