<?php

namespace App\Http\Requests;

use App\Domain\DataOrigin;
use App\Domain\DrivingEventType;
use App\Support\UtcTimestamp;
use Illuminate\Validation\Rule;

class EventWriteRequest extends ContractRequest
{
    public function rules(): array
    {
        return ['type' => ['required', Rule::enum(DrivingEventType::class)],
            'occurred_at' => ['required', new UtcTimestamp],
            'origin' => ['required', Rule::enum(DataOrigin::class)]];
    }
}
