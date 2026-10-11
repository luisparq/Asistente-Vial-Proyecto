<?php

namespace Tests\Feature;

use App\Models\User;
use App\Models\DrivingEvent;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;
use Tests\TestCase;

/** Synthetic TEST fixtures, not physical GPS observations. */
class EventLocationTest extends TestCase
{
    use RefreshDatabase;
    private function setupTrip(): array
    {
        $user = User::factory()->create();
        Auth::forgetGuards();
        $headers = ['Authorization' => 'Bearer '.$user->createToken('TEST ONLY', $user->capabilities(), now()->addHour())->plainTextToken];
        $url = '/api/v1/trips/'.Str::uuid();
        $this->putJson($url, ['started_at'=>'2026-10-09T12:00:00.000Z','ended_at'=>null,'status'=>'ACTIVE','client_revision'=>1],$headers)->assertCreated();
        return [$url, $headers];
    }
    private function event(bool $gps = true): array
    {
        $data = ['type'=>'SHARP_TURN','origin'=>'SIMULATED','occurred_at'=>'2026-10-09T12:00:02.000Z'];
        return $gps ? $data + ['latitude'=>-16.0,'longitude'=>-71.0,'location_origin'=>'GPS_NEO6M',
            'gps_utc_seconds'=>43201,'gps_received_at'=>'2026-10-09T12:00:01.500Z','gps_age_ms'=>1000] : $data;
    }
    public function test_location_persists_queries_and_retries_without_duplicates(): void
    {
        [$url,$headers] = $this->setupTrip(); $eventUrl=$url.'/events/'.Str::uuid(); $data=$this->event();
        $this->putJson($eventUrl,$data,$headers)->assertCreated()->assertJsonPath('data.location_origin','GPS_NEO6M')->assertJsonPath('data.gps_received_at',$data['gps_received_at']);
        $this->putJson($eventUrl,$data,$headers)->assertOk()->assertJsonPath('sync.disposition','already_applied');
        $this->assertDatabaseCount('driving_events',1);
        $this->getJson($url.'/events',$headers)->assertOk()->assertJsonPath('data.0.gps_age_ms',1000);
        $this->putJson($eventUrl,array_replace($data,['latitude'=>-15.0]),$headers)->assertConflict();
        Auth::forgetGuards(); $other=User::factory()->create();
        $foreign=['Authorization'=>'Bearer '.$other->createToken('TEST ONLY',$other->capabilities(),now()->addHour())->plainTextToken];
        $this->getJson($url.'/events',$foreign)->assertNotFound();
    }
    public function test_legacy_events_keep_null_location_and_cannot_be_enriched_on_retry(): void
    {
        [$url,$headers] = $this->setupTrip(); $eventUrl=$url.'/events/'.Str::uuid();
        $this->putJson($eventUrl,$this->event(false),$headers)->assertCreated()->assertJsonPath('data.latitude',null);
        $this->putJson($eventUrl,$this->event(false),$headers)->assertOk();
        $this->putJson($eventUrl,$this->event(),$headers)->assertConflict();
        $this->assertNull(DrivingEvent::first()->gps_received_at);
    }
    public function test_invalid_partial_stale_and_future_locations_are_rejected(): void
    {
        [$url,$headers]=$this->setupTrip();
        foreach ([['latitude'=>91],['longitude'=>181],['location_origin'=>'SIMULATED'],['gps_age_ms'=>2001],
            ['gps_utc_seconds'=>86400],['longitude'=>null],['gps_received_at'=>'2026-10-09T12:00:02.001Z'],
            ['gps_received_at'=>'2026-10-09T11:59:59.000Z'],['gps_age_ms'=>499],['origin'=>'SENSOR']] as $change) {
            $this->putJson($url.'/events/'.Str::uuid(),array_replace($this->event(),$change),$headers)->assertUnprocessable();
        }
        $this->assertDatabaseCount('driving_events',0);
    }
}
