<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\TomTomService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Tests\TestCase;

class RoadAccessTest extends TestCase
{
    use RefreshDatabase;

    public function test_provider_endpoints_require_authentication_before_calling_tomtom(): void
    {
        Http::fake();
        foreach (['reverse-geocode', 'route', 'traffic-flow-segment', 'emergency-pois', 'traffic-incidents', 'schools'] as $endpoint) {
            $this->getJson('/api/v1/road/'.$endpoint)->assertUnauthorized();
        }
        $this->getJson('/api/v1/traffic-flow/12/123/456.png')->assertUnauthorized();
        Http::assertNothingSent();
    }

    public function test_fleet_map_uses_session_authenticated_provider_routes(): void
    {
        config(['services.tomtom.key' => 'TEST ONLY']);
        Http::fake(['api.tomtom.com/*' => Http::response('png', 200)]);
        $this->get('/dashboard/road/traffic-flow/12/123/456.png')->assertRedirect('/dashboard/login');
        $this->actingAs(User::factory()->create(['role' => 'fleet_admin']))
            ->get('/dashboard/road/traffic-flow/12/123/456.png')->assertOk();
        $this->get('/dashboard/live-map')->assertOk()
            ->assertSee('/dashboard/road/traffic-flow/', false)
            ->assertSee('/dashboard/road/traffic-incidents', false);
    }

    public function test_nearby_coordinates_share_route_cache_and_http_failures_are_logged(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'TEST ONLY secret']);
        Http::fake(['api.tomtom.com/*' => Http::sequence()->push(['routes' => [[
            'summary' => ['lengthInMeters' => 100], 'legs' => [['points' => []]],
        ]]])->push([], 429)]);
        $service = app(TomTomService::class);
        $first = $service->calculateRoute('-16.398901,-71.536901', '-16.410001,-71.540001');
        $this->assertNotNull($first);
        $this->assertSame($first, $service->calculateRoute('-16.398902,-71.536902', '-16.410002,-71.540002'));
        Http::assertSentCount(1);

        Log::spy();
        $this->assertNull($service->calculateRoute('-17,-72', '-18,-73'));
        Log::shouldHaveReceived('warning')->with('TomTom HTTP failure', \Mockery::on(
            fn ($context) => $context['status'] === 429 && !isset($context['key'])
        ))->once();
    }
}
