<?php

namespace App\Http\Controllers\Dashboard;

use App\Models\Vehicle;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class LiveMapController
{
    public function show(): View
    {
        return view('dashboard.live-map');
    }

    public function vehicles(Request $request): JsonResponse
    {
        $companyId = $request->user('web')->company_id;
        $vehicles = Vehicle::query()
            ->where('company_id', $companyId)
            ->with([
                'currentAssignment' => fn ($query) => $query->where('company_id', $companyId)
                    ->whereNull('ended_at')->select('id', 'vehicle_id', 'driver_id'),
                'currentAssignment.driver' => fn ($query) => $query->where('company_id', $companyId)
                    ->select('id', 'full_name'),
            ])
            ->orderBy('plate')
            ->get(['id', 'plate', 'brand', 'model'])
            ->map(fn (Vehicle $vehicle) => [
                'id' => $vehicle->id,
                'name' => trim($vehicle->brand.' '.$vehicle->model),
                'plate' => $vehicle->plate,
                'driver' => $vehicle->currentAssignment?->driver?->full_name,
                // The existing coordinates belong to events, not a periodic vehicle feed.
                'position' => null,
            ]);

        return response()->json(['vehicles' => $vehicles, 'updated_at' => now()->toISOString()]);
    }
}
