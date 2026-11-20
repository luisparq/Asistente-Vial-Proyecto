<?php

namespace App\Services;

use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use JsonException;

class MqttDiagnosticsIngest
{
    public const TOPIC = 'avigem/devices/+/imu';

    public const CODE_PATTERN = '[A-Za-z0-9][A-Za-z0-9_-]{0,62}';

    public function store(string $topic, string $message, bool $retained = false, string $source = 'MQTT', ?int $relayUserId = null): bool
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

        if (! in_array($source, ['MQTT', 'BLE_API'], true) || ! in_array($data['protocol'] ?? 1, [1, 2], true)) {
            return false;
        }
        $v2 = ($data['protocol'] ?? 1) === 2;
        if ($v2) {
            foreach (['profileVersion' => 65535, 'event' => 3, 'range' => 4, 'flags' => 19] as $key => $max) {
                if (! is_int($data[$key] ?? null) || $data[$key] < 0 || $data[$key] > $max) {
                    return false;
                }
            }
            $stage = $data['flags'] >> 2;
            if ($stage > 4 || ($stage > 0 && ($data['flags'] & 3) !== 0) || $data['sequence'] > 4294967295) {
                return false;
            }
            if ($data['event'] > 0 && (($data['flags'] & 3) !== 3 || $data['range'] !== $data['event'])) {
                return false;
            }
            foreach (['accelerationLimit' => 2, 'brakingLimit' => 2, 'turnLimit' => 250] as $key => $max) {
                if (($data['flags'] & 1) === 0) {
                    $max = 1000;
                }
                if (array_key_exists($key, $data) && (! is_int($data[$key]) && ! is_float($data[$key]) || ! is_finite((float) $data[$key]) || $data[$key] < 0 || $data[$key] >= $max)) {
                    return false;
                }
            }
            if (isset($data['calibrationStatus']) && (! is_string($data['calibrationStatus']) || ! preg_match('/\A[A-Z0-9_]{1,48}\z/', $data['calibrationStatus']))) {
                return false;
            }
            if (isset($data['ack']) && (! is_string($data['ack']) || ($data['ack'] !== '' && ! preg_match('/\A[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\z/i', $data['ack'])))) {
                return false;
            }
        } elseif ($source === 'BLE_API') {
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
        $observed = $received;
        if ($source === 'BLE_API') {
            $captured = $data['capturedAt'] ?? null;
            $nowMs = now('UTC')->getTimestampMs();
            if (! is_int($captured) || $captured > $nowMs + 30000 || $captured < $nowMs - 7 * 86400000) {
                return false;
            }
            $observed = CarbonImmutable::createFromTimestampMs($captured, 'UTC')->format('Y-m-d H:i:s.v');
        }

        return DB::transaction(function () use ($match, $data, $axes, $received, $source, $relayUserId, $v2, $observed) {
            $binding = DB::table('diagnostic_device_codes')->where('code', $match[1])->lockForUpdate()->first();
            // MySQL may compare text case-insensitively. MQTT topics are case-sensitive.
            if (! $binding || $binding->code !== $match[1]) {
                return false;
            }

            $device = DB::table('devices')->where('id', $binding->device_id)->first(['id', 'company_id']);
            if ($source === 'BLE_API') {
                $user = DB::table('users')->where('id', $relayUserId)->first();
                $owner = DB::table('devices')->where('id', $device->id)->value('user_id');
                if (! $user || (int) $user->company_id !== (int) $device->company_id || (int) $owner !== $relayUserId) {
                    return false;
                }
            }
            $identity = [
                'device_id' => $device->id,
                'source_session_id' => strtolower($data['sessionId']),
            ];
            $session = DB::table('diagnostic_sessions')->where($identity)->value('id')
                ?? DB::table('diagnostic_sessions')->insertGetId($identity + [
                    'company_id' => $device->company_id,
                    'started_at' => $received,
                ]);

            $key = ['session_id' => $session, 'sequence' => $data['sequence']];
            $original = DB::table('diagnostic_samples')->where($key)->first();
            $extra = ['profile_version' => $v2 ? $data['profileVersion'] : 0, 'motion_range' => $v2 ? $data['range'] : 4,
                'motion_event' => $v2 ? $data['event'] : 0, 'motion_flags' => $v2 ? $data['flags'] : 0];
            if ($original) {
                foreach ($axes + $extra as $field => $value) {
                    if ((int) $original->$field !== $value) {
                        return false;
                    }
                }
            } else {
                DB::table('diagnostic_samples')->insert($key + ['sampled_at' => $observed] + $axes + $extra);
            }
            $column = $source === 'MQTT' ? 'mqtt_received_at' : 'ble_received_at';
            $update = [$column => $received];
            if ($source === 'BLE_API') {
                $update['relay_user_id'] = $relayUserId;
            }
            DB::table('diagnostic_samples')->where($key)->whereNull($column)->update($update);
            $sample = DB::table('diagnostic_samples')->where($key)->first();
            if ($sample->motion_event > 0) {
                DB::table('diagnostic_sensor_events')->insertOrIgnore([
                    'sample_id' => $sample->id, 'company_id' => $device->company_id, 'device_id' => $device->id, 'type' => $sample->motion_event, 'received_at' => $sample->sampled_at]);
            }
            $last = DB::table('diagnostic_device_state')->where('device_id', $device->id)->first();
            $sameSession = $last && $last->session_id === strtolower($data['sessionId']);
            $newer = ! $last || ($sameSession && $data['sequence'] > $last->sequence) || (! $sameSession && $sample->sampled_at > $last->received_at);
            $enrich = $sameSession && $data['sequence'] === (int) $last->sequence && $source === 'MQTT';
            if ($newer || $enrich) {
                $canonical = array_intersect_key($data, array_flip(['protocol', 'deviceCode', 'sessionId', 'sequence', 'axRaw', 'ayRaw', 'azRaw', 'gxRaw', 'gyRaw', 'gzRaw', 'profileVersion', 'event', 'range', 'flags', 'capturedAt']));
                $state = ['session_id' => strtolower($data['sessionId']), 'sequence' => $data['sequence'], 'payload' => json_encode($canonical), 'received_at' => $sample->sampled_at];
                if ($source === 'MQTT') {
                    $state['mqtt_payload'] = json_encode($data);
                    $state['mqtt_received_at'] = $received;
                } elseif (! $sameSession) {
                    $state['mqtt_payload'] = null;
                    $state['mqtt_received_at'] = null;
                }
                DB::table('diagnostic_device_state')->updateOrInsert(['device_id' => $device->id], $state);
            }
            if ($source === 'MQTT' && is_string($data['ack'] ?? null)) {
                DB::table('diagnostic_device_commands')->where('id', $data['ack'])
                    ->where('device_id', $device->id)->whereIn('status', ['QUEUED', 'PUBLISHED'])->update(['status' => 'ACKNOWLEDGED', 'acknowledged_at' => $received]);
            }

            return true;
        }, 5);
    }
}
