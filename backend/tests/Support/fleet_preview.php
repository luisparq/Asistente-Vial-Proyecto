<?php

use App\Models\User;
use Illuminate\Support\Str;

// Explicit TEST ONLY preview on a newly created isolated SQLite file.
require __DIR__.'/dashboard_preview.php';
$fixturePath = storage_path('framework/testing/dashboard-preview.json');
$fixture = json_decode(file_get_contents($fixturePath), true, 512, JSON_THROW_ON_ERROR);
if (config('database.connections.sqlite.database') !== $fixture['database']) {
    throw new RuntimeException('Unexpected preview database');
}
$platform = new User;
$platform->forceFill(['company_id' => $company->id, 'name' => 'TEST ONLY Plataforma', 'email' => 'platform-'.Str::uuid().'@example.invalid', 'password' => $password, 'role' => 'platform_admin', 'access_status' => 'ACTIVE'])->save();
$fixture['platformEmail'] = $platform->email;
file_put_contents($fixturePath, json_encode($fixture, JSON_THROW_ON_ERROR));
echo "Platform TEST ONLY fixture added in isolated preview.\n";
