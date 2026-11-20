<?php

namespace App\Http\Controllers\Dashboard;

use App\Http\Controllers\Controller;
use App\Models\User;
use App\Services\SensorFieldTrials;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;

class MqttDiagnosticsController extends Controller
{
    public function __invoke(Request $request)
    {
        $global = $request->routeIs('platform.*');
        $prefix = $global ? 'platform' : 'dashboard';
        $companyId = $global ? $this->selectedId($request, 'company') : $request->user('web')->company_id;
        $companies = $global ? DB::table('companies')->orderBy('name')->get(['id', 'name']) : collect();
        $deviceId = $this->selectedId($request, 'device');
        $sessionId = $this->selectedId($request, 'session');
        $devices = DB::table('devices')->leftJoin('diagnostic_device_codes', 'devices.id', '=', 'diagnostic_device_codes.device_id')
            ->join('companies', 'companies.id', '=', 'devices.company_id')
            ->when($companyId !== null, fn ($q) => $q->where('devices.company_id', $companyId))
            ->orderBy('companies.name')->orderBy('devices.name')->orderBy('devices.id')
            ->get(['devices.id', 'devices.name', 'devices.company_id', 'companies.name as company_name', 'diagnostic_device_codes.code']);
        $device = $deviceId === null ? $devices->first() : $devices->firstWhere('id', $deviceId);
        abort_if($deviceId !== null && ! $device, 404);

        $sessions = $device ? DB::table('diagnostic_sessions')->where('company_id', $device->company_id)
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
                    ->get(['sampled_at', 'ax', 'ay', 'az', 'gx', 'gy', 'gz'])->reverse()->values();
            }
        }

        $chart = $this->chart($samples, $end);
        $gyroChart = $this->chart($samples->map(fn ($s) => (object) ['sampled_at' => $s->sampled_at, 'ax' => $s->gx, 'ay' => $s->gy, 'az' => $s->gz]), $end);
        $trials = $device ? DB::table('sensor_field_trials')->where('company_id', $device->company_id)->where('device_id', $device->id)->orderByDesc('id')->paginate(15)->withQueryString() : collect();
        $active = $device ? DB::table('sensor_field_trials')->where('device_id', $device->id)->where('company_id', $device->company_id)->where('status', 'ACTIVE')->first() : null;
        $references = $device && $session ? DB::table('sensor_field_trials')->where('device_id', $device->id)->where('company_id', $device->company_id)
            ->where('session_id', $session->id)->where('phase', 'REST')->where('status', 'READY')->where('source', 'MQTT')->orderByDesc('id')->limit(20)->get() : collect();
        $trialId = $this->selectedId($request, 'trial');
        $trial = $trialId ? $this->trial($request, $trialId) : null;
        abort_if($trial && (! $device || $trial->device_id !== $device->id), 404);
        $trialSummary = $trial ? ($trial->status === 'ACTIVE' ? app(SensorFieldTrials::class)->summary(app(SensorFieldTrials::class)->rows($trial)) : json_decode($trial->summary, true)) : null;

        $latest = $session ? DB::table('diagnostic_samples')->where('session_id', $session->id)->orderByDesc('sequence')->first() : null;
        $deviceState = $device ? DB::table('diagnostic_device_state')->where('device_id', $device->id)->first() : null;
        $sensorState = $deviceState ? json_decode($deviceState->mqtt_payload ?? $deviceState->payload, true) : [];
        $commands = $global && $device ? DB::table('diagnostic_device_commands')->where('device_id', $device->id)->orderByDesc('created_at')->limit(8)->get() : collect();
        $events = $device ? DB::table('diagnostic_sensor_events')->where('company_id', $device->company_id)->where('device_id', $device->id)->orderByDesc('received_at')->limit(15)->get() : collect();
        $relayOwner = $latest?->relay_user_id ? User::with('driver.currentAssignment.vehicle')->find($latest->relay_user_id) : null;

        return view('devices.diagnostics', compact('devices', 'device', 'sessions', 'session', 'samples', 'end', 'chart', 'global', 'prefix', 'companies', 'companyId', 'trials', 'active', 'references', 'trial', 'trialSummary', 'latest', 'deviceState', 'sensorState', 'commands', 'events', 'relayOwner', 'gyroChart'));
    }

    public function start(Request $request)
    {
        abort_unless($request->routeIs('platform.*'), 403);
        $data = $request->validate(['device' => ['required', 'integer', 'min:1'], 'session' => ['required', 'integer', 'min:1'],
            'phase' => ['required', 'in:REST,RECORDING'], 'mounting' => ['required', 'string', 'max:500'], 'reference' => ['nullable', 'integer', 'min:1']]);
        $device = $this->device($request, (int) $data['device']);
        $id = DB::transaction(function () use ($request, $data, $device) {
            DB::table('devices')->where('id', $device->id)->lockForUpdate()->first();
            abort_if(DB::table('sensor_field_trials')->where('device_id', $device->id)->where('status', 'ACTIVE')->exists(), 409, 'Ya hay una prueba activa. Detén y guarda esa prueba primero.');
            $session = DB::table('diagnostic_sessions')->where('id', $data['session'])->where('device_id', $device->id)->where('company_id', $device->company_id)->first();
            abort_unless($session, 404);
            $latest = DB::table('diagnostic_samples')->where('session_id', $session->id)->max('sampled_at');
            abort_unless($latest && CarbonImmutable::parse($latest, 'UTC')->gte(CarbonImmutable::now('UTC')->subSeconds(2)), 422, 'No hay recepción MQTT reciente. Usa el CSV del teléfono si estás sin Internet.');
            $reference = null;
            if ($data['phase'] === 'RECORDING') {
                $reference = DB::table('sensor_field_trials')->where('id', $data['reference'] ?? 0)->where('company_id', $device->company_id)->where('device_id', $device->id)
                    ->where('session_id', $session->id)->where('source', 'MQTT')->where('phase', 'REST')->where('status', 'READY')->where('mounting', $data['mounting'])->first();
                abort_unless($reference, 422, 'Mide primero el reposo con este dispositivo, sesión y montaje.');
            }

            return DB::table('sensor_field_trials')->insertGetId(['company_id' => $device->company_id, 'device_id' => $device->id, 'user_id' => $request->user('web')->id,
                'session_id' => $session->id, 'reference_id' => $reference?->id, 'source' => 'MQTT', 'phase' => $data['phase'], 'status' => 'ACTIVE', 'mounting' => $data['mounting'], 'started_at' => now('UTC')->format('Y-m-d H:i:s.v')]);
        });

        return $this->redirectTrial($request, $id, 'Prueba iniciada. La captura web usa las muestras MQTT recibidas por el servidor.');
    }

    public function stop(Request $request, int $trial, SensorFieldTrials $service)
    {
        abort_unless($request->routeIs('platform.*'), 403);
        DB::transaction(function () use ($request, $trial, $service) {
            $record = $this->trial($request, $trial, true);
            $service->finish($record);
        });

        return $this->redirectTrial($request, $trial, 'Prueba guardada. Revisa el estado del reposo y descarga el CSV.');
    }

    public function label(Request $request, int $trial)
    {
        abort_unless($request->routeIs('platform.*'), 403);
        $data = $request->validate(['label' => ['required', Rule::in(array_keys(SensorFieldTrials::LABELS))]]);
        DB::transaction(function () use ($request, $trial, $data) {
            $record = $this->trial($request, $trial, true);
            abort_unless($record->status === 'ACTIVE' && $record->phase === 'RECORDING', 409);
            DB::table('sensor_field_labels')->insert(['trial_id' => $trial, 'label' => $data['label'], 'marked_at' => now('UTC')->format('Y-m-d H:i:s.v')]);
        });

        return $this->redirectTrial($request, $trial, 'Etiqueta aplicada a las siguientes muestras. Es una anotación manual.');
    }

    public function import(Request $request, SensorFieldTrials $service)
    {
        abort_unless($request->routeIs('platform.*'), 403);
        $data = $request->validate(['device' => ['required', 'integer', 'min:1'], 'csv' => ['required', 'file', 'max:10240']]);
        $device = $this->device($request, (int) $data['device']);
        $id = $service->import($request->file('csv')->getRealPath(), $device, $request->user('web')->id);

        return $this->redirectTrial($request, $id, 'CSV del teléfono importado. Se conserva su origen y sus tiempos Android.');
    }

    public function export(Request $request, int $trial, SensorFieldTrials $service)
    {
        $record = $this->trial($request, $trial);
        abort_if($record->status === 'ACTIVE', 409, 'Detén la prueba antes de exportar.');

        return response()->streamDownload(function () use ($record, $service) {
            $out = fopen('php://output', 'wb');
            fputcsv($out, explode(',', SensorFieldTrials::HEADER.($record->source === 'MQTT' ? ',server_utc_ms' : '')), ',', '"', '');
            foreach ($service->rows($record) as $row) {
                // Keep spreadsheet applications from evaluating user-supplied mounting text as a formula.
                $row = array_map(fn ($v) => is_string($v) && ! is_numeric($v) && preg_match('/\A\s*[=+@\-\t\r]/', $v) ? "'".$v : $v, $row);
                fputcsv($out, $row, ',', '"', '');
            }
            if ($record->source === 'MQTT') {
                $row = array_fill(0, 41, '');
                $row[0] = 'END';
                $row[6] = $record->phase;
                $row[39] = $record->status;
                $row[40] = CarbonImmutable::parse($record->finished_at, 'UTC')->valueOf();
                fputcsv($out, $row, ',', '"', '');
            }
            fclose($out);
        }, 'sensor-'.$record->id.'-'.$record->source.'.csv', ['Content-Type' => 'text/csv; charset=UTF-8']);
    }

    public function control(Request $request)
    {
        abort_unless($request->routeIs('platform.*'), 403);
        $data = $request->validate(['device' => ['required', 'integer'], 'operation' => ['required', Rule::in(['rest', 'acceleration', 'braking', 'turn', 'finish', 'enable', 'disable', 'buzzer'])]]);
        $device = $this->device($request, (int) $data['device']);
        abort_unless(DB::table('diagnostic_device_codes')->where('device_id', $device->id)->exists(), 422, 'Vincula el código MQTT del AVIGEM One antes de enviar comandos.');
        $id = (string) Str::uuid();
        DB::table('diagnostic_device_commands')->insert(['id' => $id, 'device_id' => $device->id, 'user_id' => $request->user('web')->id, 'operation' => $data['operation'], 'status' => 'QUEUED', 'created_at' => now('UTC')]);

        return redirect()->route('platform.devices.diagnostics', ['device' => $device->id])->with('success', 'Orden en cola. Espera confirmación del ESP32; enviada no significa aplicada.');
    }

    private function device(Request $request, int $id): object
    {
        $query = DB::table('devices')->where('id', $id);
        if (! $request->routeIs('platform.*')) {
            $query->where('company_id', $request->user('web')->company_id);
        }
        $record = $query->first();
        abort_unless($record, 404);

        return $record;
    }

    private function trial(Request $request, int $id, bool $lock = false): object
    {
        $query = DB::table('sensor_field_trials')->where('id', $id);
        if (! $request->routeIs('platform.*')) {
            $query->where('company_id', $request->user('web')->company_id);
        }
        if ($lock) {
            $query->lockForUpdate();
        }
        $record = $query->first();
        abort_unless($record, 404);

        return $record;
    }

    private function redirectTrial(Request $request, int $id, string $message)
    {
        $trial = $this->trial($request, $id);

        return redirect()->route(($request->routeIs('platform.*') ? 'platform' : 'dashboard').'.devices.diagnostics',
            array_filter(['device' => $trial->device_id, 'session' => $trial->session_id, 'trial' => $id]))->with('success', $message);
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
