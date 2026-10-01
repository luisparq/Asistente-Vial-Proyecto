<?php

// Explicit TEST ONLY fixture. Only an isolated database under framework/testing is accepted.
$root = dirname(__DIR__, 2);
$backend = $root.'/backend';
require $backend.'/vendor/autoload.php';
$app = require $backend.'/bootstrap/app.php';
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();
if ($app->configurationIsCached()) { throw new RuntimeException('Clear cached config before E2E tests.'); }
$action = $argv[1] ?? '';
$database = $argv[2] ?? '';
$directory = realpath($backend.'/storage/framework/testing');
if (realpath(dirname($database)) !== $directory || !preg_match('/^android-e2e-[a-zA-Z0-9-]+\.sqlite$/', basename($database))) {
    throw new RuntimeException('Refusing to use a non-test database.');
}
config(['database.default' => 'sqlite', 'database.connections.sqlite.database' => $database, 'hashing.bcrypt.rounds' => 4]);
Illuminate\Support\Facades\DB::purge('sqlite');
if ($action === 'setup') {
    if (file_exists($database)) { throw new RuntimeException('Test database already exists; refusing to overwrite.'); }
    touch($database);
    Illuminate\Support\Facades\Artisan::call('migrate', ['--force' => true]);
    $company = App\Models\Company::create(['name' => 'TEST ONLY Android integration']);
    $user = new App\Models\User;
    $password = bin2hex(random_bytes(24));
    $email = 'test-'.bin2hex(random_bytes(8)).'@example.invalid';
    $user->forceFill(['company_id' => $company->id, 'name' => 'TEST ONLY Android integration', 'email' => $email, 'role' => 'driver', 'password' => $password])->save();
    $driver = new App\Models\Driver;
    $driver->forceFill(['company_id'=>$company->id,'user_id'=>$user->id,'full_name'=>'TEST ONLY Android profile','status'=>'ACTIVE'])->save();
    $vehicle = new App\Models\Vehicle;
    $vehicle->forceFill(['company_id'=>$company->id,'plate'=>'TEST-HTTP','brand'=>'TEST ONLY','model'=>'TEST ONLY','status'=>'ACTIVE'])->save();
    app(App\Services\VehicleAssignments::class)->assign($user,$driver,$vehicle->id);
    $contact = new App\Models\EmergencyContact;
    $contact->forceFill(['company_id'=>$company->id,'driver_id'=>$driver->id,'name'=>'TEST ONLY emergency','relationship'=>'TEST ONLY','phone'=>'TEST ONLY'])->save();
    // Credentials go to an ignored report file, never stdout.
    file_put_contents($argv[3], json_encode(['email' => $email, 'password' => $password], JSON_THROW_ON_ERROR));
    echo "Isolated TEST fixture created.\n";
} elseif ($action === 'verify') {
    $trips = App\Models\Trip::all();
    $events = App\Models\DrivingEvent::all();
    if ($trips->count() !== 1 || $events->count() !== 1 || $trips[0]->status !== 'COMPLETED' || $events[0]->origin !== 'SIMULATED' || $events[0]->trip_id !== $trips[0]->id) {
        throw new RuntimeException('End-to-end persistence assertion failed.');
    }
    echo json_encode(['test_only' => true, 'trips' => 1, 'events' => 1, 'status' => $trips[0]->status, 'origin' => $events[0]->origin,
        'client_trip_id' => $trips[0]->client_trip_id, 'client_event_id' => $events[0]->client_event_id], JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR)."\n";
} else { throw new RuntimeException('Unknown action.'); }
