<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class RoadCatalogTest extends TestCase
{
    public function test_it_exposes_mapped_school_and_speed_and_keeps_traffic_unavailable_without_key(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => null]);
        Http::preventStrayRequests();
        Http::fake([
            config('services.overpass.url') => Http::response(['elements' => [
                ['id' => 1, 'type' => 'node', 'lat' => -16.41, 'lon' => -71.53, 'tags' => ['amenity' => 'school']],
                ['id' => 2, 'type' => 'way', 'tags' => ['maxspeed' => '40'], 'geometry' => [
                    ['lat' => -16.41, 'lon' => -71.53], ['lat' => -16.411, 'lon' => -71.53],
                ]],
                ['id' => 3, 'type' => 'way', 'tags' => ['maxspeed' => '40', 'maxspeed:forward' => '30'], 'geometry' => [
                    ['lat' => -16.41, 'lon' => -71.53], ['lat' => -16.411, 'lon' => -71.53],
                ]],
            ]]),
        ]);

        $this->getJson('/api/v1/road-catalog')->assertOk()
            ->assertJsonPath('zones.0.name', 'Colegio cerca')
            ->assertJsonPath('speedSections.0.kmh', 40)
            ->assertJsonCount(1, 'speedSections')
            ->assertJsonPath('osmAvailable', true)
            ->assertJsonPath('trafficAvailable', false)->assertJsonPath('trafficConfigured', false);
        $this->getJson('/api/v1/road-catalog')->assertOk();
        Http::assertSentCount(1);
        Http::assertSent(fn ($request) => $request->hasHeader('User-Agent', 'AVIGEM-RoadCatalog/1.0'));
    }

    public function test_it_exposes_current_directional_traffic_and_skips_expired_reports(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        $this->freezeTime();
        Http::preventStrayRequests();
        $coordinates = ['type' => 'LineString', 'coordinates' => [[-71.53, -16.411], [-71.53, -16.41]]];
        Http::fake([
            config('services.overpass.url') => Http::response([], 503),
            'https://api.tomtom.com/traffic/services/5/incidentDetails*' => Http::response(['incidents' => [
                ['geometry' => $coordinates, 'properties' => ['id' => 'active']],
                ['geometry' => $coordinates, 'properties' => ['id' => 'expired', 'endTime' => now()->subMinute()->toIso8601String()]],
            ]]),
        ]);

        $this->getJson('/api/v1/road-catalog')->assertOk()
            ->assertJsonPath('osmAvailable', false)
            ->assertJsonPath('trafficAvailable', true)
            ->assertJsonPath('trafficConfigured', true)
            ->assertJsonCount(1, 'traffic')
            ->assertJsonPath('traffic.0.id', 'tomtom:active')
            ->assertJsonPath('traffic.0.directionDegrees', 0)
            ->assertJsonPath('traffic.0.expiresAt', now()->timestamp + 180);
    }
}
