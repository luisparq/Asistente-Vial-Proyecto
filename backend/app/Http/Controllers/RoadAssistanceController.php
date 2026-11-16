<?php

namespace App\Http\Controllers;

use App\Services\TomTomService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class RoadAssistanceController extends Controller
{
    public function __construct(private readonly TomTomService $tomtom)
    {
    }

    /**
     * GET /api/v1/road/reverse-geocode?lat=...&lon=...
     */
    public function reverseGeocode(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'lat' => ['required', 'numeric', 'between:-90,90'],
            'lon' => ['required', 'numeric', 'between:-180,180'],
        ]);

        $data = $this->tomtom->reverseGeocode((float) $validated['lat'], (float) $validated['lon']);

        return response()->json([
            'success' => $data !== null,
            'address' => $data,
        ], $data ? 200 : 404);
    }

    /**
     * GET /api/v1/road/route?origin=lat,lon&destination=lat,lon
     */
    public function calculateRoute(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'origin' => ['required', 'string', 'regex:/^-?\d+(\.\d+)?,-?\d+(\.\d+)?$/'],
            'destination' => ['required', 'string', 'regex:/^-?\d+(\.\d+)?,-?\d+(\.\d+)?$/'],
        ]);

        $route = $this->tomtom->calculateRoute($validated['origin'], $validated['destination']);

        return response()->json([
            'success' => $route !== null,
            'route' => $route,
        ], $route ? 200 : 502);
    }

    /**
     * GET /api/v1/road/traffic-flow-segment?lat=...&lon=...
     */
    public function flowSegment(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'lat' => ['required', 'numeric', 'between:-90,90'],
            'lon' => ['required', 'numeric', 'between:-180,180'],
        ]);

        $flow = $this->tomtom->flowSegment((float) $validated['lat'], (float) $validated['lon']);

        return response()->json([
            'success' => $flow !== null,
            'flow' => $flow,
        ], $flow ? 200 : 502);
    }

    /**
     * GET /api/v1/road/emergency-pois?lat=...&lon=...&type=hospital|police|fuel|repair|all
     */
    public function emergencyPois(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'lat' => ['required', 'numeric', 'between:-90,90'],
            'lon' => ['required', 'numeric', 'between:-180,180'],
            'type' => ['nullable', 'string', 'in:hospital,police,fuel,repair,all'],
        ]);

        $pois = $this->tomtom->emergencyPois(
            (float) $validated['lat'],
            (float) $validated['lon'],
            $validated['type'] ?? 'all'
        );

        return response()->json([
            'success' => $pois !== null,
            'configured' => $this->tomtom->isConfigured(),
            'count' => count($pois ?? []),
            'pois' => $pois ?? [],
        ], $pois === null ? ($this->tomtom->isConfigured() ? 502 : 503) : 200);
    }

    /**
     * GET /api/v1/road/traffic-incidents — incidentes y cierres vigentes en Arequipa.
     */
    public function trafficIncidents(): JsonResponse
    {
        $incidents = $this->tomtom->trafficIncidents(self::AREQUIPA_BBOX);

        return response()->json([
            'success' => $incidents !== null,
            'configured' => $this->tomtom->isConfigured(),
            'count' => count($incidents ?? []),
            'incidents' => $incidents ?? [],
        ], $incidents === null ? ($this->tomtom->isConfigured() ? 502 : 503) : 200);
    }

    /**
     * GET /api/v1/road/schools?lat=...&lon=... — establecimientos educativos según TomTom.
     */
    public function schools(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'lat' => ['required', 'numeric', 'between:-90,90'],
            'lon' => ['required', 'numeric', 'between:-180,180'],
        ]);

        $schools = $this->tomtom->nearbySchools((float) $validated['lat'], (float) $validated['lon']);

        return response()->json([
            'success' => $schools !== null,
            'configured' => $this->tomtom->isConfigured(),
            'count' => count($schools ?? []),
            'schools' => $schools ?? [],
        ], $schools === null ? ($this->tomtom->isConfigured() ? 502 : 503) : 200);
    }

    private const AREQUIPA_BBOX = '-71.66,-16.51,-71.44,-16.29';
}
