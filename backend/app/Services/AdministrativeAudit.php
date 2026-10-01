<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Database\Eloquent\Model;

class AdministrativeAudit
{
    public static function record(?User $actor, int $company, string $action, Model $entity, array $changedFields = []): void
    {
        // Only field names, never submitted values, passwords, paths or credentials.
        $allowed = ['name', 'full_name', 'email', 'status', 'access_status', 'plate', 'brand', 'model', 'contact', 'vehicle_id', 'review_status', 'document', 'password_reset'];
        \App\Models\AdministrativeAudit::create([
            'actor_id' => $actor?->id, 'company_id' => $company, 'action' => $action,
            'entity' => $entity->getTable(), 'entity_id' => $entity->getKey(),
            'changes' => ['fields' => array_values(array_intersect($changedFields, $allowed))], 'occurred_at' => now(),
        ]);
    }
}
