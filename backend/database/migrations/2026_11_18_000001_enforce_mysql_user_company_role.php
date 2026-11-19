<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        if (!in_array(DB::getDriverName(), ['mysql', 'mariadb'], true)) {
            return;
        }

        foreach (['INSERT', 'UPDATE'] as $operation) {
            $name = 'users_company_role_'.strtolower($operation);
            DB::unprepared("CREATE TRIGGER $name BEFORE $operation ON users FOR EACH ROW
                BEGIN
                    IF (NEW.role = 'platform_admin' AND NEW.company_id IS NOT NULL)
                        OR (NEW.role <> 'platform_admin' AND NEW.company_id IS NULL) THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'company_id must match the user role';
                    END IF;
                END");
        }
    }

    public function down(): void
    {
        if (!in_array(DB::getDriverName(), ['mysql', 'mariadb'], true)) {
            return;
        }

        DB::unprepared('DROP TRIGGER IF EXISTS users_company_role_insert');
        DB::unprepared('DROP TRIGGER IF EXISTS users_company_role_update');
    }
};
