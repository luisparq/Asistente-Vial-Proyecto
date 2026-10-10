<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::table('driving_events', function (Blueprint $table) {
            $table->double('latitude')->nullable();
            $table->double('longitude')->nullable();
            $table->string('location_origin', 16)->nullable();
            $table->unsignedInteger('gps_utc_seconds')->nullable();
            $table->dateTime('gps_received_at', 3)->nullable();
            $table->unsignedInteger('gps_age_ms')->nullable();
        });
    }
    public function down(): void
    {
        Schema::table('driving_events', fn (Blueprint $table) => $table->dropColumn([
            'latitude', 'longitude', 'location_origin', 'gps_utc_seconds', 'gps_received_at', 'gps_age_ms',
        ]));
    }
};
