<?php

namespace App\Services;

use App\Models\Driver;
use App\Models\DrivingEvent;
use App\Models\Trip;
use App\Support\DashboardDisplay;
use Illuminate\Database\Eloquent\Builder;

class DriverScores
{
    public const PENALTY = 5;

    public function leaders(int $companyId)
    {
        return Driver::query()->fromSub($this->query($companyId)->where('status', 'ACTIVE'), 'drivers')
            ->where(fn ($drivers) => $drivers->where('scoring_trips_count', '>', 0)->orWhere('scoring_events_count', '>', 0))
            ->orderBy('scoring_events_count')->orderByDesc('scoring_trips_count')->orderBy('full_name')->orderBy('id')
            ->limit(3)->get();
    }

    public function query(int $companyId): Builder
    {
        $today = now(DashboardDisplay::ZONE)->toImmutable()->startOfDay();
        $since = $today->subDays(29)->utc();
        $until = $today->addDay()->utc();
        $history = Trip::where('trips.company_id', $companyId)->where(function (Builder $trips) {
            $trips->whereColumn('trips.driver_id', 'drivers.id')
                ->orWhere(fn (Builder $account) => $account->whereNull('trips.driver_id')
                    ->whereColumn('trips.created_by_user_id', 'drivers.user_id'));
        });
        $periodEvents = DrivingEvent::where('driving_events.company_id', $companyId)
            ->where('occurred_at', '>=', $since)->where('occurred_at', '<', $until);
        $sensorAvailable = (clone $periodEvents)->where('origin', 'SENSOR')
            ->whereHas('trip', fn (Builder $trip) => $trip->where('company_id', $companyId)
                ->where(fn (Builder $context) => $context->whereNotNull('driver_id')
                    ->orWhereIn('created_by_user_id', Driver::where('company_id', $companyId)->select('user_id'))))
            ->exists();
        $origin = $sensorAvailable ? 'SENSOR' : 'SIMULATED';

        return Driver::where('drivers.company_id', $companyId)
            ->select('drivers.*')->selectRaw('? AS score_origin', [$origin])
            ->addSelect([
                'scoring_trips_count' => (clone $history)->where('started_at', '>=', $since)
                    ->where('started_at', '<', $until)->selectRaw('COUNT(*)'),
                'scoring_events_count' => $periodEvents->where('origin', $origin)
                    ->whereIn('trip_id', $history->select('trips.id'))->selectRaw('COUNT(*)'),
            ]);
    }
}
