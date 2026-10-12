<?php
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
return new class extends Migration {
    public function up(): void { Schema::table('companies', fn(Blueprint $t) => $t->string('trade_name')->nullable()); }
    public function down(): void { Schema::table('companies', fn(Blueprint $t) => $t->dropColumn('trade_name')); }
};
