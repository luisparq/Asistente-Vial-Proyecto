<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('diagnostic_device_codes', function (Blueprint $table) {
            $table->string('code', 63)->primary();
            $table->foreignId('device_id')->unique()->constrained()->restrictOnDelete();
        });

        Schema::create('diagnostic_sessions', function (Blueprint $table) {
            $table->id();
            $table->foreignId('company_id');
            $table->foreignId('device_id');
            $table->uuid('source_session_id');
            $table->timestamp('started_at', 3);
            $table->timestamp('finished_at', 3)->nullable();
            $table->unique(['device_id', 'source_session_id']);
            $table->index(['company_id', 'device_id', 'started_at']);
            $table->foreign(['company_id', 'device_id'])->references(['company_id', 'id'])->on('devices')->restrictOnDelete();
        });

        Schema::create('diagnostic_samples', function (Blueprint $table) {
            $table->id();
            $table->foreignId('session_id')->constrained('diagnostic_sessions')->cascadeOnDelete();
            $table->unsignedBigInteger('sequence');
            $table->timestamp('sampled_at', 3);
            foreach (['ax', 'ay', 'az', 'gx', 'gy', 'gz'] as $axis) {
                $table->smallInteger($axis);
            }
            $table->unique(['session_id', 'sequence']);
            $table->index(['session_id', 'sampled_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('diagnostic_samples');
        Schema::dropIfExists('diagnostic_sessions');
        Schema::dropIfExists('diagnostic_device_codes');
    }
};
