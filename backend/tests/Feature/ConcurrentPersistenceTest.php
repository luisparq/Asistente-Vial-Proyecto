<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Symfony\Component\Process\Process;
use Tests\TestCase;

class ConcurrentPersistenceTest extends TestCase
{
    public function test_simultaneous_retries_and_fresh_process_reads_keep_one_trip_and_event(): void
    {
        // Dedicated disposable database: never migrate/fresh the user's local database.
        $path = tempnam(sys_get_temp_dir(), 'avi-api-test-');
        try {
            config(['database.connections.sqlite.database' => $path]);
            DB::purge('sqlite');
            $this->artisan('migrate', ['--force' => true])->assertSuccessful();
            $user = User::factory()->create();
            $token = $user->createToken('TEST ONLY subprocess', $user->capabilities(), now()->addHour())->plainTextToken;
            $tripUrl = '/api/v1/trips/'.Str::uuid();
            $eventUrl = $tripUrl.'/events/'.Str::uuid();
            $env = ['APP_ENV' => 'testing', 'APP_KEY' => config('app.key'), 'DB_CONNECTION' => 'sqlite',
                'DB_DATABASE' => $path, 'CACHE_STORE' => 'array', 'SESSION_DRIVER' => 'array'];
            $run = function (string $method, string $uri, array $body, int $count) use ($env, $token): array {
                $processes = [];
                $start = microtime(true) + 1.5;
                for ($i = 0; $i < $count; $i++) {
                    $process = new Process([PHP_BINARY, base_path('tests/Support/api_worker.php')], base_path(), $env, json_encode([
                        'method' => $method, 'uri' => $uri, 'body' => $body, 'token' => $token, 'start_at' => $start,
                    ], JSON_THROW_ON_ERROR), 30);
                    $process->start();
                    $processes[] = $process;
                }

                return array_map(function (Process $process) {
                    $process->wait();
                    $this->assertTrue($process->isSuccessful(), $process->getErrorOutput());

                    return json_decode($process->getOutput(), true, 32, JSON_THROW_ON_ERROR);
                }, $processes);
            };
            $trip = ['started_at' => '2026-09-28T10:00:00.123Z', 'ended_at' => null, 'status' => 'ACTIVE', 'client_revision' => 1];
            $statuses = array_column($run('PUT', $tripUrl, $trip, 4), 'status');
            sort($statuses);
            $this->assertSame([200, 200, 200, 201], $statuses);
            $event = ['type' => 'SHARP_TURN', 'origin' => 'SIMULATED', 'occurred_at' => '2026-09-28T10:01:00.456Z'];
            $statuses = array_column($run('PUT', $eventUrl, $event, 4), 'status');
            sort($statuses);
            $this->assertSame([200, 200, 200, 201], $statuses);
            $completed = array_replace($trip, ['status' => 'COMPLETED', 'client_revision' => 2, 'ended_at' => '2026-09-28T10:02:00.789Z']);
            $this->assertSame(200, $run('PUT', $tripUrl, $completed, 1)[0]['status']);
            $fresh = $run('GET', $tripUrl, [], 1)[0];
            $this->assertSame(200, $fresh['status']);
            $this->assertSame('COMPLETED', $fresh['body']['data']['status']);
            $this->assertSame(1, $fresh['body']['data']['received_event_count']);
            $events = $run('GET', $tripUrl.'/events', [], 1)[0];
            $this->assertSame('2026-09-28T10:01:00.456Z', $events['body']['data'][0]['occurred_at']);
            $this->assertDatabaseCount('trips', 1);
            $this->assertDatabaseCount('driving_events', 1);
        } finally {
            DB::purge('sqlite');
            if (is_file($path)) {
                unlink($path);
            }
        }
    }
}
