<?php

use App\Models\User;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

// Isolated migration preservation test; never opens the manual database.
require dirname(__DIR__, 2).'/vendor/autoload.php';
$app = require dirname(__DIR__, 2).'/bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();
if ($app->configurationIsCached()) {
    throw new RuntimeException('Cached configuration forbidden');
}
config(['database.default' => 'sqlite', 'database.connections.sqlite.database' => ':memory:']);
DB::purge('sqlite');
$files = glob(database_path('migrations/*.php'));
sort($files);
$new = array_pop($files);
foreach ($files as $file) {
    (require $file)->up();
}
$db = DB::connection();
$company = $db->table('companies')->insertGetId(['name' => 'TEST migration company']);
$user = $db->table('users')->insertGetId(['company_id' => $company, 'name' => 'TEST legacy', 'email' => 'migration@example.test', 'password' => password_hash('TEST password', PASSWORD_BCRYPT), 'role' => 'member']);
$trip = $db->table('trips')->insertGetId(['company_id' => $company, 'created_by_user_id' => $user, 'client_trip_id' => (string) Str::uuid(), 'started_at' => '2026-09-30 02:26:55.999', 'ended_at' => '2026-09-30 02:27:11.905', 'status' => 'COMPLETED', 'client_revision' => 2, 'first_received_at' => '2026-09-30 02:28:00.000', 'last_changed_at' => '2026-09-30 02:28:00.000']);
$db->table('driving_events')->insert(['company_id' => $company, 'trip_id' => $trip, 'client_event_id' => (string) Str::uuid(), 'type' => 'HARSH_BRAKING', 'origin' => 'SIMULATED', 'occurred_at' => '2026-09-30 02:27:05.016', 'first_received_at' => '2026-09-30 02:28:00.000']);
$db->table('personal_access_tokens')->insert(['company_id' => $company, 'tokenable_type' => User::class, 'tokenable_id' => $user, 'name' => 'TEST migration token', 'token' => hash('sha256', random_bytes(32)), 'abilities' => '["trips:read","trips:write"]']);
$snap = [];
foreach (['companies', 'users', 'trips', 'driving_events', 'personal_access_tokens'] as $table) {
    $cols = Schema::getColumnListing($table);
    $snap[$table] = [$cols, $db->table($table)->select($cols)->get()->toJson()];
}
(require $new)->up();
foreach ($snap as $table => [$cols,$json]) {
    if ($json !== $db->table($table)->select($cols)->get()->toJson()) {
        throw new RuntimeException('Changed legacy '.$table);
    }
}
if ($db->select('PRAGMA foreign_key_check')) {
    throw new RuntimeException('Invalid foreign keys');
}
$row = $db->table('trips')->first();
if ($row->driver_id !== null || $row->vehicle_id !== null) {
    throw new RuntimeException('Invented associations');
}
echo 'PASS: original companies/users/trips/events/tokens and timestamps unchanged; nullable context; foreign keys valid';
