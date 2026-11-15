<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    private const TYPES = [
        'DRIVER_CREDENTIAL' => 'Credencial del conductor',
        'NATIONAL_ID' => 'DNI vigente',
        'DRIVER_CONTRACT' => 'Contrato',
    ];

    public function up(): void
    {
        Schema::table('drivers', function (Blueprint $table) {
            $table->string('address')->nullable();
            $table->string('phone', 40)->nullable();
            $table->string('photo_path')->nullable();
        });

        foreach (self::TYPES as $code => $name) {
            DB::table('document_types')->updateOrInsert(['code' => $code], ['name' => $name]);
        }
    }

    public function down(): void
    {
        Schema::table('drivers', fn (Blueprint $table) => $table->dropColumn(['address', 'phone', 'photo_path']));
        DB::table('document_types')->whereIn('code', array_keys(self::TYPES))
            ->whereNotIn('id', DB::table('driver_documents')->select('document_type_id'))->delete();
    }
};
