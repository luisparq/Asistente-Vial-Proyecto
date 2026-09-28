<?php

namespace App\Http\Requests;

use App\Domain\DataOrigin;
use App\Domain\DrivingEventType;
use App\Domain\TripStatus;
use App\Support\UtcTimestamp;
use Illuminate\Validation\Rule;

class ListRequest extends ContractRequest
{
    public function rules(): array
    {
        $rules = ['limit' => ['sometimes', 'integer', 'between:1,100'], 'cursor' => ['sometimes', 'string', 'max:4096']];

        return $rules + ($this->routeIs('events.index') ? [
            'type' => ['sometimes', Rule::enum(DrivingEventType::class)],
            'origin' => ['sometimes', Rule::enum(DataOrigin::class)],
            'occurred_from' => ['sometimes', new UtcTimestamp], 'occurred_before' => ['sometimes', new UtcTimestamp],
        ] : ['status' => ['sometimes', Rule::enum(TripStatus::class)],
            'started_from' => ['sometimes', new UtcTimestamp], 'started_before' => ['sometimes', new UtcTimestamp]]);
    }
}
