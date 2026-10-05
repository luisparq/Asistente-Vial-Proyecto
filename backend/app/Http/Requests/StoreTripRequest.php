<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class StoreTripRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        return [
            "user_id"     => "required|integer",
            "latitude"    => "required|numeric",
            "longitude"   => "required|numeric",
            "speed"       => "nullable|numeric|min:0",
            "recorded_at" => "nullable|date",
        ];
    }
}
