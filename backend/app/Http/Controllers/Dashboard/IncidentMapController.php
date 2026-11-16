<?php

namespace App\Http\Controllers\Dashboard;

use App\Http\Controllers\Controller;
use App\Http\Requests\IncidentMapRequest;
use App\Models\Vehicle;
use App\Services\DashboardQuery;
use App\Support\DashboardDisplay;
use Illuminate\Http\Request;

class IncidentMapController extends Controller
{
    public function __invoke(Request $request)
    {
        return redirect()->route('dashboard.events', ['tab' => 'map'] + $request->query());
    }

    public function show(IncidentMapRequest $request, DashboardQuery $query)
    {
        $user = $request->user('web');
        $filters = $request->validated();
        if (($filters['kind'] ?? null) === 'alerts') {
            return $this->alerts($request, $query, $filters);
        }

        $matchingEvents = $query->events($user, $filters);
        if (!empty($filters['vehicle_id'])) {
            $matchingEvents->whereHas('trip', fn ($trip) => $trip->where('company_id', $user->company_id)
                ->where('vehicle_id', $filters['vehicle_id'])
                ->whereHas('vehicle', fn ($vehicle) => $vehicle->where('company_id', $user->company_id)));
        }
        $companyScope = fn ($relation) => $relation->where('company_id', $user->company_id);
        $events = (clone $matchingEvents)
            ->with(['trip' => fn ($trip) => $companyScope($trip)->with(['driver' => $companyScope, 'vehicle' => $companyScope])])
            ->orderByDesc('occurred_at')->orderByDesc('id')->paginate(20)->withQueryString();
        $tripReturn = ['from_view' => 'events', 'source_page' => $filters['page'] ?? null]
            + array_intersect_key($filters, array_flip(['from', 'to', 'type', 'origin', 'vehicle_id']));

        $locatedEvents = $matchingEvents->whereNotNull('latitude')->whereNotNull('longitude')
            ->whereBetween('latitude', [-90,90])->whereBetween('longitude', [-180,180])
            ->whereHas('trip', fn ($q) => $q->where('company_id',$user->company_id));
        $features = $locatedEvents->with(['trip' => fn ($trip) => $companyScope($trip)->with(['vehicle' => $companyScope])])
            ->orderBy('occurred_at')->get()->map(function ($event) use ($tripReturn) {
                return ['type'=>'Feature', 'id'=>$event->client_event_id,
                    'geometry'=>['type'=>'Point','coordinates'=>[$event->longitude,$event->latitude]],
                    'properties'=>['uuid'=>$event->client_event_id,'label'=>DashboardDisplay::type($event->type),
                        'origin_label'=>'Dato de prueba',
                        'date'=>DashboardDisplay::date($event->occurred_at),'location_origin'=>$event->location_origin,
                        'vehicle'=>$event->trip->vehicle?->plate,
                        'trip_url'=>route('dashboard.trip', ['uuid' => $event->trip->client_trip_id] + $tripReturn)]];
            })->values();
        $vehicles = Vehicle::where('company_id',$user->company_id)->whereHas('trips', fn ($q) => $q->where('company_id',$user->company_id)
            ->whereHas('events', fn ($e) => $e->where('company_id',$user->company_id)->whereBetween('latitude',[-90,90])->whereBetween('longitude',[-180,180])))
            ->orderBy('plate')->get(['id','plate']);
        return view('dashboard.events', [
            'events' => $events,
            'tripReturn' => $tripReturn,
            'geojson' => ['type' => 'FeatureCollection', 'features' => $features],
            'filters' => $filters,
            'vehicles' => $vehicles,
            'mapStyle' => config('maps.style_url'),
        ]);
    }

    private function alerts(IncidentMapRequest $request, DashboardQuery $query, array $filters)
    {
        $user = $request->user('web');
        $matching = $query->alerts($user, $filters);
        $alerts = (clone $matching)->orderByDesc('occurred_at')->orderByDesc('id')->paginate(20)->withQueryString();
        $focused = isset($filters['focus_alert']) ? $query->alert($user, $filters['focus_alert']) : null;
        $located = $focused ? collect([$focused])->filter(fn ($alert) => $alert->location_status === 'AVAILABLE')
            : $matching->where('location_status', 'AVAILABLE')->whereNotNull('latitude')->whereNotNull('longitude')->get();

        $features = $located->map(fn ($alert) => [
            'type' => 'Feature', 'id' => $alert->client_alert_id,
            'geometry' => ['type' => 'Point', 'coordinates' => [$alert->longitude, $alert->latitude]],
            'properties' => [
                'uuid' => $alert->client_alert_id, 'label' => 'Maniobras repetidas',
                'origin_label' => $alert->test_only ? 'Alerta de prueba' : 'Alerta operativa',
                'origin' => $alert->test_only ? 'SIMULATED' : 'SENSOR',
                'date' => DashboardDisplay::date($alert->occurred_at),
                'vehicle' => $alert->vehicle?->plate,
                'alert_url' => route('dashboard.alerts.show', $alert->client_alert_id),
            ],
        ])->values();

        return view('dashboard.events', [
            'alerts' => $alerts, 'focusedAlert' => $focused,
            'geojson' => ['type' => 'FeatureCollection', 'features' => $features],
            'filters' => $filters,
            'vehicles' => Vehicle::where('company_id', $user->company_id)->orderBy('plate')->get(['id', 'plate']),
            'mapStyle' => config('maps.style_url'),
        ]);
    }
}
