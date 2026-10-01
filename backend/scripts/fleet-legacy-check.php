<?php

use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

// Read-only legacy verification; 'before' also creates a consistent SQLite backup.
require dirname(__DIR__).'/vendor/autoload.php';
$app = require dirname(__DIR__).'/bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();
$db = DB::connection();
if ($db->getDriverName() !== 'sqlite' || $db->getDatabaseName() === ':memory:') {
    throw new RuntimeException('This preservation check requires the configured file SQLite database.');
}
$dir = storage_path('app/private/fleet-preservation');
if (! is_dir($dir)) {
    mkdir($dir, 0700, true);
}
$file = $dir.'/baseline.json';
$mode = $argv[1] ?? '';
if ($mode === 'before') {
    if (file_exists($file)) {
        throw new RuntimeException('Baseline already exists; refusing to overwrite it.');
    }
    $backup = $dir.'/before-'.gmdate('Ymd-His').'.sqlite';
    $db->statement('VACUUM INTO '.$db->getPdo()->quote($backup));
    $snapshot = [];
    foreach (['companies', 'users', 'trips', 'driving_events', 'personal_access_tokens'] as $table) {
        $columns = Schema::getColumnListing($table);
        $hashes = [];
        foreach ($db->table($table)->select($columns)->orderBy('id')->get() as $row) {
            $hashes[$row->id] = hash('sha256', json_encode($row, JSON_THROW_ON_ERROR));
        }
        $snapshot[$table] = ['columns' => $columns, 'hashes' => $hashes];
    }
    file_put_contents($file, json_encode($snapshot, JSON_THROW_ON_ERROR | JSON_PRETTY_PRINT));
    echo "Consistent private backup created: $backup\n";
} elseif ($mode === 'after') {
    $snapshot = json_decode(file_get_contents($file), true, 512, JSON_THROW_ON_ERROR);
    foreach ($snapshot as $table => $data) {
        $current = $db->table($table)->select($data['columns'])->orderBy('id')->get()->keyBy('id');
        foreach ($data['hashes'] as $id => $hash) {
            if (! isset($current[$id]) || ! hash_equals($hash, hash('sha256', json_encode($current[$id], JSON_THROW_ON_ERROR)))) {
                throw new RuntimeException("Legacy row changed: $table/$id. Investigate before continuing.");
            }
        }
    }
    echo "All original rows and original columns match the baseline exactly.\n";
} else {
    throw new RuntimeException('Use before or after.');
}
foreach ($snapshot as $table => $data) {
    echo $table.': '.count($data['hashes'])." preserved rows\n";
}
echo 'Foreign key violations: '.count($db->select('PRAGMA foreign_key_check'))."\n";
echo json_encode(['companies' => $db->table('companies')->select('id', 'name')->get(), 'users' => $db->table('users')->select('id', 'name', 'role')->get()], JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR)."\n";
