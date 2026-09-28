<?php

namespace App\Support;

use App\Models\DrivingEvent;
use App\Models\Trip;

class ApiData
{
    public static function trip(Trip $trip, bool $summary = false): array
    {
        $data = ['client_trip_id' => $trip->client_trip_id, 'started_at' => UtcTimestamp::format($trip->started_at),
            'ended_at' => UtcTimestamp::format($trip->ended_at), 'status' => $trip->status,
            'client_revision' => $trip->client_revision, 'first_received_at' => UtcTimestamp::format($trip->first_received_at),
            'last_changed_at' => UtcTimestamp::format($trip->last_changed_at)];
        if ($summary) {
            $issues = [];
            if ($trip->ended_at && $trip->ended_at->lt($trip->started_at)) {
                $issues[] = 'end_before_start';
            }
            if ($trip->events()->where('company_id', $trip->company_id)->where('occurred_at', '<', $trip->started_at->format('Y-m-d H:i:s.v'))->exists()) {
                $issues[] = 'event_before_start';
            }
            if ($trip->ended_at && $trip->events()->where('company_id', $trip->company_id)->where('occurred_at', '>', $trip->ended_at->format('Y-m-d H:i:s.v'))->exists()) {
                $issues[] = 'event_after_end';
            }
            $data['received_event_count'] = $trip->events()->where('company_id', $trip->company_id)->count();
            $data['temporal_issues'] = $issues;
        }

        return $data;
    }

    public static function event(DrivingEvent $event): array
    {
        return ['client_event_id' => $event->client_event_id, 'client_trip_id' => $event->trip->client_trip_id,
            'type' => $event->type, 'origin' => $event->origin, 'occurred_at' => UtcTimestamp::format($event->occurred_at),
            'first_received_at' => UtcTimestamp::format($event->first_received_at)];
    }
}
