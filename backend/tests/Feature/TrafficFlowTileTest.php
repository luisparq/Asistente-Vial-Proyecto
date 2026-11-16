<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class TrafficFlowTileTest extends TestCase
{
    public function test_it_proxies_and_caches_a_tomtom_flow_tile_without_exposing_the_key(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'secret-key']);
        Http::preventStrayRequests();
        Http::fake([
            'https://api.tomtom.com/traffic/map/4/tile/flow/relative0/12/123/456.png*' => Http::response('png-bytes', 200, ['Content-Type' => 'image/png']),
        ]);

        $this->get('/api/v1/traffic-flow/12/123/456.png')->assertOk()
            ->assertHeader('Content-Type', 'image/png')->assertSee('png-bytes');
        $this->get('/api/v1/traffic-flow/12/123/456.png')->assertOk();

        Http::assertSentCount(1);
        Http::assertSent(fn ($request) => $request->url() === 'https://api.tomtom.com/traffic/map/4/tile/flow/relative0/12/123/456.png?key=secret-key');
    }

    public function test_it_does_not_serve_tiles_without_a_server_key(): void
    {
        config(['services.tomtom.key' => null]);

        $this->get('/api/v1/traffic-flow/12/123/456.png')->assertServiceUnavailable();
    }
}
