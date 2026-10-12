<?php
namespace Tests\Feature;
use App\Models\{Company,Device,SupportTicket,SupportEntry,User};
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Tests\TestCase;
/** TEST-only fixtures in isolated SQLite, never local customer data. */
class SupportTest extends TestCase {
    use RefreshDatabase;
    private function platform(): User {return User::factory()->create(['role'=>'platform_admin','company_id'=>null]);}
    private function fleet(): User {return User::factory()->create(['role'=>'fleet_admin']);}
    private function device(User $fleet): Device {
        $device=(new Device)->forceFill(['company_id'=>$fleet->company_id,'user_id'=>$fleet->id,'client_device_id'=>(string)Str::uuid(),
            'name'=>'TEST ONLY ESP32','observed_at'=>now(),'received_at'=>now(),'last_contact_at'=>now(),
            'ble_state'=>'CONNECTED','imu_state'=>'RECEIVING','gps_state'=>'NO_FIX']);$device->save();return $device;
    }
    private function data(array $extra=[]): array {return $extra+['subject'=>'TEST ONLY BLE issue','description'=>'TEST ONLY controlled report','category'=>'BLE','priority'=>'MEDIUM'];}
    private function create(User $fleet,?Device $device=null): SupportTicket {
        $this->actingAs($fleet)->post('/dashboard/support',$this->data(['device_id'=>$device?->id]))->assertRedirect()->assertSessionHasNoErrors();
        return SupportTicket::latest('id')->firstOrFail();
    }
    public function test_company_creation_comment_global_management_and_history_are_atomic(): void {
        $fleet=$this->fleet();$device=$this->device($fleet);$ticket=$this->create($fleet,$device);$global=$this->platform();
        $this->assertSame($fleet->company_id,$ticket->company_id);$this->assertStringStartsWith('AV-',$ticket->code);
        $this->assertDatabaseCount('support_entries',1);
        $this->post('/dashboard/support/'.$ticket->id.'/comments',['comment'=>'TEST ONLY follow-up'])->assertRedirect();
        $this->actingAs($global)->put('/admin/support/'.$ticket->id,['status'=>'IN_PROGRESS','priority'=>'HIGH','device_id'=>$device->id,'responsible_id'=>$global->id,'version'=>$ticket->fresh()->version])->assertRedirect()->assertSessionHasNoErrors();
        $this->assertSame('IN_PROGRESS',$ticket->fresh()->status);$this->assertSame($global->id,$ticket->fresh()->responsible_id);
        $this->put('/admin/support/'.$ticket->id,['status'=>'RESOLVED','priority'=>'HIGH','device_id'=>$device->id,'responsible_id'=>$global->id,'version'=>$ticket->fresh()->version])->assertRedirect();
        $this->assertDatabaseCount('support_entries',4);$this->assertDatabaseCount('administrative_audits',4);
        $this->assertSame('OPEN',SupportEntry::orderBy('id')->first()->changes['status']['to']);
        $this->get('/admin/support/'.$ticket->id)->assertOk()->assertSee('TEST ONLY follow-up')->assertSee($fleet->name)->assertSee($global->name)->assertSee('Últimos reportes conocidos');
        $this->get('/admin/support')->assertOk()->assertViewHas('metrics',fn($m)=>$m['RESOLVED']===1);
    }
    public function test_cross_company_ids_comments_updates_and_devices_are_denied(): void {
        $a=$this->fleet();$b=$this->fleet();$device=$this->device($a);$ticket=$this->create($a,$device);
        $this->actingAs($b)->get('/dashboard/support/'.$ticket->id)->assertNotFound();
        $this->post('/dashboard/support/'.$ticket->id.'/comments',['comment'=>'TEST ONLY intrusion'])->assertNotFound();
        $this->get('/dashboard/devices/'.$device->id)->assertNotFound();
        $this->post('/dashboard/support',$this->data(['device_id'=>$device->id]))->assertSessionHasErrors('device_id');
        $this->post('/dashboard/support',$this->data(['company_id'=>$a->company_id]))->assertSessionHasErrors('company_id');
        $this->get('/dashboard/support?company_id='.$a->company_id)->assertSessionHasErrors('company_id');
        $this->put('/dashboard/support/'.$ticket->id,['status'=>'CLOSED'])->assertForbidden();
        $this->assertDatabaseCount('support_tickets',1);$this->assertDatabaseCount('support_entries',1);
        $this->get('/dashboard/support')->assertOk()->assertDontSee($ticket->subject);
    }
    public function test_global_cannot_assign_device_of_another_company_or_enterprise_as_staff(): void {
        $a=$this->fleet();$b=$this->fleet();$ticket=$this->create($a);$foreign=$this->device($b);$admin=$this->platform();
        $this->actingAs($admin)->put('/admin/support/'.$ticket->id,['status'=>'IN_PROGRESS','priority'=>'HIGH','version'=>1,'device_id'=>$foreign->id,'responsible_id'=>$b->id])->assertSessionHasErrors(['device_id','responsible_id']);
        $this->post('/admin/support',$this->data(['company_id'=>$a->company_id,'device_id'=>$foreign->id]))->assertSessionHasErrors('device_id');
        $this->assertDatabaseCount('support_tickets',1);$this->assertSame('OPEN',$ticket->fresh()->status);
    }
    public function test_validations_unknown_fields_stale_version_and_escaped_comments(): void {
        $fleet=$this->fleet();$this->actingAs($fleet);
        foreach([['subject'=>''],['category'=>'SENSOR'],['priority'=>'URGENT'],['status'=>'RESOLVED'],['responsible_id'=>1]] as $bad)
            $this->post('/dashboard/support',$this->data($bad))->assertSessionHasErrors();
        $this->assertDatabaseCount('support_tickets',0);$ticket=$this->create($fleet);
        $this->post('/dashboard/support/'.$ticket->id.'/comments',['comment'=>'<script>TEST ONLY</script>'])->assertRedirect();
        $this->get('/dashboard/support/'.$ticket->id)->assertOk()->assertSee('&lt;script&gt;TEST ONLY&lt;/script&gt;',false)->assertDontSee('<script>TEST ONLY</script>',false);
        $this->actingAs($this->platform())->put('/admin/support/'.$ticket->id,['status'=>'CLOSED','priority'=>'LOW','version'=>1])->assertConflict();
        $this->assertSame('OPEN',$ticket->fresh()->status);$this->assertDatabaseCount('support_entries',2);
        $this->get('/admin/support?sort=password')->assertSessionHasErrors('sort');
    }
    public function test_permissions_filters_device_links_and_existing_pages(): void {
        $fleet=$this->fleet();$device=$this->device($fleet);$ticket=$this->create($fleet,$device);
        $this->get('/dashboard/devices/'.$device->id)->assertOk()->assertSee($ticket->code);
        $this->get('/dashboard/support?q=TEST&priority=MEDIUM')->assertOk()->assertSee($ticket->code);
        $this->get('/dashboard/support?status=RESOLVED')->assertOk()->assertViewHas('tickets',fn($rows)=>$rows->isEmpty());
        $this->get('/dashboard')->assertOk();$this->get('/admin/support')->assertForbidden();
        $this->actingAs($this->platform());foreach(['/admin','/admin/devices','/admin/analytics','/admin/support','/admin/devices/'.$device->id] as $url)$this->get($url)->assertOk();
        $this->get('/dashboard/support')->assertForbidden();
        foreach(['driver','member'] as $role){$this->actingAs(User::factory()->create(['role'=>$role]));$this->get('/dashboard/support')->assertForbidden();$this->get('/admin/support')->assertForbidden();}
    }
}
