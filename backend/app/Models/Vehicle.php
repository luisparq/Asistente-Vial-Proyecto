<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Vehicle extends Model
{
    protected $guarded = ['id', 'company_id'];

    public function company()
    {
        return $this->belongsTo(Company::class);
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
}
