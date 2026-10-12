<?php
namespace App\Models;
use Illuminate\Database\Eloquent\Model;
class SupportEntry extends Model {
    public $timestamps=false;
    protected $guarded=['id'];
    protected function casts(): array {return ['changes'=>'array','created_at'=>'immutable_datetime'];}
    public function actor(){return $this->belongsTo(User::class,'actor_id');}
}
