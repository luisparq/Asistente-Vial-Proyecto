<?php

namespace App\Http\Requests;

use App\Domain\DataOrigin;
use App\Domain\DrivingEventType;
use App\Domain\TripStatus;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class DashboardFilterRequest extends FormRequest
{
    public function authorize(): bool
    {
        return $this->user('web')?->role === 'fleet_admin';
    }

    public function rules(): array
    {
        return [
            'from' => ['nullable', 'date_format:Y-m-d'],
            'to' => array_filter(['nullable', 'date_format:Y-m-d', $this->filled('from') ? 'after_or_equal:from' : null]),
            'status' => ['nullable', Rule::enum(TripStatus::class)],
            'type' => ['nullable', Rule::enum(DrivingEventType::class)],
            'origin' => ['nullable', Rule::enum(DataOrigin::class)],
            'user_id' => ['nullable', 'integer', Rule::exists('users', 'id')->where('company_id', $this->user('web')->company_id)],
            'page' => ['nullable', 'integer', 'min:1', 'max:1000000'],
        ];
    }
}
