<?php

namespace App\Services;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class TomTomService
{
    private ?string $key;

    public function __construct()
    {
        $this->key = config('services.tomtom.key');
    }

    public function isConfigured(): bool
    {
        return is_string($this->key) && trim($this->key) !== '';
    }

    /**
     * Convierte coordenadas (lat, lon) a una dirección legible.
     */
    public function reverseGeocode(float $lat, float $lon): ?array
    {
        if (!$this->isConfigured()) {
            return null;
        }

        $roundedLat = round($lat, 4);
        $roundedLon = round($lon, 4);
        $cacheKey = "tomtom:geocode:$roundedLat:$roundedLon";

        return Cache::remember($cacheKey, now()->addDays(7), function () use ($lat, $lon) {
            try {
                $response = Http::acceptJson()->connectTimeout(3)->timeout(6)->get(
                    "https://api.tomtom.com/search/2/reverseGeocode/{$lat},{$lon}.json",
                    ['key' => $this->key]
                );

                if (! $response->successful()) {
                    Log::warning('TomTom HTTP failure', ['operation' => 'reverseGeocode', 'status' => $response->status()]);
                }
                if ($response->successful()) {
                    $address = $response->json('addresses.0.address');
                    if ($address) {
                        return [
                            'formatted' => $address['freeformAddress'] ?? '',
                            'street' => $address['streetName'] ?? '',
                            'district' => $address['municipalitySubdivision'] ?? '',
                            'city' => $address['municipality'] ?? 'Arequipa',
                            'country' => $address['country'] ?? 'Perú',
                        ];
                    }
                }
            } catch (\Exception $error) {
                Log::warning('TomTom request failure', ['exception' => $error::class]);
            }
            return null;
        });
    }

    /**
     * Calcula una ruta óptima considerando tráfico en tiempo real con ETA y demoras.
     * $origin y $destination en formato "lat,lon"
     */
    public function calculateRoute(string $origin, string $destination): ?array
    {
        if (!$this->isConfigured()) {
            return null;
        }

        $roundedOrigin = implode(',', array_map(fn ($value) => number_format((float) $value, 4, '.', ''), explode(',', $origin)));
        $roundedDestination = implode(',', array_map(fn ($value) => number_format((float) $value, 4, '.', ''), explode(',', $destination)));
        $cacheKey = "tomtom:route:$roundedOrigin:$roundedDestination";

        return Cache::remember($cacheKey, now()->addMinutes(2), function () use ($origin, $destination) {
            try {
                $response = Http::acceptJson()->connectTimeout(3)->timeout(8)->get(
                    "https://api.tomtom.com/routing/1/calculateRoute/{$origin}:{$destination}/json",
                    [
                        'key' => $this->key,
                        'traffic' => 'true',
                        'travelMode' => 'car',
                        'computeTravelTimeFor' => 'all',
                    ]
                );

                if (! $response->successful()) {
                    Log::warning('TomTom HTTP failure', ['operation' => 'calculateRoute', 'status' => $response->status()]);
                }
                if ($response->successful()) {
                    $route = $response->json('routes.0');
                    if ($route) {
                        $summary = $route['summary'] ?? [];
                        $points = $route['legs'][0]['points'] ?? [];

                        return [
                            'distanceMeters' => $summary['lengthInMeters'] ?? 0,
                            'durationSeconds' => $summary['travelTimeInSeconds'] ?? 0,
                            'trafficDelaySeconds' => $summary['trafficDelayInSeconds'] ?? 0,
                            'eta' => $summary['arrivalTime'] ?? null,
                            'coordinates' => array_map(fn ($p) => ['lat' => $p['latitude'], 'lon' => $p['longitude']], $points),
                        ];
                    }
                }
            } catch (\Exception $error) {
                Log::warning('TomTom request failure', ['exception' => $error::class]);
            }
            return null;
        });
    }

