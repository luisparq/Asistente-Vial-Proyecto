<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Company extends Model
{
    use HasFactory;

    protected $fillable = ['name', 'trade_name'];

    public function administrators(): HasMany
    {
        return $this->hasMany(User::class)->where('role', 'fleet_admin')->orderBy('id');
    }

    public function users(): HasMany
    {
        return $this->hasMany(User::class);
    }

    public function trips(): HasMany
    {
        return $this->hasMany(Trip::class);
    }

    public function events(): HasMany
    {
        return $this->hasMany(DrivingEvent::class);
    }
}
