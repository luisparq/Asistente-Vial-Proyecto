<?php

namespace App\Http\Controllers\Dashboard;

use App\Http\Controllers\Controller;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

class MqttDiagnosticsController extends Controller
{
    public function __invoke(Request $request)
    {
        $companyId = $request->user('web')->company_id;
        $deviceId = $this->selectedId($request, 'device');
        $sessionId = $this->selectedId($request, 'session');
        $devices = DB::table('devices')->join('diagnostic_device_codes', 'devices.id', '=', 'diagnostic_device_codes.device_id')
            ->where('devices.company_id', $companyId)
            ->orderBy('devices.name')->orderBy('devices.id')->get(['devices.id', 'devices.name', 'diagnostic_device_codes.code']);
        $device = $deviceId === null ? $devices->first() : $devices->firstWhere('id', $deviceId);
        abort_if($deviceId !== null && ! $device, 404);

        $sessions = $device ? DB::table('diagnostic_sessions')->where('company_id', $companyId)
            ->where('device_id', $device->id)->orderByDesc('started_at')->orderByDesc('id')->get() : collect();
        $session = $sessionId === null ? $sessions->first() : $sessions->firstWhere('id', $sessionId);
        abort_if($sessionId !== null && ! $session, 404);

        $samples = collect();
        $end = null;
        if ($session) {
            $window = DB::table('diagnostic_samples')->where('session_id', $session->id);
            $latest = (clone $window)->max('sampled_at');
            if ($latest !== null) {
                $end = CarbonImmutable::parse($latest, 'UTC');
                $samples = $window->whereBetween('sampled_at', [
                    $end->subSeconds(30)->format('Y-m-d H:i:s.v'),
                    $end->format('Y-m-d H:i:s.v'),
                ])->orderByDesc('sampled_at')->orderByDesc('sequence')->limit(2000)
                    ->get(['sampled_at', 'ax', 'ay', 'az'])->reverse()->values();
            }
        }

        $chart = $this->chart($samples, $end);

        return view('devices.diagnostics', compact('devices', 'device', 'sessions', 'session', 'samples', 'end', 'chart'));
    }

    private function selectedId(Request $request, string $key): ?int
    {
        $value = $request->query($key);
        if ($value === null) {
            return null;
        }

        $id = filter_var($value, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
        abort_if($id === false, 404);

        return $id;
    }

    private function chart(Collection $samples, ?CarbonImmutable $end): array
    {
        $scale = max(1, $samples->max(fn ($sample) => max(abs($sample->ax), abs($sample->ay), abs($sample->az))) ?? 0);
        $series = ['ax' => [], 'ay' => [], 'az' => []];
        if ($end !== null) {
            foreach ($samples as $sample) {
                $secondsAgo = ($end->valueOf() - CarbonImmutable::parse($sample->sampled_at, 'UTC')->valueOf()) / 1000;
                $x = round(810 - 760 * $secondsAgo / 30, 2);
                foreach (array_keys($series) as $axis) {
                    $series[$axis][] = [$x, round(110 - 90 * $sample->$axis / $scale, 2)];
                }
            }
        }

        return ['scale' => $scale, 'series' => $series];
    }
}
