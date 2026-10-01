<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('companies', fn (Blueprint $t) => $t->string('status')->default('ACTIVE'));
        Schema::table('users', fn (Blueprint $t) => $t->string('access_status')->default('ACTIVE'));
        Schema::create('drivers', function (Blueprint $t) {
            $t->id();
            $t->foreignId('company_id')->constrained()->restrictOnDelete();
            $t->unsignedBigInteger('user_id')->unique();
            $t->string('full_name');
            $t->string('status')->default('ACTIVE');
            $t->timestamps();
            $t->unique(['company_id', 'id']);
            $t->foreign(['company_id', 'user_id'])->references(['company_id', 'id'])->on('users')->restrictOnDelete();
        });
        Schema::create('vehicles', function (Blueprint $t) {
            $t->id();
            $t->foreignId('company_id')->constrained()->restrictOnDelete();
            $t->string('plate', 30);
            $t->string('brand', 100);
            $t->string('model', 100);
            $t->string('status')->default('ACTIVE');
            $t->timestamps();
            $t->unique(['company_id', 'plate']);
            $t->unique(['company_id', 'id']);
        });
        Schema::create('vehicle_assignments', function (Blueprint $t) {
            $t->id();
            $t->foreignId('company_id')->constrained()->restrictOnDelete();
            $t->unsignedBigInteger('driver_id');
            $t->unsignedBigInteger('vehicle_id');
            $t->timestamp('started_at');
            $t->timestamp('ended_at')->nullable();
            $t->unsignedTinyInteger('active_slot')->nullable();
            $t->unique(['driver_id', 'active_slot']);
            $t->unique(['vehicle_id', 'active_slot']);
            $t->foreign(['company_id', 'driver_id'])->references(['company_id', 'id'])->on('drivers')->restrictOnDelete();
            $t->foreign(['company_id', 'vehicle_id'])->references(['company_id', 'id'])->on('vehicles')->restrictOnDelete();
        });
        Schema::create('emergency_contacts', function (Blueprint $t) {
            $t->id();
            $t->foreignId('company_id')->constrained()->restrictOnDelete();
            $t->unsignedBigInteger('driver_id')->unique();
            $t->string('name');
            $t->string('relationship', 100);
            $t->string('phone', 40);
            $t->timestamps();
            $t->foreign(['company_id', 'driver_id'])->references(['company_id', 'id'])->on('drivers')->restrictOnDelete();
        });
        Schema::create('document_types', function (Blueprint $t) {
            $t->id();
            $t->string('code')->unique();
            $t->string('name');
        });
        DB::table('document_types')->insert(['code' => 'DRIVING_LICENSE', 'name' => 'Licencia de conducir (brevete)']);
        Schema::create('driver_documents', function (Blueprint $t) {
            $t->id();
            $t->foreignId('company_id')->constrained()->restrictOnDelete();
            $t->unsignedBigInteger('driver_id');
            $t->foreignId('document_type_id')->constrained()->restrictOnDelete();
            $t->string('reference')->nullable();
            $t->date('issued_on')->nullable();
            $t->date('expires_on')->nullable();
            $t->string('review_status')->default('PENDING');
            $t->string('path');
            $t->string('mime', 100);
            $t->unsignedInteger('size');
            $t->timestamps();
            $t->foreign(['company_id', 'driver_id'])->references(['company_id', 'id'])->on('drivers')->restrictOnDelete();
        });
        Schema::create('access_invitations', function (Blueprint $t) {
            $t->id();
            $t->foreignId('company_id')->constrained()->restrictOnDelete();
            $t->unsignedBigInteger('user_id');
            $t->string('token_hash', 64)->unique();
            $t->timestamp('expires_at');
            $t->timestamp('consumed_at')->nullable();
            $t->timestamps();
            $t->foreign(['company_id', 'user_id'])->references(['company_id', 'id'])->on('users')->restrictOnDelete();
        });
        Schema::create('administrative_audits', function (Blueprint $t) {
            $t->id();
            $t->foreignId('actor_id')->nullable()->constrained('users')->restrictOnDelete();
            $t->foreignId('company_id')->constrained()->restrictOnDelete();
            $t->string('action');
            $t->string('entity');
            $t->unsignedBigInteger('entity_id');
            $t->json('changes');
            $t->timestamp('occurred_at');
        });
        Schema::table('trips', function (Blueprint $t) {
            $t->unsignedBigInteger('driver_id')->nullable();
            $t->unsignedBigInteger('vehicle_id')->nullable();
            $t->foreign(['company_id', 'driver_id'])->references(['company_id', 'id'])->on('drivers')->restrictOnDelete();
            $t->foreign(['company_id', 'vehicle_id'])->references(['company_id', 'id'])->on('vehicles')->restrictOnDelete();
        });
    }

    public function down(): void
    {
        throw new RuntimeException('Migración aditiva: revertir requiere un plan explícito de conservación de datos.');
    }
};
