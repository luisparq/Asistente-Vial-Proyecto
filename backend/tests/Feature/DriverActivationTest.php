<?php

namespace Tests\Feature;

use App\Models\AccessInvitation;
use App\Models\Driver;
use App\Models\User;
use App\Services\AccessInvitations;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

/** TEST ONLY: isolated PHPUnit SQLite, no real accounts or credentials. */
class DriverActivationTest extends TestCase
{
    use RefreshDatabase;

    private function pending(): array
    {
        $admin = User::factory()->create(['role' => 'fleet_admin']);
        $user = User::factory()->create(['role' => 'driver', 'company_id' => $admin->company_id, 'access_status' => 'PENDING']);
        $driver = new Driver;
        $driver->forceFill(['user_id' => $user->id, 'company_id' => $admin->company_id, 'full_name' => 'TEST ONLY driver', 'status' => 'ACTIVE'])->save();

        return [$admin, $user, $driver];
    }

    private function payload(string $code, array $changes = []): array
    {
        return array_replace(['activation_code' => $code, 'password' => 'TEST ONLY password 123', 'password_confirmation' => 'TEST ONLY password 123'], $changes);
    }

    public function test_mobile_activation_is_single_use_hashed_expires_in_24_hours_and_allows_normal_login(): void
    {
        [$admin, $user] = $this->pending();
        $user->createToken('TEST ONLY obsolete', ['trips:read']);
        $code = app(AccessInvitations::class)->issue($admin, $user);
        $invitation = AccessInvitation::firstOrFail();
        $this->assertSame(hash('sha256', $code), $invitation->token_hash);
        $this->assertSame(86400, $invitation->expires_at->timestamp - $invitation->created_at->timestamp);
        $this->postJson('/api/v1/auth/activate', $this->payload($code))->assertNoContent()->assertHeader('Cache-Control', 'no-store, private');
        $this->assertSame('ACTIVE', $user->fresh()->access_status);
        $this->assertTrue(Hash::check('TEST ONLY password 123', $user->fresh()->password));
        $this->assertNotNull($invitation->fresh()->consumed_at);
        $this->assertDatabaseCount('personal_access_tokens', 0);
        $this->postJson('/api/v1/auth/activate', $this->payload($code))->assertStatus(422);
        $this->postJson('/api/v1/auth/token', ['email' => $user->email, 'password' => 'TEST ONLY password 123'])->assertCreated();
    }

    public function test_password_confirmation_and_length_fail_without_consuming_the_code(): void
    {
        [$admin, $user] = $this->pending();
        $code = app(AccessInvitations::class)->issue($admin, $user);
        $this->postJson('/api/v1/auth/activate', $this->payload($code, ['password_confirmation' => 'different']))->assertStatus(422)->assertJsonPath('error.code', 'validation_failed');
        $this->postJson('/api/v1/auth/activate', $this->payload($code, ['password' => 'short', 'password_confirmation' => 'short']))->assertStatus(422);
        $this->assertNull(AccessInvitation::first()->consumed_at);
        $this->assertSame('PENDING', $user->fresh()->access_status);
    }

    public function test_expired_and_superseded_codes_are_rejected(): void
    {
        [$admin, $user] = $this->pending();
        $old = app(AccessInvitations::class)->issue($admin, $user);
        $new = app(AccessInvitations::class)->issue($admin, $user);
        $this->postJson('/api/v1/auth/activate', $this->payload($old))->assertStatus(422);
        $this->travel(25)->hours();
        $this->postJson('/api/v1/auth/activate', $this->payload($new))->assertStatus(422);
        $this->assertSame('PENDING', $user->fresh()->access_status);
    }

    public function test_disabled_account_inactive_profile_or_company_cannot_activate(): void
    {
        foreach (['account', 'profile', 'company'] as $blocked) {
            [$admin, $user, $driver] = $this->pending();
            $code = app(AccessInvitations::class)->issue($admin, $user);
            if ($blocked === 'account') {
                $user->forceFill(['access_status' => 'DISABLED'])->save();
            }
            if ($blocked === 'profile') {
                $driver->update(['status' => 'INACTIVE']);
            }
            if ($blocked === 'company') {
                $admin->company->forceFill(['status' => 'INACTIVE'])->save();
            }
            $this->postJson('/api/v1/auth/activate', $this->payload($code))->assertStatus(422);
            $this->assertNull(AccessInvitation::where('user_id', $user->id)->first()->consumed_at);
        }
    }

    public function test_web_is_not_a_driver_activation_channel_and_mobile_does_not_activate_admins(): void
    {
        [$admin, $user] = $this->pending();
        $code = app(AccessInvitations::class)->issue($admin, $user);
        $this->post('/access', $this->payload($code))->assertSessionHasErrors('activation_code');
        $this->assertNull(AccessInvitation::first()->consumed_at);
        $adminCode = app(AccessInvitations::class)->issue($admin, $admin);
        $this->postJson('/api/v1/auth/activate', $this->payload($adminCode))->assertStatus(422);
        $this->post('/access', $this->payload($adminCode))->assertRedirect('/access')->assertSessionHasNoErrors();
    }

    public function test_dashboard_shows_only_valid_actions_and_code_once_without_web_link(): void
    {
        [$admin, $user, $driver] = $this->pending();
        $url = '/dashboard/drivers/'.$driver->id;
        $this->actingAs($admin)->get($url)->assertOk()->assertSee('Pendiente de activación')->assertSee('Generar código de activación')->assertDontSee('Deshabilitar acceso')->assertDontSee('Permitir nueva activación');
        $this->post($url.'/access', ['action' => 'disable'])->assertSessionHasErrors('access');
        $this->post($url.'/access', ['action' => 'invite'])->assertRedirect()->assertSessionHasNoErrors();
        $code = session('activation_code');
        $this->get($url)->assertOk()->assertSee($code)->assertSee('Debe introducirlo en la aplicación')->assertDontSee('Activar acceso');
        $this->get($url)->assertDontSee($code);
        app(AccessInvitations::class)->consume($code, 'TEST ONLY password 123', 'android');
        $this->get($url)->assertSee('Deshabilitar acceso')->assertDontSee('Generar código de activación')->assertDontSee('Permitir nueva activación');
        $this->post($url.'/access', ['action' => 'invite'])->assertSessionHasErrors('access');
        $this->post($url.'/access', ['action' => 'disable'])->assertRedirect()->assertSessionHasNoErrors();
        $this->get($url)->assertSee('Permitir nueva activación')->assertDontSee('Generar código de activación')->assertDontSee('Deshabilitar acceso');
        $this->post($url.'/access', ['action' => 'enable'])->assertRedirect()->assertSessionHasNoErrors();
        $this->assertSame('PENDING', $user->fresh()->access_status);
        $other = User::factory()->create(['role' => 'fleet_admin']);
        $this->actingAs($other)->post($url.'/access', ['action' => 'invite'])->assertNotFound();
        $this->actingAs($user)->get($url)->assertForbidden();
    }

    public function test_activation_endpoint_is_rate_limited(): void
    {
        for ($i = 0; $i < 6; $i++) {
            $this->postJson('/api/v1/auth/activate', $this->payload(str_repeat('a', 64)))->assertStatus(422);
        }
        $this->postJson('/api/v1/auth/activate', $this->payload(str_repeat('a', 64)))->assertStatus(429)->assertHeader('Retry-After');
    }
}
