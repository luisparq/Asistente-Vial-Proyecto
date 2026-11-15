<?php

namespace App\Console\Commands;

use App\Services\MqttDiagnosticsIngest;
use Illuminate\Console\Command;
use Illuminate\Database\UniqueConstraintViolationException;
use Illuminate\Support\Facades\DB;

class BindMqttDiagnosticDevice extends Command
{
    protected $signature = 'mqtt:bind-device {device-id} {device-code}';

    protected $description = 'Associate a workshop MQTT code with an existing device';

    public function handle(): int
    {
        $id = filter_var($this->argument('device-id'), FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
        $code = $this->argument('device-code');
        if ($id === false || ! preg_match('/\A'.MqttDiagnosticsIngest::CODE_PATTERN.'\z/', $code)) {
            $this->error('Indica un ID de dispositivo y un código MQTT válidos.');

            return self::FAILURE;
        }

        $device = DB::table('devices')->find($id, ['id', 'company_id']);
        if (! $device) {
            $this->error('El dispositivo no está registrado.');

            return self::FAILURE;
        }

        try {
            DB::table('diagnostic_device_codes')->insert(['device_id' => $id, 'code' => $code]);
        } catch (UniqueConstraintViolationException) {
            if (DB::table('diagnostic_device_codes')->where('device_id', $id)->value('code') !== $code) {
                $this->error('El código o el dispositivo ya tiene otra asociación MQTT.');

                return self::FAILURE;
            }
        }

        $this->info("Dispositivo {$device->id} de la empresa {$device->company_id} asociado a {$code}.");

        return self::SUCCESS;
    }
}
