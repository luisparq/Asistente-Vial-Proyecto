<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class RoadCatalogTest extends TestCase
{
    public function test_it_exposes_mapped_school_and_speed_and_keeps_traffic_unconfigured_without_key(): void
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
            ->assertJsonPath('speedSections.0.name', 'Tramo vial')
            ->assertJsonCount(1, 'speedSections')
            ->assertJsonPath('osmAvailable', true)->assertJsonPath('trafficConfigured', false);
        $this->getJson('/api/v1/road-catalog')->assertOk();
        Http::assertSentCount(1);
        Http::assertSent(fn ($request) => $request->hasHeader('User-Agent', 'AVIGEM-RoadCatalog/1.0'));
    }

    public function test_it_reports_when_server_side_traffic_is_configured(): void
    {
        config([
            'cache.default' => 'array',
            'services.tomtom.key' => 'test-key',
            'services.overpass.fallback_urls' => [],
        ]);
        Http::preventStrayRequests();
        Http::fake([
            config('services.overpass.url') => Http::response([], 503),
        ]);

        $this->getJson('/api/v1/road-catalog')->assertOk()
            ->assertJsonPath('osmAvailable', false)
            ->assertJsonPath('trafficConfigured', true);
    }

    public function test_it_uses_a_configured_overpass_fallback_when_the_primary_is_unavailable(): void
    {
        config([
            'cache.default' => 'array',
            'services.overpass.url' => 'https://primary.example/interpreter',
            'services.overpass.fallback_urls' => ['https://fallback.example/interpreter'],
        ]);
        Http::preventStrayRequests();
        Http::fake([
            'https://primary.example/interpreter' => Http::response([], 503),
            'https://fallback.example/interpreter' => Http::response(['elements' => []]),
        ]);

        $this->getJson('/api/v1/road-catalog')->assertOk()->assertJsonPath('osmAvailable', true);
        Http::assertSentCount(2);
    }
}
