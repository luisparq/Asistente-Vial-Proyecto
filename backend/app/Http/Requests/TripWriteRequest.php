<?php

namespace App\Http\Requests;

use App\Domain\TripStatus;
use App\Support\UtcTimestamp;
use Illuminate\Validation\Rule;

class TripWriteRequest extends ContractRequest
{
    public function after(): array
    {
        return [function ($validator) {
            if ($this->json('status') === 'ACTIVE' && $this->json('ended_at') !== null) {
                $validator->errors()->add('ended_at', 'Un recorrido activo debe tener ended_at nulo.');
            }
        }];
    }

    public function rules(): array
    {
        return [
            'started_at' => ['required', new UtcTimestamp],
            'ended_at' => $this->json('status') === 'COMPLETED' ? ['required', new UtcTimestamp] : ['present', 'nullable', function ($attribute, $value, $fail) {
                if ($value !== null) {
                    $fail('Un recorrido activo debe tener ended_at nulo.');
                }
            }],
            'status' => ['required', Rule::enum(TripStatus::class)],
            'client_revision' => ['required', function ($attribute, $value, $fail) {
                $expected = $this->json('status') === 'ACTIVE' ? 1 : 2;
                if (! is_int($value) || $value !== $expected) {
                    $fail('Revisión incompatible con el estado.');
                }
            }],
        ];
    }
}
