<?php
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
return new class extends Migration {
    public function up(): void {
        Schema::create('devices', function (Blueprint $t) {
            $t->id(); $t->foreignId('company_id')->constrained()->restrictOnDelete();
            $t->unsignedBigInteger('user_id'); $t->uuid('client_device_id');
            $t->string('name', 100); $t->timestamp('observed_at', 3)->nullable();
            $t->timestamp('received_at', 3)->nullable(); $t->timestamp('last_contact_at', 3)->nullable();
            $t->string('ble_state', 40)->nullable(); $t->string('imu_state', 40)->nullable();
            $t->string('gps_state', 40)->nullable(); $t->string('technical_error', 255)->nullable();
            $t->timestamps(); $t->unique(['company_id', 'client_device_id']);
            $t->foreign(['company_id', 'user_id'])->references(['company_id', 'id'])->on('users')->restrictOnDelete();
        });
    }
    public function down(): void { Schema::dropIfExists('devices'); }
};
