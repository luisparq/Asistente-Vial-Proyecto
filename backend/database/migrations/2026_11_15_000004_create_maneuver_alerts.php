<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('maneuver_alerts', function (Blueprint $table) {
            $table->id();
            $table->foreignId('company_id')->constrained()->cascadeOnDelete();
            $table->foreignId('trip_id')->constrained()->cascadeOnDelete();
            $table->foreignId('driver_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('vehicle_id')->nullable()->constrained()->nullOnDelete();
            $table->uuid('client_alert_id');
            $table->dateTime('occurred_at', 3);
            $table->boolean('test_only');
            $table->double('latitude')->nullable();
            $table->double('longitude')->nullable();
            $table->dateTime('location_captured_at', 3)->nullable();
            $table->string('location_status', 12);
            $table->dateTime('first_received_at', 3);
            $table->dateTime('reviewed_at', 3)->nullable();
            $table->foreignId('reviewed_by_user_id')->nullable()->constrained('users')->nullOnDelete();
            $table->unique(['company_id', 'client_alert_id']);
            $table->index(['company_id', 'test_only', 'reviewed_at']);
        });

        Schema::create('driving_event_maneuver_alert', function (Blueprint $table) {
            $table->foreignId('maneuver_alert_id')->constrained()->cascadeOnDelete();
            $table->foreignId('driving_event_id')->constrained()->cascadeOnDelete();
            $table->primary(['maneuver_alert_id', 'driving_event_id'], 'maneuver_alert_event_pk');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('driving_event_maneuver_alert');
        Schema::dropIfExists('maneuver_alerts');
    }
};
