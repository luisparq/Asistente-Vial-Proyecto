<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('driving_events', function (Blueprint $table) {
            $table->id();
            $table->foreignId('company_id')->constrained()->restrictOnDelete();
            $table->unsignedBigInteger('trip_id');
            $table->foreign(['company_id', 'trip_id'])->references(['company_id', 'id'])->on('trips')->restrictOnDelete();
            $table->uuid('client_event_id');
            $table->string('type', 32);
            $table->string('origin', 16);
            $table->dateTime('occurred_at', 3);
            $table->dateTime('first_received_at', 3);
            $table->unique(['company_id', 'client_event_id']);
            $table->index(['company_id', 'trip_id', 'occurred_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('driving_events');
    }
};
