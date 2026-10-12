<?php
namespace App\Models;
use Illuminate\Database\Eloquent\Model;
class SupportTicket extends Model {
    public const STATUSES=['OPEN'=>'Abierto','IN_PROGRESS'=>'En proceso','RESOLVED'=>'Resuelto','CLOSED'=>'Cerrado'];
    public const PRIORITIES=['LOW'=>'Baja','MEDIUM'=>'Media','HIGH'=>'Alta'];
    public const CATEGORIES=['BLE'=>'Conectividad BLE','GPS'=>'GPS','IMU'=>'IMU','ANDROID'=>'Aplicación Android','SYNC'=>'Sincronización','DEVICE'=>'Dispositivo','OTHER'=>'Otros'];
    protected $guarded=['id','company_id','created_by','code'];
    public function company(){return $this->belongsTo(Company::class);}
    public function device(){return $this->belongsTo(Device::class);}
    public function creator(){return $this->belongsTo(User::class,'created_by');}
    public function responsible(){return $this->belongsTo(User::class,'responsible_id');}
    public function entries(){return $this->hasMany(SupportEntry::class);}
}
