<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class AdministrativeAudit extends Model
{
    public $timestamps = false;

    protected $guarded = ['id'];

    protected function casts(): array
    {
        return ['changes' => 'array', 'occurred_at' => 'immutable_datetime'];
    }

    public function company() { return $this->belongsTo(Company::class); }

    public function actor()
    {
        return $this->belongsTo(User::class, 'actor_id');
    }
}
