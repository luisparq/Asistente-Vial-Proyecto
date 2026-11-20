<?php

namespace App\Console\Commands;

use App\Services\MqttDiagnosticsIngest;
use Illuminate\Console\Command;
use Illuminate\Database\QueryException;
use Illuminate\Support\Facades\DB;
use PhpMqtt\Client\ConnectionSettings;
use PhpMqtt\Client\Exceptions\MqttClientException;
use PhpMqtt\Client\MqttClient;

class ListenForMqttDiagnostics extends Command
{
    protected $signature = 'mqtt:listen';

    protected $description = 'Receive raw ESP32 diagnostics from MQTT';

    public function handle(MqttDiagnosticsIngest $ingest): int
    {
        $config = config('mqtt');
        if (blank($config['host']) || blank($config['username']) || blank($config['password']) ||
            $config['port'] < 1 || $config['port'] > 65535 ||
            ($config['ca_file'] !== null && ! is_readable($config['ca_file']))) {
            $this->error('Revisa la configuración MQTT en .env.');

            return self::FAILURE;
        }

        $settings = (new ConnectionSettings)
            ->setUsername($config['username'])
            ->setPassword($config['password'])
            ->setUseTls($config['tls'])
            ->setTlsVerifyPeer(true)
            ->setTlsVerifyPeerName(true)
            ->setTlsCertificateAuthorityFile($config['ca_file'])
            ->setConnectTimeout(5)
            ->setKeepAliveInterval(15);

        $running = true;
        $client = null;
        $this->trap(fn () => [SIGINT, SIGTERM], function () use (&$running, &$client) {
            $running = false;
            $client?->interrupt();
        });

        while ($running) {
            $client = new MqttClient($config['host'], $config['port'], protocol: MqttClient::MQTT_3_1_1);

            try {
                $client->connect($settings, true);
                if ($running) {
                    $reconnectRequested = false;
                    $client->subscribe(MqttDiagnosticsIngest::TOPIC, function (string $topic, string $message, bool $retained) use ($ingest, $client, &$reconnectRequested) {
                        try {
                            $ingest->store($topic, $message, $retained);
                        } catch (QueryException) {
                            $this->error('No se pudo guardar la muestra MQTT. Reintentando la conexión en 3 segundos.');
                            $reconnectRequested = true;
                            $client->interrupt();
                        }
                    }, MqttClient::QOS_AT_LEAST_ONCE);
                    $this->info('Escuchando '.MqttDiagnosticsIngest::TOPIC);
                    $loopStart = microtime(true);
                    $nextCommands = 0;
                    while ($running && ! $reconnectRequested && $client->isConnected()) {
                        $client->loopOnce($loopStart, true);
                        if (microtime(true) < $nextCommands) {
                            continue;
                        }
                        $nextCommands = microtime(true) + 1;
                        $commands = DB::table('diagnostic_device_commands')->whereIn('status', ['QUEUED', 'PUBLISHED'])
                            ->where('created_at', '>=', now('UTC')->subMinutes(2))->where(function ($q) {
                                $q->whereNull('published_at')->orWhere('published_at', '<', now('UTC')->subSeconds(5));
                            })
                            ->orderBy('created_at')->limit(10)->get();
                        foreach ($commands as $command) {
                            $code = DB::table('diagnostic_device_codes')->where('device_id', $command->device_id)->value('code');
                            if (! $code) {
                                continue;
                            }
                            $client->publish("avigem/devices/$code/control", $command->id.'|'.$command->operation, 0, false);
                            DB::table('diagnostic_device_commands')->where('id', $command->id)->whereIn('status', ['QUEUED', 'PUBLISHED'])->update(['status' => 'PUBLISHED', 'published_at' => now('UTC')]);
                        }
                        DB::table('diagnostic_device_commands')->whereIn('status', ['QUEUED', 'PUBLISHED'])->where('created_at', '<', now('UTC')->subMinutes(2))->update(['status' => 'EXPIRED']);
                    }
                }
            } catch (QueryException) {
                if ($running) {
                    $this->warn('Base de datos no disponible. Reintentando en 3 segundos.');
                }
            } catch (MqttClientException) {
                if ($running) {
                    $this->warn('Conexión MQTT interrumpida. Reintentando en 3 segundos.');
                }
            } finally {
                if ($client->isConnected()) {
                    try {
                        $client->disconnect();
                    } catch (MqttClientException) {
                        // The connection may already have closed on the broker.
                    }
                }
            }

            if ($running) {
                sleep(3);
            }
        }

        return self::SUCCESS;
    }
}
