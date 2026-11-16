<?php

namespace App\Http\Requests;

use Illuminate\Validation\Rule;

class IncidentMapRequest extends DashboardFilterRequest
{
    public function rules(): array
    {
        return parent::rules() + [
            'kind' => ['nullable', 'in:alerts'],
            'alert_status' => ['nullable', 'in:pending'],
            'focus_alert' => ['nullable', 'uuid:4'],
            'vehicle_id' => ['nullable', 'integer', Rule::exists('vehicles', 'id')->where('company_id', $this->user('web')->company_id)],
        ];
    }
}