    /**
     * Obtiene el flujo de tráfico en tiempo real para un tramo o punto específico.
     */
    public function flowSegment(float $lat, float $lon): ?array
    {
        if (!$this->isConfigured()) {
            return null;
        }

        $roundedLat = round($lat, 4);
        $roundedLon = round($lon, 4);
        $cacheKey = "tomtom:flow_seg:$roundedLat:$roundedLon";

        return Cache::remember($cacheKey, now()->addMinutes(1), function () use ($lat, $lon) {
            try {
                $response = Http::acceptJson()->connectTimeout(3)->timeout(6)->get(
                    "https://api.tomtom.com/traffic/services/4/flowSegmentData/relative0/10/json",
                    [
                        'key' => $this->key,
                        'point' => "{$lat},{$lon}",
                        'unit' => 'KMPH',
                    ]
                );

                if (! $response->successful()) {
                    Log::warning('TomTom HTTP failure', ['operation' => 'flowSegment', 'status' => $response->status()]);
                }
                if ($response->successful()) {
                    $flow = $response->json('flowSegmentData');
                    if ($flow) {
                        return [
                            'currentSpeedKmh' => $flow['currentSpeed'] ?? null,
                            'freeFlowSpeedKmh' => $flow['freeFlowSpeed'] ?? null,
                            'currentTravelTimeSec' => $flow['currentTravelTime'] ?? null,
                            'freeFlowTravelTimeSec' => $flow['freeFlowTravelTime'] ?? null,
                            'confidence' => $flow['confidence'] ?? 0,
                            'roadClosure' => $flow['roadClosure'] ?? false,
                        ];
                    }
                }
            } catch (\Exception $error) {
                Log::warning('TomTom request failure', ['exception' => $error::class]);
            }
            return null;
        });
    }

    /**
     * Busca puntos de auxilio / emergencia cercanos (hospitales, comisarías, grifos, talleres).
     */
    /**
     * Busca puntos de auxilio cercanos (hospitales, comisarías, grifos, talleres).
     * Devuelve null cuando ninguna consulta respondió, para distinguir el fallo de «sin resultados».
     */
    public function emergencyPois(float $lat, float $lon, string $type = 'all'): ?array
    {
        if (!$this->isConfigured()) {
            return null;
        }

        $categories = match ($type) {
            'hospital' => ['7321'], // Hospital / Policlínico
            'police' => ['7322'],   // Comisaría / Policía
            'fuel' => ['7311'],     // Estación de servicio / Grifo
            'repair' => ['7310'],   // Taller mecánico (7314 corresponde a Hotel/Motel en TomTom)
            default => ['7321', '7322', '7311', '7310'],
        };

        $cacheKey = 'tomtom:pois:v2:' . round($lat, 3) . ':' . round($lon, 3) . ":$type";
        $cached = Cache::get($cacheKey);
        if (is_array($cached)) {
            return $cached;
        }

        $pois = $this->searchCategories($lat, $lon, $categories, 5000, 5, 'Punto de auxilio');
        if ($pois !== null) {
            usort($pois, fn ($a, $b) => $a['distanceMeters'] <=> $b['distanceMeters']);
            $pois = array_slice($pois, 0, 15);
            Cache::put($cacheKey, $pois, now()->addHours(1));
        }

        return $pois;
    }

    /**
     * Lugares que TomTom clasifica como establecimientos educativos (categoría 7372) cerca de un punto.
     */
    public function nearbySchools(float $lat, float $lon): ?array
    {
        if (!$this->isConfigured()) {
            return null;
        }

        $cacheKey = 'tomtom:schools:' . round($lat, 2) . ':' . round($lon, 2);
        $cached = Cache::get($cacheKey);
        if (is_array($cached)) {
            return $cached;
        }

        $schools = $this->searchCategories($lat, $lon, ['7372'], 3000, 100, 'Establecimiento educativo');
        if ($schools !== null) {
            Cache::put($cacheKey, $schools, now()->addHours(12));
        }

        return $schools;
    }

