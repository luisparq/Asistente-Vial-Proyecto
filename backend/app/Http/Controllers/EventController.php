<?php

namespace App\Http\Controllers;

use App\Http\Requests\EventWriteRequest;
use App\Http\Requests\ListRequest;
use App\Services\TripAccess;
use App\Services\TripSync;
use App\Support\ApiData;
use App\Support\ScopedCursor;
use Carbon\CarbonImmutable;

class EventController extends Controller
{
    public function put(EventWriteRequest $request, string $client_trip_id, string $client_event_id, TripSync $sync)
    {
        [$event, $disposition] = $sync->event($request->user(), $client_trip_id, $client_event_id, $request->validated());

        return response()->json(['data' => ApiData::event($event), 'sync' => ['disposition' => $disposition]], $disposition === 'created' ? 201 : 200);
    }

    public function index(ListRequest $request, string $client_trip_id, TripAccess $access, ScopedCursor $cursor)
    {
        $trip = $access->readable($request->user(), $client_trip_id);
        $query = $trip->events()->getQuery()->where('company_id', $request->user()->company_id)->with('trip');
        foreach (['type', 'origin'] as $key) {
            if ($request->has($key)) {
                $query->where($key, $request->query($key));
            }
        }
        foreach (['occurred_from' => '>=', 'occurred_before' => '<'] as $key => $operator) {
            if ($request->has($key)) {
                $query->where('occurred_at', $operator, CarbonImmutable::parse($request->query($key))->format('Y-m-d H:i:s.v'));
            }
        }
        $query->orderBy('occurred_at')->orderBy('client_event_id');

        return response()->json($cursor->page($request, $query, fn ($event) => ApiData::event($event)));
    }
}
