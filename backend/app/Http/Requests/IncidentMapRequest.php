<?php

namespace App\Http\Requests;

use Illuminate\Validation\Rule;

class IncidentMapRequest extends DashboardFilterRequest
{
    public function rules(): array
    {
        return parent::rules() + ['vehicle_id' => ['nullable', 'integer', Rule::exists('vehicles','id')->where('company_id',$this->user('web')->company_id)]];
    }
}
