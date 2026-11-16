<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

class ManeuverAlert extends Model
{
    public $timestamps = false;

    protected $guarded = ['id'];

    protected $dateFormat = 'Y-m-d H:i:s.v';

    protected function casts(): array
    {
        return ['occurred_at' => 'immutable_datetime', 'location_captured_at' => 'immutable_datetime',
            'first_received_at' => 'immutable_datetime', 'reviewed_at' => 'immutable_datetime',
            'test_only' => 'boolean', 'latitude' => 'double', 'longitude' => 'double'];
    }

    public function trip(): BelongsTo
    {
        return $this->belongsTo(Trip::class);
    }

    public function driver(): BelongsTo
    {
        return $this->belongsTo(Driver::class);
    }

    public function vehicle(): BelongsTo
    {
        return $this->belongsTo(Vehicle::class);
    }

    public function events(): BelongsToMany
    {
        return $this->belongsToMany(DrivingEvent::class);
    }
}
