<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('trip_track_points', function (Blueprint $table) {
            $table->id();
            $table->foreignId('trip_id')->constrained()->cascadeOnDelete();
            $table->uuid('client_point_id');
            $table->dateTime('captured_at', 3);
            $table->double('latitude');
            $table->double('longitude');
            $table->unique(['trip_id', 'client_point_id']);
            $table->index(['trip_id', 'captured_at', 'id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('trip_track_points');
    }
};
