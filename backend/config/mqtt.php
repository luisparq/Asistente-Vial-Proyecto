<?php

return [
    'host' => env('MQTT_HOST'),
    'port' => (int) env('MQTT_PORT', 8883),
    'username' => env('MQTT_USERNAME'),
    'password' => env('MQTT_PASSWORD'),
    'tls' => (bool) env('MQTT_TLS', true),
    'ca_file' => env('MQTT_CA_FILE') ?: null,
];
