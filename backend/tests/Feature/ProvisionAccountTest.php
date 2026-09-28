<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class ProvisionAccountTest extends TestCase
{
    use RefreshDatabase;

    public function test_operator_can_explicitly_provision_a_test_account_without_issuing_token(): void
    {
        $this->artisan('account:provision')
            ->expectsQuestion('Nombre real de la empresa', 'TEST ONLY provision fixture')
            ->expectsQuestion('Nombre del usuario', 'TEST ONLY operator fixture')
            ->expectsQuestion('Email del usuario', 'test-provision@example.invalid')
            ->expectsChoice('Permisos de la cuenta', 'member', ['member', 'fleet_admin'])
            ->expectsQuestion('Contraseña (mínimo 12 caracteres)', 'TEST-only-password-123')
            ->expectsQuestion('Repetir contraseña', 'TEST-only-password-123')
            ->assertSuccessful();
        $this->assertDatabaseCount('companies', 1);
        $this->assertDatabaseCount('users', 1);
        $this->assertDatabaseCount('personal_access_tokens', 0);
        $this->assertTrue(Hash::check('TEST-only-password-123', User::first()->password));
    }
}