    /**
     * Incidentes vigentes (congestión, cierres, obras…) en un recuadro "minLon,minLat,maxLon,maxLat".
     */
    public function trafficIncidents(string $bbox): ?array
    {
        if (!$this->isConfigured()) {
            return null;
        }

        $cacheKey = "tomtom:incidents:$bbox";
        $cached = Cache::get($cacheKey);
        if (is_array($cached)) {
            return $cached;
        }

        try {
            $response = Http::acceptJson()->connectTimeout(3)->timeout(8)->get(
                'https://api.tomtom.com/traffic/services/5/incidentDetails',
                [
                    'key' => $this->key,
                    'bbox' => $bbox,
                    'language' => 'es-ES',
                    'timeValidityFilter' => 'present',
                    'fields' => '{incidents{type,geometry{type,coordinates},properties{id,iconCategory,events{description},from,to,endTime}}}',
                ]
            );
        } catch (\Illuminate\Http\Client\ConnectionException $error) {
            Log::warning('TomTom connection failure', ['exception' => $error::class]);
            return null;
        }

        if (! $response->successful()) {
            Log::warning('TomTom HTTP failure', ['operation' => 'trafficIncidents', 'status' => $response->status()]);
        }
        $incidents = $response->successful() ? $response->json('incidents') : null;
        if (!is_array($incidents)) {
            return null;
        }

        $items = [];
        foreach ($incidents as $incident) {
            $geometry = $incident['geometry'] ?? [];
            $properties = $incident['properties'] ?? [];
            if (!in_array($geometry['type'] ?? null, ['LineString', 'Point'], true) || empty($geometry['coordinates'])) {
                continue;
            }
            $category = (int) ($properties['iconCategory'] ?? 0);
            $items[] = [
                'id' => (string) ($properties['id'] ?? ''),
                'kind' => in_array($category, [7, 8], true) ? 'closure' : 'incident',
                'category' => self::INCIDENT_CATEGORIES[$category] ?? 'Incidente',
                'description' => $properties['events'][0]['description'] ?? null,
                'from' => $properties['from'] ?? null,
                'to' => $properties['to'] ?? null,
                'endTime' => $properties['endTime'] ?? null,
                'geometry' => ['type' => $geometry['type'], 'coordinates' => $geometry['coordinates']],
            ];
        }

        Cache::put($cacheKey, $items, now()->addMinutes(1));

        return $items;
    }

    private const INCIDENT_CATEGORIES = [
        1 => 'Accidente', 2 => 'Niebla', 3 => 'Condición peligrosa', 4 => 'Lluvia', 5 => 'Hielo',
        6 => 'Congestión', 7 => 'Carril cerrado', 8 => 'Vía cerrada', 9 => 'Obras en la vía',
        10 => 'Viento', 11 => 'Inundación', 14 => 'Vehículo averiado',
    ];

    /**
     * categorySearch por cada categoría. Devuelve null solo si ninguna consulta respondió.
     */
    private function searchCategories(float $lat, float $lon, array $categories, int $radius, int $limit, string $fallbackName): ?array
    {
        $results = [];
        $answered = false;
        foreach ($categories as $cat) {
            try {
                $response = Http::acceptJson()->connectTimeout(3)->timeout(6)->get(
                    'https://api.tomtom.com/search/2/categorySearch/.json',
                    [
                        'key' => $this->key,
                        'categorySet' => $cat,
                        'lat' => $lat,
                        'lon' => $lon,
                        'radius' => $radius,
                        'limit' => $limit,
                        'language' => 'es-419',
                    ]
                );
            } catch (\Illuminate\Http\Client\ConnectionException $error) {
                Log::warning('TomTom connection failure', ['exception' => $error::class]);
                continue;
            }
            if (!$response->successful()) {
                Log::warning('TomTom HTTP failure', ['operation' => 'searchCategories', 'status' => $response->status()]);
                continue;
            }
            $answered = true;
            foreach ($response->json('results') ?? [] as $item) {
                $pos = $item['position'] ?? [];
                if (!isset($pos['lat'], $pos['lon'])) {
                    continue;
                }
                $poi = $item['poi'] ?? [];
                $results[] = [
                    'id' => $item['id'] ?? '',
                    'name' => $poi['name'] ?? $fallbackName,
                    'category' => $poi['categories'][0] ?? $cat,
                    'phone' => $poi['phone'] ?? null,
                    'distanceMeters' => (int) round($item['dist'] ?? 0),
                    'address' => $item['address']['freeformAddress'] ?? '',
                    'lat' => $pos['lat'],
                    'lon' => $pos['lon'],
                ];
            }
        }

        return $answered ? $results : null;
    }
}
