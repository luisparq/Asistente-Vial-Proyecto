<?php

namespace App\Http\Controllers;

use App\Http\Requests\TrackPointRequest;
use App\Services\TripAccess;
use App\Support\ApiProblem;
use App\Support\UtcTimestamp;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

class TripTrackController extends Controller
{
    public function put(TrackPointRequest $request, string $client_trip_id, string $client_point_id, TripAccess $access)
    {
        $trip = $access->writable($request->user(), $client_trip_id);
        $data = $request->validated();
        $capturedAt = CarbonImmutable::parse($data['captured_at']);
        if ($capturedAt->lt($trip->started_at) || ($trip->ended_at && $capturedAt->gt($trip->ended_at))) {
            throw new ApiProblem(422, 'point_outside_trip', 'La posición debe pertenecer al período del recorrido.');
        }

        $key = ['trip_id' => $trip->id, 'client_point_id' => $client_point_id];
        $values = [
            'captured_at' => $capturedAt->format('Y-m-d H:i:s.v'),
            'latitude' => (float) $data['latitude'],
            'longitude' => (float) $data['longitude'],
        ];
        $inserted = DB::table('trip_track_points')->insertOrIgnore($key + $values);
        $point = DB::table('trip_track_points')->where($key)->first();
        $receipt = [
            'captured_at' => UtcTimestamp::format(CarbonImmutable::parse($point->captured_at, 'UTC')),
            'latitude' => (float) $point->latitude,
            'longitude' => (float) $point->longitude,
        ];
        if ($receipt !== ['captured_at' => $data['captured_at'], 'latitude' => $values['latitude'], 'longitude' => $values['longitude']]) {
            throw new ApiProblem(409, 'point_conflict', 'El identificador ya existe con otra posición.');
        }

        return response()->json(['data' => $receipt], $inserted ? 201 : 200);
    }
}
