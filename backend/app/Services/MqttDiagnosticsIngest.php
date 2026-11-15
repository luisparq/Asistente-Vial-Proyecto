<?php

namespace App\Services;

use Illuminate\Support\Facades\DB;
use JsonException;

class MqttDiagnosticsIngest
{
    public const TOPIC = 'avigem/devices/+/imu';

    public const CODE_PATTERN = '[A-Za-z0-9][A-Za-z0-9_-]{0,62}';

    public function store(string $topic, string $message, bool $retained = false): bool
    {
        if ($retained || strlen($message) > 1024 ||
            ! preg_match('~\Aavigem/devices/('.self::CODE_PATTERN.')/imu\z~', $topic, $match)) {
            return false;
        }

        try {
            $data = json_decode($message, true, 8, JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            return false;
        }

        if (! is_array($data) || ($data['deviceCode'] ?? null) !== $match[1] ||
            ! is_string($data['sessionId'] ?? null) ||
            ! preg_match('/\A[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\z/i', $data['sessionId']) ||
            ! is_int($data['sequence'] ?? null) || $data['sequence'] < 0) {
            return false;
        }

        $axes = [];
        foreach (['ax', 'ay', 'az', 'gx', 'gy', 'gz'] as $axis) {
            $value = $data[$axis.'Raw'] ?? null;
            if (! is_int($value) || $value < -32768 || $value > 32767) {
                return false;
            }
            $axes[$axis] = $value;
        }

        $received = now('UTC')->format('Y-m-d H:i:s.v');

        return DB::transaction(function () use ($match, $data, $axes, $received) {
            $binding = DB::table('diagnostic_device_codes')->where('code', $match[1])->lockForUpdate()->first();
            // MySQL may compare text case-insensitively. MQTT topics are case-sensitive.
            if (! $binding || $binding->code !== $match[1]) {
                return false;
            }

            $device = DB::table('devices')->where('id', $binding->device_id)->first(['id', 'company_id']);
            $identity = [
                'device_id' => $device->id,
                'source_session_id' => strtolower($data['sessionId']),
            ];
            $session = DB::table('diagnostic_sessions')->where($identity)->value('id')
                ?? DB::table('diagnostic_sessions')->insertGetId($identity + [
                    'company_id' => $device->company_id,
                    'started_at' => $received,
                ]);

            DB::table('diagnostic_samples')->upsert([[
                'session_id' => $session,
                'sequence' => $data['sequence'],
                'sampled_at' => $received,
            ] + $axes], ['session_id', 'sequence'], ['sequence']);

            return true;
        }, 5);
    }
}
