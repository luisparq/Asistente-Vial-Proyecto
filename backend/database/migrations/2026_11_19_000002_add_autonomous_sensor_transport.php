<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('diagnostic_samples', function (Blueprint $t) {
            $t->timestamp('mqtt_received_at', 3)->nullable();
            $t->timestamp('ble_received_at', 3)->nullable();
            $t->foreignId('relay_user_id')->nullable()->constrained('users');
            $t->unsignedSmallInteger('profile_version')->default(0);
            $t->unsignedTinyInteger('motion_range')->default(4);
            $t->unsignedTinyInteger('motion_event')->default(0);
            $t->unsignedTinyInteger('motion_flags')->default(0);
        });
        Schema::create('diagnostic_sensor_events', function (Blueprint $t) {
            $t->id();
            $t->foreignId('sample_id')->unique()->constrained('diagnostic_samples');
            $t->foreignId('company_id');
            $t->foreignId('device_id');
            $t->unsignedTinyInteger('type');
            $t->timestamp('received_at', 3);
        });
        Schema::create('diagnostic_device_state', function (Blueprint $t) {
            $t->foreignId('device_id')->primary()->constrained('devices');
            $t->string('session_id', 36);
            $t->unsignedBigInteger('sequence');
            $t->json('payload');
            $t->json('mqtt_payload')->nullable();
            $t->timestamp('mqtt_received_at', 3)->nullable();
            $t->timestamp('received_at', 3);
        });
        Schema::create('diagnostic_device_commands', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignId('device_id')->constrained('devices');
            $t->foreignId('user_id')->constrained('users');
            $t->string('operation', 24);
            $t->string('status', 24)->default('QUEUED');
            $t->timestamp('created_at', 3);
            $t->timestamp('published_at', 3)->nullable();
            $t->timestamp('acknowledged_at', 3)->nullable();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('diagnostic_device_commands');
        Schema::dropIfExists('diagnostic_device_state');
        Schema::dropIfExists('diagnostic_sensor_events');
        Schema::table('diagnostic_samples', function (Blueprint $t) {
            $t->dropConstrainedForeignId('relay_user_id');
            $t->dropColumn(['mqtt_received_at', 'ble_received_at', 'profile_version', 'motion_range', 'motion_event', 'motion_flags']);
        });
    }
};
