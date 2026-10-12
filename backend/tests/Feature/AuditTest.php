<?php
namespace Tests\Feature;
use App\Models\{AdministrativeAudit as Audit,User,SupportTicket};
use App\Services\AdministrativeAudit;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;
/** Synthetic TEST fixtures only, never modifying local historical audit records. */
class AuditTest extends TestCase {
    use RefreshDatabase;
    private function admin(): User {return User::factory()->create(['role'=>'platform_admin','company_id'=>null]);}
    private function row(User $actor,array $extra=[]): Audit {
        return Audit::create($extra+['actor_id'=>$actor->id,'company_id'=>$actor->company_id,'action'=>'company.created','entity'=>'companies',
            'entity_id'=>$actor->company_id??1,'changes'=>['fields'=>['name']],'occurred_at'=>'2026-10-11 04:30:00']);
    }
    private function query(array $f=[]): string {return http_build_query($f+['from'=>'2026-10-10','to'=>'2026-10-11']);}
    public function test_permissions_read_only_and_existing_admin_modules(): void {
        $this->get('/admin/audits')->assertRedirect('/admin/login');$admin=$this->admin();$row=$this->row($admin);
        $this->actingAs($admin);foreach(['/admin','/admin/companies','/admin/devices','/admin/analytics','/admin/support','/admin/audits','/admin/audits/'.$row->id] as $url)$this->get($url)->assertOk();
        $this->put('/admin/audits/'.$row->id,[])->assertStatus(405);$this->delete('/admin/audits/'.$row->id)->assertStatus(405);
        foreach(['driver','fleet_admin','member'] as $role){$this->actingAs(User::factory()->create(['role'=>$role]));$this->get('/admin/audits')->assertForbidden();$this->get('/admin/audits/'.$row->id)->assertForbidden();$this->get('/admin/audits/export')->assertForbidden();}
        $this->assertDatabaseCount('administrative_audits',1);
    }
    public function test_combined_filters_pagination_and_lima_boundary(): void {
        $admin=$this->admin();$actor=User::factory()->create(['role'=>'fleet_admin']);$foreign=User::factory()->create();
        for($i=0;$i<26;$i++)$this->row($actor);$this->row($foreign,['occurred_at'=>'2026-10-11 05:00:00']);
        $f=['company_id'=>$actor->company_id,'actor_id'=>$actor->id,'module'=>'companies','action'=>'company.created','resource_id'=>$actor->company_id,'q'=>'nueva empresa'];
        $r=$this->actingAs($admin)->get('/admin/audits?'.$this->query($f))->assertOk();$this->assertSame(26,$r->viewData('total'));$this->assertCount(25,$r->viewData('audits'));
        $r->assertSee('Se registró una nueva empresa')->assertSee('10/10/2026',false);
        $page=$this->get('/admin/audits?'.$this->query($f+['page'=>2]))->assertOk();$this->assertCount(1,$page->viewData('audits'));$page->assertSee('company_id=',false);
        $this->get('/admin/audits?'.$this->query(['from'=>'2026-10-11','to'=>'2026-10-11']))->assertViewHas('total',1);
        foreach([['sort'=>'password'],['from'=>'INVALID'],['from'=>'2020-01-01'],['module'=>'UNKNOWN'],['token'=>'TEST']] as $bad)$this->get('/admin/audits?'.$this->query($bad))->assertSessionHasErrors();
    }
    public function test_detail_hides_sensitive_metadata_and_does_not_mutate_history(): void {
        $admin=$this->admin();$row=$this->row($admin,['changes'=>['fields'=>['password','token','name'],'token'=>'SECRET_TEST_TOKEN','password'=>'SECRET_TEST_PASSWORD',
            'before'=>['email'=>'PRIVATE_TEST_EMAIL'],'transitions'=>['priority'=>['from'=>'SECRET_TEST_PASSWORD','to'=>'HIGH'],'password'=>['from'=>'SECRET_TEST_PASSWORD','to'=>'SECRET_TEST_TOKEN']]]]);
        $before=$row->getRawOriginal('changes');$r=$this->actingAs($admin)->get('/admin/audits/'.$row->id)->assertOk();
        foreach(['SECRET_TEST_TOKEN','SECRET_TEST_PASSWORD','PRIVATE_TEST_EMAIL'] as $secret)$r->assertDontSee($secret);
        $r->assertSee('Contenido oculto')->assertSee('No registrado');$this->assertSame($before,$row->fresh()->getRawOriginal('changes'));
        $this->row($admin,['changes'=>['fields'=>'SECRET_TEST_TOKEN','actor_role'=>['token'=>'SECRET_TEST_TOKEN'],'transitions'=>'SECRET_TEST_TOKEN']]);
        $this->get('/admin/audits?'.$this->query())->assertOk()->assertDontSee('SECRET_TEST_TOKEN');
    }
    public function test_csv_respects_filters_safe_formula_and_excludes_secrets(): void {
        $admin=$this->admin();$actor=User::factory()->create(['role'=>'fleet_admin','name'=>'=TEST FORMULA']);$actor->company->update(['name'=>'@TEST COMPANY']);
        $row=$this->row($actor,['changes'=>['fields'=>['name'],'password'=>'SECRET_TEST_PASSWORD']]);$this->row($admin);
        $csv=$this->actingAs($admin)->get('/admin/audits/export?'.$this->query(['actor_id'=>$actor->id]))->assertOk()->streamedContent();
        $this->assertStringStartsWith("\xEF\xBB\xBF",$csv);$lines=explode("\n",trim($csv));$this->assertCount(2,$lines);$cells=str_getcsv($lines[1],';','"','');
        $this->assertSame("'=TEST FORMULA",$cells[1]);$this->assertSame("'@TEST COMPANY",$cells[3]);$this->assertStringNotContainsString('SECRET_TEST_PASSWORD',$csv);$this->assertStringNotContainsString($actor->email,$csv);
    }
    public function test_new_instrumentation_records_safe_context_close_reopen_without_duplicates(): void {
        $admin=$this->admin();$fleet=User::factory()->create(['role'=>'fleet_admin']);$this->actingAs($admin);
        $this->post('/admin/companies/'.$fleet->company_id.'/state',['status'=>'INACTIVE'])->assertRedirect();
        $audit=Audit::first();$this->assertSame('platform_admin',$audit->changes['actor_role']);$this->assertSame('SUCCESS',$audit->changes['result']);$this->assertSame(['from'=>'ACTIVE','to'=>'INACTIVE'],$audit->changes['transitions']['status']);
        $this->post('/admin/support',['company_id'=>$fleet->company_id,'subject'=>'TEST ticket','description'=>'TEST description','category'=>'OTHER','priority'=>'LOW'])->assertRedirect();
        $ticket=SupportTicket::first();$this->put('/admin/support/'.$ticket->id,['version'=>1,'status'=>'CLOSED','priority'=>'LOW'])->assertRedirect();
        $this->assertDatabaseHas('administrative_audits',['action'=>'support.closed']);
        $this->put('/admin/support/'.$ticket->id,['version'=>2,'status'=>'OPEN','priority'=>'LOW'])->assertRedirect();$this->assertDatabaseHas('administrative_audits',['action'=>'support.reopened']);
        $this->assertDatabaseCount('administrative_audits',4);
        AdministrativeAudit::record($admin,null,'support.updated',$ticket,['password','priority'],['password'=>['from'=>'SECRET','to'=>'SECRET'],'priority'=>['from'=>'LOW','to'=>'HIGH']]);
        $latest=Audit::latest('id')->first();$this->assertArrayNotHasKey('password',$latest->changes['transitions']);$this->assertSame(['priority'],$latest->changes['fields']);
    }
}
