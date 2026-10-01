<?php

namespace App\Services;

use App\Models\DrivingEvent;
use App\Models\Trip;
use App\Models\User;
use App\Support\DashboardDisplay;
use Carbon\CarbonImmutable;
use Illuminate\Database\Eloquent\Builder;

/** Web reads only. Every root query is scoped before filters, aggregates or lookup. */
class DashboardQuery
{
    public function trips(User $user, array $filters = []): Builder
    {
        $query = Trip::query()->where('company_id', $user->company_id);
        foreach (['status' => 'status', 'user_id' => 'created_by_user_id'] as $key => $column) {
            if (! empty($filters[$key])) {
                $query->where($column, $filters[$key]);
            }
        }

        return $this->dates($query, 'started_at', $filters);
    }

    public function events(User $user, array $filters = []): Builder
    {
        $query = DrivingEvent::query()->where('company_id', $user->company_id);
        foreach (['type', 'origin'] as $key) {
            if (! empty($filters[$key])) {
                $query->where($key, $filters[$key]);
            }
        }

        return $this->dates($query, 'occurred_at', $filters);
    }

    private function dates(Builder $query, string $column, array $filters): Builder
    {
        foreach (['from' => '>=', 'to' => '<'] as $key => $comparison) {
            if (! empty($filters[$key])) {
                $boundary = CarbonImmutable::createFromFormat('!Y-m-d', $filters[$key], DashboardDisplay::ZONE);
                if ($key === 'to') {
                    $boundary = $boundary->addDay();
                }
                $query->where($column, $comparison, $boundary->utc()->format('Y-m-d H:i:s.v'));
            }
        }

        return $query;
    }
}
