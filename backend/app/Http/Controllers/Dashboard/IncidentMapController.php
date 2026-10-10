<?php

namespace App\Http\Controllers\Dashboard;

use App\Http\Controllers\Controller;
use App\Http\Requests\IncidentMapRequest;
use App\Models\Vehicle;
use App\Services\DashboardQuery;
use App\Support\DashboardDisplay;

class IncidentMapController extends Controller
{
    public function __invoke(IncidentMapRequest $request, DashboardQuery $query)
    {
        $user = $request->user('web'); $filters = $request->validated();
        $events = $query->events($user, $filters)->whereNotNull('latitude')->whereNotNull('longitude')
            ->whereBetween('latitude', [-90,90])->whereBetween('longitude', [-180,180])
            ->whereHas('trip', fn ($q) => $q->where('company_id',$user->company_id));
        if (!empty($filters['vehicle_id'])) {
            $events->whereHas('trip', fn ($q) => $q->where('company_id',$user->company_id)->where('vehicle_id',$filters['vehicle_id'])
                ->whereHas('vehicle', fn ($v) => $v->where('company_id',$user->company_id)));
        }
        $features = $events->with(['trip.vehicle' => fn ($q) => $q->where('company_id',$user->company_id)])
            ->orderBy('occurred_at')->get()->map(function ($event) {
                return ['type'=>'Feature', 'id'=>$event->client_event_id,
                    'geometry'=>['type'=>'Point','coordinates'=>[$event->longitude,$event->latitude]],
                    'properties'=>['uuid'=>$event->client_event_id,'type'=>$event->type,'label'=>DashboardDisplay::type($event->type),
                        'origin'=>$event->origin,'origin_label'=>DashboardDisplay::origin($event->origin),
                        'date'=>DashboardDisplay::date($event->occurred_at),'location_origin'=>$event->location_origin,
                        'vehicle'=>$event->trip->vehicle?->plate,'trip_url'=>route('dashboard.trip',$event->trip->client_trip_id)]];
            })->values();
        $vehicles = Vehicle::where('company_id',$user->company_id)->whereHas('trips', fn ($q) => $q->where('company_id',$user->company_id)
            ->whereHas('events', fn ($e) => $e->where('company_id',$user->company_id)->whereBetween('latitude',[-90,90])->whereBetween('longitude',[-180,180])))
            ->orderBy('plate')->get(['id','plate']);
        return view('dashboard.map', ['geojson'=>['type'=>'FeatureCollection','features'=>$features], 'filters'=>$filters,
            'vehicles'=>$vehicles, 'mapStyle'=>config('maps.style_url')]);
    }
}
