<?php

namespace App\Services;

use App\Models\Trip;
use App\Models\DrivingEvent;
use App\Models\Driver;
use App\Models\Vehicle;

class FleetAnalyticsService
{
    /**
     * Calcula la puntuación del viaje (0 a 100).
     */
    public function calculateTripScore(Trip $trip): int
    {
        $baseScore = 100;
        $events = $trip->events;

        if ($events->isEmpty()) {
            return $baseScore;
        }

        $penalties = 0;

        foreach ($events as $event) {
            $penalties += match ($event->type) {
                'HARSH_BRAKING' => 5,
                'HARSH_ACCELERATION' => 3,
                'SPEEDING' => 10,
                'DISTRACTED_DRIVING' => 15,
                default => 2,
            };
        }

        return max(0, $baseScore - $penalties);
    }

    /**
     * Retorna métricas generales para la empresa.
     */
    public function getCompanyDashboardStats(int $companyId): array
    {
        $totalDrivers = Driver::where('company_id', $companyId)->where('status', 'ACTIVE')->count();
        $totalVehicles = Vehicle::where('company_id', $companyId)->where('status', 'ACTIVE')->count();
        $totalTrips = Trip::where('company_id', $companyId)->count();

        $totalEvents = DrivingEvent::where('company_id', $companyId)->count();
        $eventsByType = DrivingEvent::where('company_id', $companyId)
            ->selectRaw('type, count(*) as total')
            ->groupBy('type')
            ->pluck('total', 'type')
            ->toArray();

        return [
            'active_drivers' => $totalDrivers,
            'active_vehicles' => $totalVehicles,
            'total_trips' => $totalTrips,
            'total_events' => $totalEvents,
            'events_breakdown' => $eventsByType,
        ];
    }
}