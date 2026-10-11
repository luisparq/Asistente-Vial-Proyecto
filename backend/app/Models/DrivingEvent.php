<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class DrivingEvent extends Model
{
    public $timestamps = false;

    protected $guarded = ['id'];

    protected $dateFormat = 'Y-m-d H:i:s.v';

    protected function casts(): array
    {
        return ['occurred_at' => 'immutable_datetime', 'first_received_at' => 'immutable_datetime', 'gps_received_at' => 'immutable_datetime',
            'latitude' => 'double', 'longitude' => 'double', 'gps_utc_seconds' => 'integer', 'gps_age_ms' => 'integer'];
    }

    public function company(): BelongsTo
    {
        return $this->belongsTo(Company::class);
    }

    public function trip(): BelongsTo
    {
        return $this->belongsTo(Trip::class);
    }
}
