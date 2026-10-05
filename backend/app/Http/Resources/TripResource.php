<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class TripResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            "id"          => $this->id,
            "user_id"     => $this->user_id,
            "latitude"    => (float) $this->latitude,
            "longitude"   => (float) $this->longitude,
            "speed"       => (float) $this->speed,
            "recorded_at" => $this->created_at?->toIso8601String(),
        ];
    }
}
