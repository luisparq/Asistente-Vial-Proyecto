<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('sensor_field_trials', function (Blueprint $table) {
            $table->id();
            $table->foreignId('company_id');
            $table->foreignId('device_id');
            $table->foreign(['company_id', 'device_id'])->references(['company_id', 'id'])->on('devices')->restrictOnDelete();
            $table->foreignId('user_id')->constrained()->restrictOnDelete();
            $table->foreignId('session_id')->nullable()->constrained('diagnostic_sessions')->restrictOnDelete();
            $table->foreignId('reference_id')->nullable()->constrained('sensor_field_trials')->restrictOnDelete();
            $table->string('source', 24);
            $table->string('phase', 16);
            $table->string('status', 24)->default('ACTIVE');
            $table->string('mounting', 500);
            $table->timestamp('started_at', 3);
            $table->timestamp('finished_at', 3)->nullable();
            $table->json('summary')->nullable();
            $table->index(['company_id', 'device_id', 'id']);
        });
        Schema::create('sensor_field_rows', function (Blueprint $table) {
            $table->id();
            $table->foreignId('trial_id')->constrained('sensor_field_trials')->cascadeOnDelete();
            $table->json('payload');
        });
        Schema::create('sensor_field_labels', function (Blueprint $table) {
            $table->id();
            $table->foreignId('trial_id')->constrained('sensor_field_trials')->cascadeOnDelete();
            $table->timestamp('marked_at', 3);
            $table->string('label', 24);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('sensor_field_labels');
        Schema::dropIfExists('sensor_field_rows');
        Schema::dropIfExists('sensor_field_trials');
    }
};
