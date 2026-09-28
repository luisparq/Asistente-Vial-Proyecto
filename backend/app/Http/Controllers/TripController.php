<?php

namespace App\Http\Controllers;

use App\Http\Requests\ListRequest;
use App\Http\Requests\TripWriteRequest;
use App\Services\TripAccess;
use App\Services\TripSync;
use App\Support\ApiData;
use App\Support\ScopedCursor;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;

class TripController extends Controller
{
    public function put(TripWriteRequest $request, string $client_trip_id, TripSync $sync)
    {
        [$trip, $disposition] = $sync->trip($request->user(), $client_trip_id, $request->validated());

        return response()->json(['data' => ApiData::trip($trip), 'sync' => ['disposition' => $disposition,
            'acknowledged_revision' => $request->validated('client_revision'), 'current_revision' => $trip->client_revision]], $disposition === 'created' ? 201 : 200);
    }

    public function show(Request $request, string $client_trip_id, TripAccess $access)
    {
        return response()->json(['data' => ApiData::trip($access->readable($request->user(), $client_trip_id), true)]);
    }

    public function index(ListRequest $request, TripAccess $access, ScopedCursor $cursor)
    {
        $query = $access->visible($request->user());
        if ($request->has('status')) {
            $query->where('status', $request->query('status'));
        }
        foreach (['started_from' => '>=', 'started_before' => '<'] as $key => $operator) {
            if ($request->has($key)) {
                $query->where('started_at', $operator, CarbonImmutable::parse($request->query($key))->format('Y-m-d H:i:s.v'));
            }
        }
        $query->orderByDesc('started_at')->orderBy('client_trip_id');

        return response()->json($cursor->page($request, $query, fn ($trip) => ApiData::trip($trip, true)));
    }
}
