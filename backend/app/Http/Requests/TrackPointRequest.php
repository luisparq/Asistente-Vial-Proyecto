<?php

namespace App\Http\Requests;

use App\Support\UtcTimestamp;

class TrackPointRequest extends ContractRequest
{
    public function rules(): array
    {
        return [
            'captured_at' => ['required', new UtcTimestamp],
            'latitude' => ['required', 'numeric', 'between:-90,90'],
            'longitude' => ['required', 'numeric', 'between:-180,180'],
        ];
    }
}
