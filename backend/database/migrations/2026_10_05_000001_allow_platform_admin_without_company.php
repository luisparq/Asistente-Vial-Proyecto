<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', fn (Blueprint $t) => $t->unsignedBigInteger('company_id')->nullable()->change());
        // Global provisioning has no tenant, but must retain its administrative audit.
        Schema::table('administrative_audits', fn (Blueprint $t) => $t->unsignedBigInteger('company_id')->nullable()->change());

        if (DB::getDriverName() === 'sqlite') {
            foreach (['INSERT', 'UPDATE'] as $operation) {
                $name = 'users_company_role_'.strtolower($operation);
                DB::unprepared("CREATE TRIGGER $name BEFORE $operation ON users
                    WHEN (NEW.role = 'platform_admin' AND NEW.company_id IS NOT NULL)
                      OR (NEW.role <> 'platform_admin' AND NEW.company_id IS NULL)
                    BEGIN SELECT RAISE(ABORT, 'company_id must match the user role'); END");
            }
        }
    }

    public function down(): void
    {
        throw new RuntimeException('Revertir requiere un plan explícito para conservar administradores globales y auditorías sin empresa.');
    }
};
