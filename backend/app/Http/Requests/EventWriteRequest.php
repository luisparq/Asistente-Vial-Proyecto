<?php

namespace App\Http\Requests;

use App\Domain\DataOrigin;
use App\Domain\DrivingEventType;
use App\Support\UtcTimestamp;
use Illuminate\Validation\Rule;

class EventWriteRequest extends ContractRequest
{
    public function rules(): array
    {
        return ['type' => ['required', Rule::enum(DrivingEventType::class)],
            'occurred_at' => ['required', new UtcTimestamp],
            'origin' => ['required', Rule::enum(DataOrigin::class)],
            'latitude' => ['nullable', 'numeric', 'between:-90,90'],
            'longitude' => ['nullable', 'numeric', 'between:-180,180'],
            'location_origin' => ['nullable', Rule::in(['GPS_NEO6M'])],
            'gps_utc_seconds' => ['nullable', 'integer', 'between:0,86399'],
            'gps_received_at' => ['nullable', new UtcTimestamp],
            'gps_age_ms' => ['nullable', 'integer', 'between:0,2000']];
    }
    public function withValidator(\Illuminate\Validation\Validator $validator): void
    {
        parent::withValidator($validator);
        $validator->after(function ($validator) {
            if ($validator->errors()->isNotEmpty()) return;
            $data = $this->validationData();
            $core = ['latitude', 'longitude', 'location_origin', 'gps_received_at', 'gps_age_ms'];
            $present = array_filter($core, fn ($key) => ($data[$key] ?? null) !== null);
            if (! $present && ($data['gps_utc_seconds'] ?? null) === null) return;
            if (count($present) !== count($core)) {
                $validator->errors()->add('location_origin', 'La ubicación requiere coordenadas, origen y referencias temporales completas.');
                return;
            }
            $event = \Carbon\CarbonImmutable::parse($data['occurred_at']);
            $received = \Carbon\CarbonImmutable::parse($data['gps_received_at']);
            $elapsed = $event->getTimestampMs() - $received->getTimestampMs();
            if ($elapsed < 0 || $elapsed > 2000 || $elapsed > (int) $data['gps_age_ms']) {
                $validator->errors()->add('gps_received_at', 'La ubicación debe corresponder al evento dentro de 2000 ms.');
            }
        });
    }
}
