<?php

namespace App\Http\Controllers;

use App\Services\MqttDiagnosticsIngest;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class SensorRelayController extends Controller
{
    public function store(Request $request, MqttDiagnosticsIngest $ingest)
    {
        $user = $request->user();
        abort_unless($user->role === 'driver' && $user->permits('trips:write') && $user->driver()->where('company_id', $user->company_id)->where('status', 'ACTIVE')->exists(), 403);
        $data = $request->validate(['samples' => ['required', 'array', 'min:1', 'max:100'], 'samples.*' => ['required', 'array']]);
        $results = DB::transaction(function () use ($data, $ingest, $user) {
            $results = [];
            foreach ($data['samples'] as $sample) {
                // Android omits default values when it serializes queued BLE v2 samples.
                $sample += ['protocol' => 2];
                $code = $sample['deviceCode'] ?? '';
                abort_unless(is_string($code) && preg_match('/\A'.MqttDiagnosticsIngest::CODE_PATTERN.'\z/', $code), 422);
                $ok = $ingest->store("avigem/devices/$code/imu", json_encode($sample), false, 'BLE_API', $user->id);
                abort_unless($ok, 409, 'Muestra inválida, dispositivo no autorizado o identidad con valores diferentes.');
                $results[] = ['deviceCode' => $sample['deviceCode'], 'sessionId' => $sample['sessionId'], 'sequence' => $sample['sequence']];
            }

            return $results;
        });

        return response()->json(['accepted' => $results]);
    }
}
