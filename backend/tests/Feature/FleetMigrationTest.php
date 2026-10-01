<?php

namespace Tests\Feature;

use Symfony\Component\Process\Process;
use Tests\TestCase;

class FleetMigrationTest extends TestCase
{
    public function test_additive_migration_preserves_legacy_records_and_tokens(): void
    {
        $process = new Process([PHP_BINARY, base_path('tests/Support/fleet_migration.php')], base_path(), ['APP_ENV' => 'testing']);
        $process->mustRun();
        $this->assertStringContainsString('PASS: original companies/users/trips/events/tokens', $process->getOutput());
    }
}
