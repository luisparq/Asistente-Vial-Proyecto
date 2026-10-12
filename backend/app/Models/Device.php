<?php
namespace App\Models;
use Illuminate\Database\Eloquent\Model;
class Device extends Model {
    protected $dateFormat = 'Y-m-d H:i:s.v';
    protected $guarded = ['id', 'company_id', 'user_id', 'client_device_id'];
    protected function casts(): array { return ['observed_at'=>'immutable_datetime', 'received_at'=>'immutable_datetime', 'last_contact_at'=>'immutable_datetime']; }
    public function company() { return $this->belongsTo(Company::class); }
    public function user() { return $this->belongsTo(User::class); }
    public function communicationState(): string {
        if (! $this->observed_at || ! $this->received_at) return 'Sin información';
        $now = now();
        return $this->observed_at->gte($now->copy()->subMinutes(3)) && $this->observed_at->lte($now->copy()->addSeconds(30))
            && $this->received_at->gte($now->copy()->subMinutes(3)) ? 'Comunicación reciente' : 'Sin comunicación reciente';
    }
}
