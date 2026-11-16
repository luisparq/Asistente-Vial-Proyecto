<?php

namespace App\Http\Requests;

use App\Support\UtcTimestamp;
use Illuminate\Validation\Validator;

class ManeuverAlertWriteRequest extends ContractRequest
{
    public function rules(): array
    {
        return [
            'event_ids' => ['required', 'array', 'list', 'min:1', 'max:100'],
            'event_ids.*' => ['required', 'uuid:4', 'distinct:ignore_case'],
            'occurred_at' => ['required', new UtcTimestamp],
            'test_only' => ['required', 'boolean:strict'],
            'latitude' => ['nullable', 'numeric', 'between:-90,90'],
            'longitude' => ['nullable', 'numeric', 'between:-180,180'],
            'location_captured_at' => ['nullable', new UtcTimestamp],
            'location_status' => ['required', 'in:AVAILABLE,MISSING'],
        ];
    }

    public function withValidator(Validator $validator): void
    {
        parent::withValidator($validator);
        $validator->after(function (Validator $validator) {
            if ($validator->errors()->isNotEmpty()) {
                return;
            }

            $data = $this->validationData();
            $present = array_filter([$data['latitude'] ?? null, $data['longitude'] ?? null,
                $data['location_captured_at'] ?? null], fn ($value) => $value !== null);

            if (($data['location_status'] === 'AVAILABLE' && count($present) !== 3)
                || ($data['location_status'] === 'MISSING' && count($present) !== 0)
                || ($data['location_captured_at'] ?? '') > $data['occurred_at']) {
                $validator->errors()->add('location_status', 'La ubicación debe estar completa y ser anterior o simultánea a la alerta.');
            }
        });
    }
}
