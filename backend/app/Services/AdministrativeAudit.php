<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Database\Eloquent\Model;

class AdministrativeAudit
{
    public static function record(?User $actor, ?int $company, string $action, Model $entity, array $changedFields = [], array $transitions = []): void
    {
        // Only field names, state enums and reference IDs; never passwords, paths or credentials.
        $allowed = ['name', 'trade_name', 'full_name', 'email', 'status', 'access_status', 'plate', 'brand', 'model', 'contact', 'vehicle_id', 'review_status', 'document', 'password_reset', 'subject', 'category', 'priority', 'device_id', 'responsible_id', 'comment'];
        $safe=[];
        foreach($transitions as $field=>$change) {
            if(!in_array($field,['status','access_status','priority','device_id','responsible_id'],true)||!is_array($change))continue;
            $valid=true;
            foreach(['from','to'] as $side) {
                if(!array_key_exists($side,$change)){$valid=false;break;}
                $value=$change[$side];
                if(in_array($field,['device_id','responsible_id'],true))$valid=$valid&&($value===null||(is_int($value)&&$value>0));
                else $valid=$valid&&is_string($value)&&in_array($value,['ACTIVE','INACTIVE','PENDING','DISABLED','OPEN','IN_PROGRESS','RESOLVED','CLOSED','LOW','MEDIUM','HIGH'],true);
            }
            if($valid)$safe[$field]=array_intersect_key($change,['from'=>true,'to'=>true]);
        }
        \App\Models\AdministrativeAudit::create([
            'actor_id' => $actor?->id, 'company_id' => $company, 'action' => $action,
            'entity' => $entity->getTable(), 'entity_id' => $entity->getKey(),
            'changes' => ['fields' => array_values(array_intersect($changedFields, $allowed)), 'actor_role'=>$actor?->role, 'result'=>'SUCCESS', 'transitions'=>$safe], 'occurred_at' => now(),
        ]);
    }
}
