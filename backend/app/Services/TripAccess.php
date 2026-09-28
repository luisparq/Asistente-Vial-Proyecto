<?php

namespace App\Services;

use App\Models\Trip;
use App\Models\User;
use App\Support\ApiProblem;
use Illuminate\Database\Eloquent\Builder;

class TripAccess
{
    public function requireAbility(User $user, string $ability): void
    {
        if (! $user->permits($ability)) {
            throw new ApiProblem(403, 'forbidden', 'Operación no autorizada.');
        }
    }

    public function visible(User $user): Builder
    {
        $this->requireAbility($user, 'trips:read');

        return Trip::query()->where('company_id', $user->company_id)
            ->when(! $user->permits('trips:read-company'), fn (Builder $query) => $query->where('created_by_user_id', $user->id));
    }

    public function readable(User $user, string $uuid): Trip
    {
        return $this->visible($user)->where('client_trip_id', $uuid)->firstOrFail();
    }

    public function writable(User $user, string $uuid, bool $mustExist = true): ?Trip
    {
        $this->requireAbility($user, 'trips:write');
        $trip = Trip::where('company_id', $user->company_id)->where('client_trip_id', $uuid)->first();
        if ($trip && (int) $trip->created_by_user_id !== (int) $user->id) {
            throw new ApiProblem($user->permits('trips:read-company') ? 403 : 404, 'resource_unavailable', 'Recurso no disponible.');
        }
        if (! $trip && $mustExist) {
            throw new ApiProblem(404, 'not_found', 'Recurso no disponible.');
        }

        return $trip;
    }
}
