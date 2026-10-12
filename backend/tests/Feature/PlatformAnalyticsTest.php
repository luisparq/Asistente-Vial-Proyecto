<?php
namespace Tests\Feature;
use App\Models\{User,Trip,DrivingEvent};
use App\Http\Controllers\PlatformAnalyticsController;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;
/** TEST-only fixtures; SENSOR fixture is not enabled API writing or a physical observation. */
class PlatformAnalyticsTest extends TestCase {
    use RefreshDatabase;
    private function admin(): User { return User::factory()->create(['role'=>'platform_admin','company_id'=>null]); }
    private function trip(User $u): Trip {
        return Trip::create(['company_id'=>$u->company_id,'created_by_user_id'=>$u->id,'client_trip_id'=>(string)Str::uuid(),
            'started_at'=>'2026-10-10 20:00:00.000','ended_at'=>null,'status'=>'ACTIVE','client_revision'=>1,
            'first_received_at'=>'2026-10-10 20:00:00.000','last_changed_at'=>'2026-10-10 20:00:00.000']);
    }
    private function event(Trip $trip,string $origin='SIMULATED',array $extra=[]): DrivingEvent {
        return DrivingEvent::create($extra+['company_id'=>$trip->company_id,'trip_id'=>$trip->id,'client_event_id'=>(string)Str::uuid(),
            'occurred_at'=>'2026-10-11 04:30:00.000','first_received_at'=>'2026-10-11 04:30:00.000','type'=>'HARSH_BRAKING','origin'=>$origin]);
    }
    private function query(array $extra=[]): string { return http_build_query($extra+['from'=>'2026-10-10','to'=>'2026-10-11']); }
    public function test_filters_origins_lima_buckets_comparison_and_private_fields(): void {
        $a=User::factory()->create();$b=User::factory()->create();$ta=$this->trip($a);$tb=$this->trip($b);
        $this->event($ta,'SIMULATED',['latitude'=>-16,'longitude'=>-71]);$this->event($ta,'SENSOR');$this->event($tb);
        $this->actingAs($this->admin());$r=$this->get('/admin/analytics?'.$this->query())->assertOk();
        $this->assertSame(2,$r->viewData('summary')['companies']);$this->assertSame(3,$r->viewData('summary')['events']);
        $r=$this->get('/admin/analytics?'.$this->query(['company_id'=>$a->company_id,'origin'=>'SIMULATED','type'=>'HARSH_BRAKING']))->assertOk();
        $this->assertSame(1,$r->viewData('summary')['events']);$this->assertSame(0,$r->viewData('summary')['sensor']);
        $this->assertSame(1,$r->viewData('daily')[0]['simulated']);$this->assertSame(0,$r->viewData('daily')[1]['simulated']);
        $this->assertSame(1,$r->viewData('hourly')[23]['simulated']);$this->assertSame(1,$r->viewData('weekly')[5]['simulated']);
        $this->assertSame(1,$r->viewData('monthly')[0]['simulated']);
        $this->assertCount(1,$r->viewData('comparison'));$this->assertCount(1,$r->viewData('geojson')['features']);
        $r->assertDontSee($a->email)->assertDontSee($ta->client_trip_id);
        $this->assertArrayNotHasKey('trip_url',$r->viewData('geojson')['features'][0]['properties']);
        $this->assertStringContainsString("script-src 'self'",$r->headers->get('Content-Security-Policy'));
    }
    public function test_invalid_coordinates_excluded_and_map_is_bounded(): void {
        $u=User::factory()->create();$trip=$this->trip($u);$this->event($trip);$this->event($trip,'SIMULATED',['latitude'=>91,'longitude'=>-71]);
        $this->event($trip,'SIMULATED',['latitude'=>-16,'longitude'=>181]);$this->event($trip,'SIMULATED',['latitude'=>-16,'longitude'=>null]);
        $rows=[];for($i=0;$i<1001;$i++) $rows[]=['company_id'=>$u->company_id,'trip_id'=>$trip->id,'client_event_id'=>(string)Str::uuid(),
            'type'=>'SHARP_TURN','origin'=>'SIMULATED','occurred_at'=>'2026-10-10 22:00:00.000','first_received_at'=>'2026-10-10 22:00:00.000','latitude'=>-16,'longitude'=>-71];
        foreach(array_chunk($rows,100) as $chunk) DB::table('driving_events')->insert($chunk);
        $r=$this->actingAs($this->admin())->get('/admin/analytics?'.$this->query())->assertOk();
        $this->assertSame(1001,$r->viewData('geoCount'));$this->assertCount(1000,$r->viewData('geojson')['features']);
    }
    public function test_csv_filters_safe_formula_cells_and_contains_no_credentials(): void {
        $a=User::factory()->create();$a->company->update(['name'=>'=HYPERLINK("TEST")']);$b=User::factory()->create();
        $this->event($this->trip($a));$this->event($this->trip($b),'SENSOR');$this->actingAs($this->admin());
        $csv=$this->get('/admin/analytics/export?'.$this->query(['company_id'=>$a->company_id,'origin'=>'SIMULATED']))->assertOk()->streamedContent();
        $lines=explode("\n",trim($csv));$this->assertCount(2,$lines);$cells=str_getcsv($lines[1],';','"','');
        $this->assertSame("'=HYPERLINK(\"TEST\")",$cells[2]);$this->assertSame('1',$cells[5]);$this->assertSame('0',$cells[6]);
        $this->assertStringNotContainsString($a->email,$csv);$this->assertStringNotContainsString($b->company->name,$csv);
        foreach(['+TEST','-TEST','@TEST','  =TEST',"\t=TEST"] as $unsafe) $this->assertSame("'".$unsafe,PlatformAnalyticsController::csvCell($unsafe));
        $this->assertSame('Empresa normal',PlatformAnalyticsController::csvCell('Empresa normal'));
    }
    public function test_permissions_strict_filters_empty_and_existing_pages(): void {
        $this->get('/admin/analytics')->assertRedirect('/admin/login');
        $this->actingAs($this->admin())->get('/admin/analytics')->assertOk()->assertSee('Sin eventos para estos filtros.');
        foreach([['origin'=>'REAL'],['type'=>'UNKNOWN'],['company_id'=>999],['from'=>'2026-10-11','to'=>'2026-10-10'],
            ['from'=>'2020-01-01','to'=>'2026-10-10'],['from'=>'INVALID'],['sort'=>'password'],['token'=>'TEST']] as $bad)
            $this->get('/admin/analytics?'.$this->query($bad))->assertSessionHasErrors();
        foreach(['/admin','/admin/companies','/admin/devices'] as $url) $this->get($url)->assertOk();
        foreach(['driver','fleet_admin','member'] as $role) {
            $this->actingAs(User::factory()->create(['role'=>$role]));
            $this->get('/admin/analytics')->assertForbidden();$this->get('/admin/analytics/export')->assertForbidden();
        }
    }
}
