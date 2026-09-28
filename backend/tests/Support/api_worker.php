<?php

use Illuminate\Contracts\Http\Kernel;
use Illuminate\Http\Request;

// Test-only subprocess: receives ephemeral fixture credentials via stdin, never argv/logs.
require __DIR__.'/../../vendor/autoload.php';
$app = require __DIR__.'/../../bootstrap/app.php';
$input = json_decode(stream_get_contents(STDIN), true, 32, JSON_THROW_ON_ERROR);
$kernel = $app->make(Kernel::class);
$kernel->bootstrap();
if ($app->configurationIsCached()) {
    throw new RuntimeException('Test workers require uncached configuration.');
}
while (microtime(true) < $input['start_at']) {
    usleep(10000);
}
$request = Request::create($input['uri'], $input['method'], [], [], [], [
    'CONTENT_TYPE' => 'application/json', 'HTTP_ACCEPT' => 'application/json',
    'HTTP_AUTHORIZATION' => 'Bearer '.$input['token'],
], json_encode($input['body'], JSON_THROW_ON_ERROR));
$response = $kernel->handle($request);
echo json_encode(['status' => $response->getStatusCode(), 'body' => json_decode($response->getContent(), true)], JSON_THROW_ON_ERROR);
$kernel->terminate($request, $response);
