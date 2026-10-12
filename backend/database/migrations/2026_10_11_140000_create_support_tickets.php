<?php
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
return new class extends Migration {
    public function up(): void {
        Schema::table('devices',fn(Blueprint $t)=>$t->unique(['company_id','id']));
        Schema::create('support_tickets',function(Blueprint $t) {
            $t->id();$t->string('code',40)->unique();$t->foreignId('company_id')->constrained()->restrictOnDelete();
            $t->foreignId('created_by')->constrained('users')->restrictOnDelete();
            $t->foreignId('responsible_id')->nullable()->constrained('users')->restrictOnDelete();
            $t->unsignedBigInteger('device_id')->nullable();
            $t->foreign(['company_id','device_id'])->references(['company_id','id'])->on('devices')->restrictOnDelete();
            $t->string('subject',180);$t->text('description');$t->string('category',20);$t->string('priority',10);$t->string('status',20)->default('OPEN');
            $t->unsignedInteger('version')->default(1);$t->timestamps();$t->index(['company_id','status','updated_at']);$t->index(['status','updated_at']);$t->index(['device_id','updated_at']);
        });
        Schema::create('support_entries',function(Blueprint $t) {
            $t->id();$t->foreignId('support_ticket_id')->constrained()->restrictOnDelete();
            $t->foreignId('actor_id')->constrained('users')->restrictOnDelete();$t->string('kind',20);
            $t->text('comment')->nullable();$t->json('changes')->nullable();$t->timestamp('created_at');
        });
    }
    public function down(): void {
        Schema::dropIfExists('support_entries');Schema::dropIfExists('support_tickets');
        Schema::table('devices',fn(Blueprint $t)=>$t->dropUnique(['company_id','id']));
    }
};
