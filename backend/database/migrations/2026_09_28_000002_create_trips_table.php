<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('trips', function (Blueprint $table) {
            $table->id();
            $table->foreignId('company_id')->constrained()->restrictOnDelete();
            $table->unsignedBigInteger('created_by_user_id');
            $table->foreign(['company_id', 'created_by_user_id'])->references(['company_id', 'id'])->on('users')->restrictOnDelete();
            $table->uuid('client_trip_id');
            $table->dateTime('started_at', 3);
            $table->dateTime('ended_at', 3)->nullable();
            $table->string('status', 16);
            $table->unsignedTinyInteger('client_revision');
            $table->dateTime('first_received_at', 3);
            $table->dateTime('last_changed_at', 3);
            $table->unique(['company_id', 'client_trip_id']);
            $table->unique(['company_id', 'id']);
            $table->index(['company_id', 'created_by_user_id', 'started_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('trips');
    }
};
