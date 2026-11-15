<?php

namespace Tests\Feature;

use App\Models\Company;
use App\Models\Driver;
use App\Models\User;
use App\Models\Vehicle;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class LiveMapTest extends TestCase
{
    use RefreshDatabase;

    public function test_only_fleet_admin_with_active_access_can_consult_the_map(): void
    {
        $page = '/dashboard/live-map';
        $data = $page.'/vehicles';
        $this->get($page)->assertRedirect('/dashboard/login');
        $this->getJson($data)->assertUnauthorized();

        foreach ([
            User::factory()->create(['role' => 'member']),
            User::factory()->create(['role' => 'platform_admin', 'company_id' => null]),
            User::factory()->create(['role' => 'fleet_admin', 'access_status' => 'SUSPENDED']),
            User::factory()->create(['role' => 'fleet_admin', 'company_id' => Company::factory()->create(['status' => 'SUSPENDED'])->id]),
        ] as $user) {
            $this->actingAs($user)->get($page)->assertForbidden();
            $this->getJson($data)->assertForbidden();
        }

        $admin = User::factory()->create(['role' => 'fleet_admin']);
        $response = $this->actingAs($admin)->get($page)->assertOk()
            ->assertSee('Mapa en vivo')->assertSee('live-map.js')
            ->assertSee('Capas viales de Arequipa')->assertSee('Límites de velocidad')
            ->assertSee('Colegios')->assertSee('Tráfico reciente')->assertSee('data-url="/api/v1/road-catalog"', false);
        $this->assertStringContainsString("worker-src 'self' blob:", $response->headers->get('Content-Security-Policy'));
        $this->getJson($data)->assertOk()->assertJsonPath('vehicles', []);
    }

    public function test_snapshot_uses_session_company_and_returns_only_current_assignments(): void
    {
        $admin = User::factory()->create(['role' => 'fleet_admin']);
        $foreign = User::factory()->create(['role' => 'fleet_admin']);
        $localVehicle = $this->assignedVehicle($admin, 'TEST-LOCAL', 'Conductor de prueba local');
        $foreignVehicle = $this->assignedVehicle($foreign, 'TEST-AJENO', 'Conductor de prueba ajeno');
        $unassigned = Vehicle::forceCreate([
            'company_id' => $admin->company_id, 'plate' => 'TEST-SIN-ASIGNAR', 'brand' => 'Marca', 'model' => 'Modelo',
        ]);

        $response = $this->actingAs($admin)->getJson('/dashboard/live-map/vehicles?company_id='.$foreign->company_id.'&vehicle_id='.$foreignVehicle->id);
        $response->assertOk()->assertJsonCount(2, 'vehicles')->assertJsonStructure(['updated_at'])
            ->assertJsonPath('vehicles.0', [
                'id' => $localVehicle->id, 'name' => 'Marca Modelo', 'plate' => 'TEST-LOCAL',
                'driver' => 'Conductor de prueba local', 'position' => null,
            ])
            ->assertJsonPath('vehicles.1.id', $unassigned->id)
            ->assertJsonPath('vehicles.1.driver', null)->assertJsonPath('vehicles.1.position', null)
            ->assertDontSee('TEST-AJENO')->assertDontSee('Conductor de prueba ajeno');
        $this->assertStringContainsString('no-store', $response->headers->get('Cache-Control'));
    }

    private function assignedVehicle(User $admin, string $plate, string $name): Vehicle
    {
        $vehicle = Vehicle::forceCreate([
            'company_id' => $admin->company_id, 'plate' => $plate, 'brand' => 'Marca', 'model' => 'Modelo',
        ]);
        $user = User::factory()->create(['company_id' => $admin->company_id]);
        $driver = Driver::forceCreate([
            'company_id' => $admin->company_id, 'user_id' => $user->id, 'full_name' => $name,
        ]);
        $vehicle->assignments()->create([
            'company_id' => $admin->company_id, 'driver_id' => $driver->id, 'started_at' => now(), 'active_slot' => 1,
        ]);

        return $vehicle;
    }
}
