<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Trip;
use App\Services\FleetAnalyticsService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class TelemetryController extends Controller
{
    public function __construct(
        protected FleetAnalyticsService $analyticsService
    ) {}

    /**
     * Ingesta masiva de eventos telemétricos desde la app móvil.
     */
    public function syncBatch(Request $request, Trip $trip): JsonResponse
    {
        $validated = $request->validate([
            'events' => 'nullable|array',
            'events.*.client_event_id' => 'required|uuid',
            'events.*.type' => 'required|string',
            'events.*.occurred_at' => 'required|date',
        ]);

        if (!empty($validated['events'])) {
            foreach ($validated['events'] as $eventData) {
                $trip->events()->firstOrCreate(
                    ['client_event_id' => $eventData['client_event_id']],
                    [
                        'company_id' => $trip->company_id,
                        'type' => $eventData['type'],
                        'origin' => 'APP',
                        'occurred_at' => $eventData['occurred_at'],
                        'first_received_at' => now(),
                    ]
                );
            }
        }

        $trip->load('events');
        $score = $this->analyticsService->calculateTripScore($trip);

        return response()->json([
            'status' => 'success',
            'message' => 'Batch procesado correctamente',
            'trip_id' => $trip->id,
            'current_score' => $score,
            'synced_at' => now()->toIso8601String(),
        ], 200);
    }

    /**
     * Estadísticas consolidadas para el Dashboard.
     */
    public function dashboardStats(Request $request): JsonResponse
    {
        $user = $request->user();
        $companyId = $user->company_id;

        if (!$companyId && $user->role !== 'platform_admin') {
            return response()->json(['error' => 'Usuario no pertenece a una empresa registrada'], 403);
        }

        $targetCompanyId = $companyId ?? $request->query('company_id', 1);

        $stats = $this->analyticsService->getCompanyDashboardStats((int)$targetCompanyId);

        return response()->json([
            'status' => 'success',
            'data' => $stats,
        ]);
    }
}