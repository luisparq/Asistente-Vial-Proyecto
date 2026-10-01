<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class DriverDocument extends Model
{
    protected $guarded = ['id', 'company_id', 'driver_id', 'path'];

    protected $hidden = ['path'];

    protected function casts(): array
    {
        return ['issued_on' => 'immutable_date', 'expires_on' => 'immutable_date'];
    }

    public function type()
    {
        return $this->belongsTo(DocumentType::class, 'document_type_id');
    }

    public function driver()
    {
        return $this->belongsTo(Driver::class);
    }

    public function expired(): bool
    {
        return $this->expires_on !== null && $this->expires_on->format('Y-m-d') < now('America/Lima')->format('Y-m-d');
    }
}
