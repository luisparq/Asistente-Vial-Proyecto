<?php
namespace Tests\Feature;
use App\Models\{User, Driver, Device};
use App\Support\UtcTimestamp;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;
use Tests\TestCase;
/** All identities and telemetry here are synthetic TEST fixtures. */
class DeviceMonitoringTest extends TestCase {
    use RefreshDatabase;
    private function driver(?int $company = null): User {
        $u=User::factory()->create(array_filter(['role'=>'driver','company_id'=>$company]));
        (new Driver)->forceFill(['company_id'=>$u->company_id,'user_id'=>$u->id,'full_name'=>'TEST ONLY driver','status'=>'ACTIVE'])->save();
        return $u;
    }
    private function headers(User $u): array {
        Auth::forgetGuards();
        return ['Authorization'=>'Bearer '.$u->createToken('TEST ONLY',$u->capabilities(),now()->addHour())->plainTextToken];
    }
    private function report(): array {
        return ['name'=>'TEST ONLY ESP32','observed_at'=>UtcTimestamp::format(now()),'last_contact_at'=>UtcTimestamp::format(now()->subSecond()),
            'ble_state'=>'CONNECTED','imu_state'=>'RECEIVING','gps_state'=>'NO_FIX','technical_error'=>null];
    }
    public function test_report_is_authenticated_idempotent_and_does_not_refresh_on_replay(): void {
        $this->freezeTime(); $u=$this->driver(); $h=$this->headers($u); $url='/api/v1/devices/'.Str::uuid().'/status'; $p=$this->report();
        $this->putJson($url,$p)->assertUnauthorized();
        $this->putJson($url,$p,$h)->assertOk(); $received=Device::first()->received_at;
        $this->travel(4)->minutes();
        $this->putJson($url,$p,$h)->assertOk(); $this->assertDatabaseCount('devices',1);
        $this->assertTrue(Device::first()->received_at->equalTo($received));
        $this->assertSame('Sin comunicación reciente',Device::first()->communicationState());
        $this->putJson($url,array_replace($p,['ble_state'=>'DISCONNECTED']),$h)->assertConflict();
    }
    public function test_latest_report_wins_and_stale_offline_report_is_not_recent(): void {
        $this->freezeTime(); $u=$this->driver();$h=$this->headers($u);$url='/api/v1/devices/'.Str::uuid().'/status';$p=$this->report();
        $this->putJson($url,$p,$h)->assertOk();
        $this->assertSame('Comunicación reciente',Device::first()->communicationState());
        $old=array_replace($p,['observed_at'=>UtcTimestamp::format(now()->subHour()),'last_contact_at'=>null,'gps_state'=>'STALE']);
        $this->putJson($url,$old,$h)->assertOk();$this->assertSame('NO_FIX',Device::first()->gps_state);
        $url2='/api/v1/devices/'.Str::uuid().'/status';$this->putJson($url2,$old,$h)->assertOk();
        $this->assertSame('Sin comunicación reciente',Device::orderByDesc('id')->first()->communicationState());
        $this->assertSame('Sin información',(new Device)->communicationState());
    }
    public function test_company_and_account_binding_cannot_be_forged(): void {
        $u=$this->driver();$url='/api/v1/devices/'.Str::uuid().'/status';$p=$this->report();
        $this->putJson($url,$p,$this->headers($u))->assertOk();
        $same=$this->driver($u->company_id);
        $this->putJson($url,$p,$this->headers($same))->assertForbidden();
        $foreign=$this->driver();
        $this->putJson($url,$p+['company_id'=>$u->company_id],$this->headers($foreign))->assertUnprocessable();
        $this->putJson($url,$p,$this->headers($foreign))->assertOk();
        $this->assertDatabaseCount('devices',2); // Separate tenant namespace, never updates the first device.
        $this->assertSame($u->id,Device::orderBy('id')->first()->user_id);
        $member=User::factory()->create(['company_id'=>$u->company_id]);
        $this->putJson('/api/v1/devices/'.Str::uuid().'/status',$p,$this->headers($member))->assertForbidden();
    }
    public function test_validation_rejects_unknown_states_future_dates_and_invalid_uuid(): void {
        $u=$this->driver();$h=$this->headers($u);$p=$this->report();$url='/api/v1/devices/'.Str::uuid().'/status';
        foreach ([['ble_state'=>'ONLINE'],['gps_state'=>'SENSOR'],['observed_at'=>UtcTimestamp::format(now()->addMinute())],
            ['last_contact_at'=>UtcTimestamp::format(now()->addMinute())],['latitude'=>-16],['user_id'=>$u->id]] as $change)
            $this->putJson($url,array_replace($p,$change),$h)->assertUnprocessable();
        $this->putJson('/api/v1/devices/not-uuid/status',$p,$h)->assertUnprocessable();
        $this->assertDatabaseCount('devices',0);
    }
    public function test_dashboard_is_tenant_scoped_and_platform_has_global_read_only_overview(): void {
        $a=$this->driver();$b=$this->driver();
        $this->putJson('/api/v1/devices/'.Str::uuid().'/status',array_replace($this->report(),['name'=>'TEST ONLY COMPANY A']),$this->headers($a))->assertOk();
        $this->putJson('/api/v1/devices/'.Str::uuid().'/status',array_replace($this->report(),['name'=>'TEST ONLY COMPANY B']),$this->headers($b))->assertOk();
        Auth::forgetGuards(); $fleet=User::factory()->create(['company_id'=>$a->company_id,'role'=>'fleet_admin']);
        $this->actingAs($fleet,'web')->get('/dashboard/devices')->assertOk()->assertSee('TEST ONLY COMPANY A')->assertDontSee('TEST ONLY COMPANY B');
        $this->get('/admin/devices')->assertForbidden();
        Auth::forgetGuards();$admin=User::factory()->create(['company_id'=>null,'role'=>'platform_admin']);
        $this->actingAs($admin,'web')->get('/admin/devices')->assertOk()->assertSee('TEST ONLY COMPANY A')->assertSee('TEST ONLY COMPANY B');
        $this->get('/dashboard/devices')->assertForbidden();
        Auth::forgetGuards();$this->actingAs($a,'web')->get('/dashboard/devices')->assertForbidden();
    }
    public function test_inactive_driver_cannot_report(): void {
        $u=$this->driver();$h=$this->headers($u);$u->driver->update(['status'=>'INACTIVE']);
        $this->putJson('/api/v1/devices/'.Str::uuid().'/status',$this->report(),$h)->assertForbidden();
    }
}
