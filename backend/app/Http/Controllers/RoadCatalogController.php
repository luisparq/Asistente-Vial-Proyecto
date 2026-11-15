<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;

class RoadCatalogController extends Controller
{
    private const AREA = '-16.51,-71.66,-16.29,-71.44';
    private const TRAFFIC_AREA = '-71.66,-16.51,-71.44,-16.29';

    public function __invoke(): JsonResponse
    {
        $osm = Cache::get('road-catalog:arequipa:osm')
            ?? Cache::lock('road-catalog:arequipa:osm-refresh', 20)->get(fn () => $this->refreshOsm());

        $key = config('services.tomtom.key');
        $trafficConfigured = is_string($key) && trim($key) !== '';
        $traffic = $trafficConfigured
            ? Cache::get('road-catalog:arequipa:traffic')
                ?? Cache::lock('road-catalog:arequipa:traffic-refresh', 10)->get(fn () => $this->refreshTraffic($key))
            : null;

        return response()->json([
            'zones' => $osm['zones'] ?? [],
            'speedSections' => $osm['speedSections'] ?? [],
            'traffic' => is_array($traffic) ? $traffic : [],
            'osmAvailable' => !empty($osm),
            'trafficAvailable' => is_array($traffic),
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
        try {
            $response = Http::withUserAgent('AVIGEM-RoadCatalog/1.0')->acceptJson()->connectTimeout(3)->timeout(12)->asForm()
                ->post(config('services.overpass.url'), ['data' => $query]);
            if ($response->successful() && !$response->json('remark')) {
                $elements = $response->json('elements');
            }
        } catch (\Illuminate\Http\Client\ConnectionException) {
            // Retry on a later request.
        }

        $catalog = is_array($elements) ? $this->osmCatalog($elements) : [];
        Cache::put('road-catalog:arequipa:osm', $catalog, $catalog ? now()->addHours(12) : now()->addMinutes(2));

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
                    'start' => $start,
                    'end' => $end,
                    'kmh' => (int) $match[1],
                ];
            }
        }

        return ['zones' => $zones, 'speedSections' => $sections];
    }

    private function refreshTraffic(string $key): array|false
    {
        $cached = Cache::get('road-catalog:arequipa:traffic');
        if ($cached !== null) {
            return $cached;
        }

        $reports = false;
        try {
            $response = Http::acceptJson()->connectTimeout(3)->timeout(6)
                ->get('https://api.tomtom.com/traffic/services/5/incidentDetails', [
                    'key' => $key,
                    'bbox' => self::TRAFFIC_AREA,
                    'categoryFilter' => 'Jam',
                    'timeValidityFilter' => 'present',
                    'fields' => '{incidents{geometry{type,coordinates},properties{id,endTime}}}',
                ]);
            if ($response->successful() && is_array($incidents = $response->json('incidents'))) {
                $reports = $this->trafficCatalog($incidents);
            }
        } catch (\Illuminate\Http\Client\ConnectionException) {
            // Expired traffic is never reused after a failed refresh.
        }

        Cache::put('road-catalog:arequipa:traffic', $reports, now()->addMinutes(2));

        return $reports;
    }

    private function trafficCatalog(array $incidents): array
    {
        $reports = [];
        $now = now()->timestamp;
        foreach ($incidents as $incident) {
            $geometry = $incident['geometry'] ?? [];
            $id = $incident['properties']['id'] ?? null;
            $start = $geometry['coordinates'][0] ?? [];
            $next = $geometry['coordinates'][1] ?? [];
            if (!$id || ($geometry['type'] ?? null) !== 'LineString'
                || !isset($start[0], $start[1], $next[0], $next[1]) || $start === $next) {
                continue;
            }
            $endTime = strtotime($incident['properties']['endTime'] ?? '');
            $expiresAt = min($endTime ?: PHP_INT_MAX, $now + 180);
            if ($expiresAt <= $now) {
                continue;
            }

            $east = ($next[0] - $start[0]) * cos(deg2rad($start[1]));
            $north = $next[1] - $start[1];
            $direction = ((int) round(rad2deg(atan2($east, $north))) + 360) % 360;
            $reports[] = [
                'id' => "tomtom:$id",
                'point' => ['lat' => $start[1], 'lon' => $start[0]],
                'directionDegrees' => $direction,
                'expiresAt' => $expiresAt,
            ];
        }

        return $reports;
    }
}
