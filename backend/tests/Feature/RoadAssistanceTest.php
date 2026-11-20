<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class RoadAssistanceTest extends TestCase
{
    use \Illuminate\Foundation\Testing\RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $user = \App\Models\User::factory()->create(['role' => 'fleet_admin']);
        $this->withToken($user->createToken('TEST ONLY road access', $user->capabilities(), now()->addHour())->plainTextToken);
    }

    public function test_reverse_geocode_returns_address(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        Http::preventStrayRequests();
        Http::fake([
            'https://api.tomtom.com/search/2/reverseGeocode/*' => Http::response([
                'addresses' => [
                    [
                        'address' => [
                            'freeformAddress' => 'Avenida Ejército 101, Yanahuara, Arequipa',
                            'streetName' => 'Avenida Ejército',
                            'municipalitySubdivision' => 'Yanahuara',
                            'municipality' => 'Arequipa',
                            'country' => 'Perú',
                        ],
                    ],
                ],
            ]),
        ]);

        $this->getJson('/api/v1/road/reverse-geocode?lat=-16.398&lon=-71.536')
            ->assertOk()
            ->assertJsonPath('success', true)
            ->assertJsonPath('address.street', 'Avenida Ejército')
            ->assertJsonPath('address.district', 'Yanahuara');
    }

    public function test_directional_reverse_geocode_returns_posted_limit_and_position(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        Http::preventStrayRequests();
        Http::fake([
            'https://api.tomtom.com/search/2/reverseGeocode/*' => Http::response([
                'addresses' => [[
                    'address' => ['streetName' => 'Avenida Ejército', 'speedLimit' => '40.00KPH'],
                    'position' => '-16.398,-71.536',
                ]],
            ]),
        ]);

        $this->getJson('/api/v1/road/reverse-geocode?lat=-16.398&lon=-71.536&heading=90')
            ->assertOk()->assertJsonPath('address.speedLimitKmh', 40)
            ->assertJsonPath('address.roadPosition', '-16.398,-71.536');
        Http::assertSent(fn ($request) => $request['returnSpeedLimit'] === 'true'
            && (int) $request['heading'] === 90);
    }

    public function test_route_calculation_returns_eta_and_distance(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        Http::preventStrayRequests();
        Http::fake([
            'https://api.tomtom.com/routing/1/calculateRoute/*' => Http::response([
                'routes' => [
                    [
                        'summary' => [
                            'lengthInMeters' => 4500,
                            'travelTimeInSeconds' => 600,
                            'trafficDelayInSeconds' => 120,
                            'arrivalTime' => '2026-11-15T15:30:00Z',
                        ],
                        'legs' => [
                            [
                                'points' => [
                                    ['latitude' => -16.398, 'longitude' => -71.536],
                                    ['latitude' => -16.410, 'longitude' => -71.540],
                                ],
                            ],
                        ],
                    ],
                ],
            ]),
        ]);

        $this->getJson('/api/v1/road/route?origin=-16.398,-71.536&destination=-16.410,-71.540')
            ->assertOk()
            ->assertJsonPath('success', true)
            ->assertJsonPath('route.distanceMeters', 4500)
            ->assertJsonPath('route.trafficDelaySeconds', 120)
            ->assertJsonCount(2, 'route.coordinates');
    }

    public function test_traffic_flow_segment_returns_speeds(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        Http::preventStrayRequests();
        Http::fake([
            'https://api.tomtom.com/traffic/services/4/flowSegmentData/*' => Http::response([
                'flowSegmentData' => [
                    'currentSpeed' => 28,
                    'freeFlowSpeed' => 45,
                    'currentTravelTime' => 180,
                    'freeFlowTravelTime' => 100,
                    'confidence' => 0.95,
                    'roadClosure' => false,
                    'coordinates' => ['coordinate' => [
                        ['latitude' => -16.398, 'longitude' => -71.536],
                        ['latitude' => -16.397, 'longitude' => -71.536],
                    ]],
                ],
            ]),
        ]);

        $this->getJson('/api/v1/road/traffic-flow-segment?lat=-16.398&lon=-71.536')
            ->assertOk()
            ->assertJsonPath('success', true)
            ->assertJsonPath('flow.currentSpeedKmh', 28)
            ->assertJsonPath('flow.freeFlowSpeedKmh', 45)
            ->assertJsonCount(2, 'flow.coordinates');
    }

    public function test_emergency_pois_returns_places(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        Http::preventStrayRequests();
        Http::fake([
            'https://api.tomtom.com/search/2/categorySearch/*' => Http::response([
                'results' => [
                    [
                        'id' => 'poi-1',
                        'dist' => 350.2,
                        'poi' => ['name' => 'Hospital Regional Honorio Delgado', 'categories' => ['Hospital']],
                        'address' => ['freeformAddress' => 'Av. Los Incas s/n, Arequipa'],
                        'position' => ['lat' => -16.415, 'lon' => -71.528],
                    ],
                ],
            ]),
        ]);

        $this->getJson('/api/v1/road/emergency-pois?lat=-16.398&lon=-71.536&type=hospital')
            ->assertOk()
            ->assertJsonPath('success', true)
            ->assertJsonPath('pois.0.name', 'Hospital Regional Honorio Delgado')
            ->assertJsonPath('pois.0.distanceMeters', 350);
    }

    public function test_repair_pois_use_tomtom_repair_facility_category(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        Http::preventStrayRequests();
        Http::fake(['https://api.tomtom.com/search/2/categorySearch/*' => Http::response(['results' => []])]);

        $this->getJson('/api/v1/road/emergency-pois?lat=-16.398&lon=-71.536&type=repair')
            ->assertOk()->assertJsonPath('count', 0);
        Http::assertSent(fn ($request) => ($request->data()['categorySet'] ?? null) === '7310');
    }

    public function test_emergency_pois_report_source_failure_instead_of_empty_list(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        Http::preventStrayRequests();
        Http::fake(['https://api.tomtom.com/search/2/categorySearch/*' => Http::response('busy', 503)]);

        $this->getJson('/api/v1/road/emergency-pois?lat=-16.398&lon=-71.536&type=hospital')
            ->assertStatus(502)->assertJsonPath('success', false);
    }

    public function test_traffic_incidents_separate_closures_from_other_incidents(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        Http::preventStrayRequests();
        Http::fake([
            'https://api.tomtom.com/traffic/services/5/incidentDetails*' => Http::response(['incidents' => [
                ['geometry' => ['type' => 'LineString', 'coordinates' => [[-71.53, -16.39], [-71.54, -16.40]]],
                    'properties' => ['id' => 'a', 'iconCategory' => 8, 'events' => [['description' => 'Cerrado']], 'from' => 'Calle Espinar']],
                ['geometry' => ['type' => 'LineString', 'coordinates' => [[-71.55, -16.41], [-71.56, -16.42]]],
                    'properties' => ['id' => 'b', 'iconCategory' => 6, 'events' => [['description' => 'Tráfico lento']]]],
            ]]),
        ]);

        $this->getJson('/api/v1/road/traffic-incidents')
            ->assertOk()
            ->assertJsonPath('count', 2)
            ->assertJsonPath('incidents.0.kind', 'closure')
            ->assertJsonPath('incidents.0.category', 'Vía cerrada')
            ->assertJsonPath('incidents.1.kind', 'incident');
    }

    public function test_road_layers_report_unconfigured_tomtom(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => null]);
        Http::preventStrayRequests();

        $this->getJson('/api/v1/road/traffic-incidents')->assertStatus(503)->assertJsonPath('configured', false);
        $this->getJson('/api/v1/road/schools?lat=-16.398&lon=-71.536')->assertStatus(503)->assertJsonPath('configured', false);
    }

    public function test_schools_return_tomtom_educational_places(): void
    {
        config(['cache.default' => 'array', 'services.tomtom.key' => 'test-key']);
        Http::preventStrayRequests();
        Http::fake(['https://api.tomtom.com/search/2/categorySearch/*' => Http::response(['results' => [
            ['id' => 's1', 'dist' => 173, 'poi' => ['name' => 'Institución Educativa', 'categories' => ['school']],
                'position' => ['lat' => -16.399, 'lon' => -71.537]],
        ]])]);

        $this->getJson('/api/v1/road/schools?lat=-16.398&lon=-71.536')
            ->assertOk()->assertJsonPath('schools.0.name', 'Institución Educativa');
        Http::assertSent(fn ($request) => ($request->data()['categorySet'] ?? null) === '7372');
    }
}
