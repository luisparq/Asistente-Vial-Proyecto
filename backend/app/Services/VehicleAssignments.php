<?php

namespace App\Services;

use App\Models\Company;
use App\Models\Driver;
use App\Models\User;
use App\Models\Vehicle;
use App\Models\VehicleAssignment;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class VehicleAssignments
{
    public function assign(User $actor, Driver $driver, ?int $vehicleId): void
    {
        DB::transaction(function () use ($actor, $driver, $vehicleId) {
            Company::whereKey($driver->company_id)->update(['name' => DB::raw('name')]);
            $driver->refresh();
            $current = $driver->assignments()->where('active_slot', 1)->first();
            if ($vehicleId !== null) {
                $vehicle = Vehicle::where('company_id', $driver->company_id)->findOrFail($vehicleId);
                if ($driver->status !== 'ACTIVE' || $vehicle->status !== 'ACTIVE') {
                    throw ValidationException::withMessages(['vehicle_id' => 'Conductor y vehículo deben estar activos.']);
                }
                if ($current?->vehicle_id === $vehicleId) {
                    return;
                }
                if ($vehicle->currentAssignment()->exists()) {
                    throw ValidationException::withMessages(['vehicle_id' => 'El vehículo ya está asignado. Finaliza esa asignación primero.']);
                }
            }
            if ($current) {
                $current->update(['ended_at' => now(), 'active_slot' => null]);
            }
            if ($vehicleId !== null) {
                VehicleAssignment::create(['company_id' => $driver->company_id, 'driver_id' => $driver->id, 'vehicle_id' => $vehicleId, 'started_at' => now(), 'active_slot' => 1]);
            }
            AdministrativeAudit::record($actor, $driver->company_id, 'assignment.changed', $driver, ['vehicle_id']);
        }, 5);
    }
}
