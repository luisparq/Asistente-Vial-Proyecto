<?php

namespace App\Services;

use App\Models\DrivingEvent;
use App\Models\Trip;
use App\Models\User;
use App\Support\ApiProblem;
use App\Support\UtcTimestamp;
use Illuminate\Support\Facades\DB;

class TripSync
{
    public function __construct(private TripAccess $access) {}

    private function lockCompany(User $user): void
    {
        // A write lock works on SQLite too (SELECT FOR UPDATE does not).
        // Serialize initial ingestion per company; unique/FK constraints remain the backstop.
        DB::table('companies')->where('id', $user->company_id)->update(['id' => $user->company_id]);
    }

    public function trip(User $user, string $uuid, array $data): array
    {
        return DB::transaction(function () use ($user, $uuid, $data) {
            $this->lockCompany($user);
            $trip = $this->access->writable($user, $uuid, false);
            if (! $trip) {
                $trip = Trip::create($data + ['company_id' => $user->company_id, 'created_by_user_id' => $user->id,
                    'client_trip_id' => $uuid, 'first_received_at' => now(), 'last_changed_at' => now()]);

                return [$trip, 'created'];
            }
            if (UtcTimestamp::format($trip->started_at) !== $data['started_at']) {
                throw new ApiProblem(409, 'trip_conflict', 'El inicio del recorrido es inmutable.');
            }
            if ($data['client_revision'] < $trip->client_revision) {
                return [$trip, 'stale_ignored'];
            }
            if ($data['client_revision'] === $trip->client_revision) {
                if ($trip->status !== $data['status'] || UtcTimestamp::format($trip->ended_at) !== $data['ended_at']) {
                    throw new ApiProblem(409, 'trip_conflict', 'La revisión ya existe con otro contenido.');
                }

                return [$trip, 'already_applied'];
            }
            $trip->fill($data)->forceFill(['last_changed_at' => now()])->save();

            return [$trip, 'updated'];
        }, 5);
    }

    public function event(User $user, string $tripUuid, string $eventUuid, array $data): array
    {
        $data += array_fill_keys(['latitude', 'longitude', 'location_origin', 'gps_utc_seconds', 'gps_received_at', 'gps_age_ms'], null);
        foreach (['latitude', 'longitude'] as $key) if ($data[$key] !== null) $data[$key] = (float) $data[$key];
        foreach (['gps_utc_seconds', 'gps_age_ms'] as $key) if ($data[$key] !== null) $data[$key] = (int) $data[$key];
        return DB::transaction(function () use ($user, $tripUuid, $eventUuid, $data) {
            $this->lockCompany($user);
            $trip = $this->access->writable($user, $tripUuid);
            if ($data['origin'] !== 'SIMULATED') {
                throw new ApiProblem(422, 'origin_not_enabled', 'SENSOR está reservado; solo se admiten datos SIMULATED de prueba.');
            }
            $event = DrivingEvent::where('company_id', $user->company_id)->where('client_event_id', $eventUuid)->first();
            if ($event) {
                // Check visibility of the existing owner before exposing a conflict.
                if ((int) $event->trip->created_by_user_id !== (int) $user->id) {
                    $this->access->visible($user)->whereKey($event->trip_id)->firstOrFail();
                }
                $stored = \App\Support\ApiData::event($event);
                $locationConflict = false;
                foreach (['latitude', 'longitude', 'location_origin', 'gps_utc_seconds', 'gps_received_at', 'gps_age_ms'] as $key) {
                    if ($stored[$key] !== $data[$key]) $locationConflict = true;
                }
                if ($locationConflict || (int) $event->trip_id !== (int) $trip->id || $event->type !== $data['type'] || $event->origin !== $data['origin'] || UtcTimestamp::format($event->occurred_at) !== $data['occurred_at']) {
                    throw new ApiProblem(409, 'event_conflict', 'El evento ya existe con otro contenido o recorrido.');
                }

                return [$event->setRelation('trip', $trip), 'already_applied'];
            }
            $event = DrivingEvent::create($data + ['company_id' => $user->company_id, 'trip_id' => $trip->id,
                'client_event_id' => $eventUuid, 'first_received_at' => now()]);

            return [$event->setRelation('trip', $trip), 'created'];
        }, 5);
    }
}
