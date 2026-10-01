<?php

use App\Models\Company;
use App\Models\DrivingEvent;
use App\Models\Trip;
use App\Models\User;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

// Local browser verification fixture. Never seeds the manual database.
require dirname(__DIR__, 2).'/vendor/autoload.php';
$app = require dirname(__DIR__, 2).'/bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();
if ($app->configurationIsCached()) {
    throw new RuntimeException('Cached configuration is not allowed for preview fixtures.');
}
$dir = storage_path('framework/testing');
if (! is_dir($dir)) {
    mkdir($dir, 0777, true);
}
$path = $dir.'/dashboard-preview-'.Str::uuid().'.sqlite';
touch($path);
config(['database.default' => 'sqlite', 'database.connections.sqlite.database' => $path, 'hashing.bcrypt.rounds' => 4]);
DB::purge('sqlite');
Artisan::call('migrate', ['--force' => true]);
$company = Company::create(['name' => 'TEST ONLY · Revisión del dashboard']);
$password = bin2hex(random_bytes(24));
$admin = new User;
$admin->forceFill(['company_id' => $company->id, 'name' => 'TEST ONLY Administrador', 'email' => 'test-'.Str::uuid().'@example.invalid', 'password' => $password, 'role' => 'fleet_admin'])->save();
$trip = Trip::create(['company_id' => $company->id, 'created_by_user_id' => $admin->id,
    'client_trip_id' => (string) Str::uuid(), 'started_at' => now()->subMinutes(2), 'ended_at' => now()->subMinute(),
    'status' => 'COMPLETED', 'client_revision' => 2, 'first_received_at' => now(), 'last_changed_at' => now()]);
DrivingEvent::create(['company_id' => $company->id, 'trip_id' => $trip->id,
    'client_event_id' => (string) Str::uuid(), 'type' => 'HARSH_BRAKING', 'origin' => 'SIMULATED',
    'occurred_at' => now()->subSeconds(90), 'first_received_at' => now()]);
file_put_contents($dir.'/dashboard-preview.json', json_encode(['database' => $path, 'email' => $admin->email, 'password' => $password], JSON_THROW_ON_ERROR));
echo "Isolated TEST ONLY preview created. Credentials in ignored storage/framework/testing/dashboard-preview.json\n";
