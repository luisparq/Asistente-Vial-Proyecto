<?php

namespace App\Http\Controllers;

use App\Http\Requests\ManeuverAlertWriteRequest;
use App\Services\TripSync;
use App\Support\ApiData;

class ManeuverAlertController extends Controller
{
    public function put(ManeuverAlertWriteRequest $request, string $client_trip_id, string $client_alert_id, TripSync $sync)
    {
        [$alert, $disposition] = $sync->alert($request->user(), $client_trip_id, $client_alert_id, $request->validated());

        return response()->json(['data' => ApiData::alert($alert), 'sync' => ['disposition' => $disposition]],
            $disposition === 'created' ? 201 : 200);
    }
}
