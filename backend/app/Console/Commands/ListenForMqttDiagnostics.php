<?php

namespace App\Console\Commands;

use App\Services\MqttDiagnosticsIngest;
use Illuminate\Console\Command;
use Illuminate\Database\QueryException;
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
                    $client->subscribe(MqttDiagnosticsIngest::TOPIC, function (string $topic, string $message, bool $retained) use ($ingest, $client) {
                        try {
                            $ingest->store($topic, $message, $retained);
                        } catch (QueryException) {
                            $this->error('No se pudo guardar la muestra MQTT. Reintentando la conexión en 3 segundos.');
                            $client->interrupt();
                        }
                    }, MqttClient::QOS_AT_LEAST_ONCE);
                    $this->info('Escuchando '.MqttDiagnosticsIngest::TOPIC);
                    $client->loop();
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
