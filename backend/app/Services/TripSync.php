<?php

namespace App\Services;

use App\Models\Driver;
use App\Models\DrivingEvent;
use App\Models\ManeuverAlert;
use App\Models\VehicleAssignment;
use App\Models\Trip;
use App\Models\User;
use App\Support\ApiData;
use App\Support\ApiProblem;
use Carbon\CarbonImmutable;
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

    public function alert(User $user, string $tripUuid, string $alertUuid, array $data): array
    {
        $data += ['latitude' => null, 'longitude' => null, 'location_captured_at' => null];
        foreach (['latitude', 'longitude'] as $key) {
            $data[$key] = $data[$key] === null ? null : (float) $data[$key];
        }
        $data['event_ids'] = array_map('strtolower', $data['event_ids']);
        sort($data['event_ids']);

        return DB::transaction(function () use ($user, $tripUuid, $alertUuid, $data) {
            $this->lockCompany($user);
            $trip = $this->access->writable($user, $tripUuid);
            $existing = ManeuverAlert::where('company_id', $user->company_id)->where('client_alert_id', $alertUuid)->first();

            if ($existing) {
                if ($existing->trip_id !== $trip->id) {
                    $this->access->visible($user)->whereKey($existing->trip_id)->firstOrFail();
                }
                $expected = $data + ['client_alert_id' => $alertUuid, 'client_trip_id' => $tripUuid];
                $stored = ApiData::alert($existing);
                ksort($stored);
                ksort($expected);
                if ($stored !== $expected) {
                    throw new ApiProblem(409, 'alert_conflict', 'La alerta ya existe con otro contenido o recorrido.');
                }

                return [$existing, 'already_applied'];
            }

            $events = $trip->events()->where('company_id', $user->company_id)
                ->whereIn('client_event_id', $data['event_ids'])->get();
            if ($events->count() !== count($data['event_ids'])) {
                throw new ApiProblem(425, 'alert_events_pending', 'Las maniobras deben recibirse antes de la alerta.');
            }
            if (! $data['test_only'] && $events->contains('origin', 'SIMULATED')) {
                throw new ApiProblem(422, 'test_alert_required', 'Una alerta con maniobras simuladas debe marcarse como prueba.');
            }
            if ($events->contains(fn ($event) => UtcTimestamp::format($event->occurred_at) > $data['occurred_at'])) {
                throw new ApiProblem(422, 'alert_before_event', 'La alerta no puede preceder a sus maniobras.');
            }

            $driverId = $trip->driver_id ?? Driver::where('company_id', $user->company_id)
                ->where('user_id', $trip->created_by_user_id)->value('id');
            $occurredAt = CarbonImmutable::parse($data['occurred_at']);
            $vehicleId = $trip->vehicle_id ?? ($driverId ? VehicleAssignment::where('company_id', $user->company_id)
                ->where('driver_id', $driverId)->where('started_at', '<=', $occurredAt)
                ->where(fn ($query) => $query->whereNull('ended_at')->orWhere('ended_at', '>', $occurredAt))
                ->latest('started_at')->orderByDesc('id')->value('vehicle_id') : null);

            $alert = ManeuverAlert::create(collect($data)->except('event_ids')->all() + [
                'company_id' => $user->company_id, 'trip_id' => $trip->id, 'driver_id' => $driverId,
                'vehicle_id' => $vehicleId, 'client_alert_id' => $alertUuid, 'first_received_at' => now(),
            ]);
            $alert->events()->attach($events->modelKeys());

            return [$alert->setRelation('trip', $trip)->setRelation('events', $events), 'created'];
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
