<?php
namespace Tests\Feature;
use App\Models\{Company, Device, Driver, DrivingEvent, Trip, User, Vehicle};
use App\Services\PlatformOverview;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\TestCase;
/** Synthetic TEST fixtures only; no physical detections are claimed. */
class PlatformOverviewTest extends TestCase {
    use RefreshDatabase;
    private function admin(): User { return User::factory()->create(['role'=>'platform_admin','company_id'=>null]); }
    private function trip(User $user,string $started): Trip {
        return Trip::create(['company_id'=>$user->company_id,'created_by_user_id'=>$user->id,'client_trip_id'=>(string)Str::uuid(),
            'started_at'=>$started,'ended_at'=>null,'status'=>'ACTIVE','client_revision'=>1,'first_received_at'=>now(),'last_changed_at'=>now()]);
    }
    private function event(Trip $trip,string $origin,string $at): void {
        DrivingEvent::create(['company_id'=>$trip->company_id,'trip_id'=>$trip->id,'client_event_id'=>(string)Str::uuid(),
            'type'=>'HARSH_BRAKING','origin'=>$origin,'occurred_at'=>$at,'first_received_at'=>now()]);
    }
    public function test_real_aggregates_and_lima_days_respect_period_and_origin(): void {
        CarbonImmutable::setTestNow(CarbonImmutable::parse('2026-10-11T16:00:00Z'));$this->travelTo(CarbonImmutable::now());
        $platform=$this->admin();$a=User::factory()->create(['role'=>'fleet_admin']);$b=User::factory()->create(['role'=>'fleet_admin']);
        $b->company->forceFill(['status'=>'INACTIVE'])->save();
        $driver=User::factory()->create(['role'=>'driver','company_id'=>$a->company_id]);
        (new Driver)->forceFill(['company_id'=>$a->company_id,'user_id'=>$driver->id,'full_name'=>'TEST ONLY driver','status'=>'ACTIVE'])->save();
        (new Vehicle)->forceFill(['company_id'=>$a->company_id,'plate'=>'TEST ONLY','brand'=>'TEST','model'=>'TEST','status'=>'ACTIVE'])->save();
        $today=$this->trip($driver,'2026-10-11T05:00:00.000Z');
        $yesterday=$this->trip($a,'2026-10-11T04:59:59.999Z');
        $old=$this->trip($b,'2026-09-20T12:00:00.000Z');
        $this->event($today,'SIMULATED','2026-10-11T05:00:01.000Z');
        $this->event($yesterday,'SENSOR','2026-10-11T04:59:59.999Z'); // TEST origin fixture, never enables API SENSOR writing.
        $this->event($old,'SIMULATED','2026-09-20T12:00:01.000Z');
        $response=$this->actingAs($platform)->get('/admin')->assertOk();$inv=$response->viewData('inventory');
        $this->assertSame(2,$inv['companies']);$this->assertSame(1,$inv['active']);$this->assertSame(1,$inv['inactive']);
        $this->assertSame(2,$inv['admins']);$this->assertSame(1,$inv['drivers']);$this->assertSame(1,$inv['vehicles']);
        $this->assertSame(3,$inv['trips']);$this->assertSame(2,$inv['simulated']);$this->assertSame(1,$inv['sensor']);
        $series=$response->viewData('series');$this->assertCount(7,$series);
        $this->assertSame(1,$series[6]['trips']);$this->assertSame(1,$series[6]['simulated']);$this->assertSame(0,$series[6]['sensor']);
        $this->assertSame(1,$series[5]['sensor']);$this->assertSame(1,$series[5]['trips']);
        $this->assertCount(1,$response->viewData('topCompanies'));$this->assertCount(2,$response->viewData('activeCompanies'));
        $month=$this->get('/admin?days=30')->assertOk();$this->assertCount(30,$month->viewData('series'));
        $this->assertSame(3,collect($month->viewData('series'))->sum('trips'));
        $response->assertSee('Histórico no disponible')->assertDontSee($driver->email);
        CarbonImmutable::setTestNow();
    }
    public function test_current_device_distribution_matches_existing_state_and_expires(): void {
        $this->freezeSecond();$platform=$this->admin();$user=User::factory()->create(['role'=>'driver']);
        foreach ([['observed_at'=>now(),'received_at'=>now()],['observed_at'=>now()->subMinutes(4),'received_at'=>now()],
            ['observed_at'=>null,'received_at'=>null],['observed_at'=>now(),'received_at'=>now()->subMinutes(4)]] as $values)
            (new Device)->forceFill($values+['company_id'=>$user->company_id,'user_id'=>$user->id,'client_device_id'=>(string)Str::uuid(),'name'=>'TEST ONLY ESP32'])->save();
        $r=$this->actingAs($platform)->get('/admin')->assertOk();
        $this->assertSame(['total'=>4,'recent'=>1,'unknown'=>1,'stale'=>2],$r->viewData('devices'));
        $this->assertCount(3,$r->viewData('latestDevices'));
        $states=Device::all()->countBy(fn($d)=>$d->communicationState());$this->assertSame(1,$states['Comunicación reciente']);
        $this->travel(4)->minutes();$r=$this->get('/admin')->assertOk();$this->assertSame(0,$r->viewData('devices')['recent']);
        $this->get('/admin/devices')->assertOk();$this->get('/admin/companies')->assertOk();
    }
    public function test_permissions_empty_states_and_invalid_period(): void {
        $this->get('/admin')->assertRedirect('/admin/login');
        $platform=$this->admin();$r=$this->actingAs($platform)->get('/admin')->assertOk();
        $r->assertSee('Sin registros para este periodo.')->assertSee('No hay dispositivos registrados');
        $this->assertSame(0,$r->viewData('inventory')['companies']);
        $this->get('/admin?days=365')->assertSessionHasErrors('days');
        foreach(['fleet_admin','driver','member'] as $role) {
            $u=User::factory()->create(['role'=>$role]);
            $this->actingAs($u)->get('/admin')->assertForbidden();
        }
    }
    public function test_query_count_does_not_grow_with_number_of_companies_or_devices(): void {
        $this->admin();$service=app(PlatformOverview::class);
        DB::enableQueryLog();$service->data(7);$baseline=count(DB::getQueryLog());DB::disableQueryLog();
        for($i=0;$i<8;$i++) {
            $u=User::factory()->create(['role'=>'driver']);$this->trip($u,now()->format('Y-m-d H:i:s.v'));
            (new Device)->forceFill(['company_id'=>$u->company_id,'user_id'=>$u->id,'client_device_id'=>(string)Str::uuid(),
                'name'=>'TEST ONLY ESP32','observed_at'=>now(),'received_at'=>now()])->save();
        }
        DB::flushQueryLog();DB::enableQueryLog();$service->data(7);$populated=count(DB::getQueryLog());DB::disableQueryLog();
        $this->assertLessThanOrEqual($baseline+1,$populated); // At most one eager company query for latest devices.
        $this->assertLessThanOrEqual(18,$populated);
    }
}
