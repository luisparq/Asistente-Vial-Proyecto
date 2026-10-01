<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class EmergencyContact extends Model
{
    protected $guarded = ['id', 'company_id', 'driver_id'];
}
