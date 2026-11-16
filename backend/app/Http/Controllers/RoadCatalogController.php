<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;

class RoadCatalogController extends Controller
{
    private const AREA = '-16.51,-71.66,-16.29,-71.44';

    public function __invoke(): JsonResponse
    {
        $osm = Cache::get('road-catalog:arequipa:osm')
            ?? Cache::lock('road-catalog:arequipa:osm-refresh', 20)->get(fn () => $this->refreshOsm());

        $key = config('services.tomtom.key');
        $trafficConfigured = is_string($key) && trim($key) !== '';
        return response()->json([
            'zones' => $osm['zones'] ?? [],
            'speedSections' => $osm['speedSections'] ?? [],
            'osmAvailable' => !empty($osm),
            'trafficConfigured' => $trafficConfigured,
        ]);
    }

    private function refreshOsm(): array
    {
        $cached = Cache::get('road-catalog:arequipa:osm');
        if ($cached !== null) {
            return $cached;
        }

        $area = self::AREA;
        $query = <<<OVERPASS
        [out:json][timeout:10];
        way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"]["maxspeed"]($area);
        out geom;
        nwr["amenity"="school"]($area);
        out center;
        OVERPASS;

        $elements = null;
        $endpoints = array_unique(array_filter([
            config('services.overpass.url'),
            ...config('services.overpass.fallback_urls', []),
        ]));
        foreach ($endpoints as $endpoint) {
            try {
                $response = Http::withUserAgent('AVIGEM-RoadCatalog/1.0')->acceptJson()->connectTimeout(3)->timeout(12)->asForm()
                    ->post($endpoint, ['data' => $query]);
                if ($response->successful() && !$response->json('remark')) {
                    $elements = $response->json('elements');
                    break;
                }
            } catch (\Illuminate\Http\Client\ConnectionException) {
                // Try the next configured endpoint; a later request retries all of them.
            }
        }

        $catalog = is_array($elements) ? $this->osmCatalog($elements) : [];
        // Si Overpass falla, se espera 5 minutos antes de reintentar: cada intento puede ocupar un proceso PHP ~25 s.
        Cache::put('road-catalog:arequipa:osm', $catalog, $catalog ? now()->addHours(12) : now()->addMinutes(5));

        return $catalog;
    }

    private function osmCatalog(array $elements): array
    {
        $zones = [];
        $sections = [];
        foreach ($elements as $element) {
            $tags = $element['tags'] ?? [];
            $type = $element['type'] ?? '';
            $id = $element['id'] ?? '';
            if (($tags['amenity'] ?? null) === 'school') {
                $center = $element['center'] ?? $element;
                if (isset($center['lat'], $center['lon'])) {
                    $zones[] = [
                        'id' => "osm:$type:$id",
                        'name' => 'Colegio cerca',
                        'point' => ['lat' => $center['lat'], 'lon' => $center['lon']],
                        'radiusM' => 65,
                    ];
                }

                continue;
            }
            $speed = $tags['maxspeed'] ?? '';
            if ($type !== 'way' || !preg_match('/^([0-9]{1,3})(?: km\/h)?$/D', $speed, $match)
                || array_intersect(['maxspeed:conditional', 'maxspeed:forward', 'maxspeed:backward',
                    'maxspeed:forward:conditional', 'maxspeed:backward:conditional'], array_keys($tags))) {
                continue;
            }
            $points = $element['geometry'] ?? [];
            foreach ($points as $index => $start) {
                $end = $points[$index + 1] ?? null;
                if (!isset($start['lat'], $start['lon'], $end['lat'], $end['lon'])) {
                    continue;
                }
                $sections[] = [
                    'id' => "osm:$id:$index",
                    'name' => $tags['name'] ?? 'Tramo vial',
                    'start' => $start,
                    'end' => $end,
                    'kmh' => (int) $match[1],
                ];
            }
        }

        return ['zones' => $zones, 'speedSections' => $sections];
    }

}
