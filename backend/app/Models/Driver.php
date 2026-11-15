<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Driver extends Model
{
    protected $guarded = ['id', 'company_id', 'user_id'];
    protected $hidden = ['photo_path'];

    public function user()
    {
        return $this->belongsTo(User::class);
    }

    public function company()
    {
        return $this->belongsTo(Company::class);
    }

    public function emergencyContact()
    {
        return $this->hasOne(EmergencyContact::class);
    }

    public function documents()
    {
        return $this->hasMany(DriverDocument::class);
    }

    public function assignments()
    {
        return $this->hasMany(VehicleAssignment::class);
    }

    public function currentAssignment()
    {
        return $this->hasOne(VehicleAssignment::class)->where('active_slot', 1);
    }

    public function trips()
    {
        return $this->hasMany(Trip::class);
    }

    public function score(): ?int
    {
        if (! $this->scoring_trips_count && ! $this->scoring_events_count) {
            return null;
        }

        return max(0, 100 - \App\Services\DriverScores::PENALTY * $this->scoring_events_count);
    }
}
