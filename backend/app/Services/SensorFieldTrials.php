<?php

namespace App\Services;

use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class SensorFieldTrials
{
    public const HEADER = 'row_type,android_utc_ms,android_monotonic_ms,elapsed_ms,device,mounting,phase,label,ax_raw,ay_raw,az_raw,gx_raw,gy_raw,gz_raw,ax_g,ay_g,az_g,gx_dps,gy_dps,gz_dps,rest_ax_g,rest_ay_g,rest_az_g,rest_gx_dps,rest_gy_dps,rest_gz_dps,delta_ax_g,delta_ay_g,delta_az_g,corrected_gx_dps,corrected_gy_dps,corrected_gz_dps,gps_status,latitude,longitude,gps_age_ms,satellites,gps_android_utc_ms,gap_before,stop_reason';

    public const LABELS = ['NORMAL' => 'Marcha normal', 'ACCELERATION' => 'Aceleración', 'BRAKING' => 'Frenada', 'TURN_LEFT' => 'Giro izquierdo', 'TURN_RIGHT' => 'Giro derecho', 'STOPPED' => 'Detenido'];

    public const AXES = ['ax', 'ay', 'az', 'gx', 'gy', 'gz'];

    public function rows(object $trial, ?CarbonImmutable $until = null): iterable
    {
        if ($trial->source === 'ANDROID_CSV') {
            foreach (DB::table('sensor_field_rows')->where('trial_id', $trial->id)->orderBy('id')->cursor() as $row) {
                yield json_decode($row->payload, true, flags: JSON_THROW_ON_ERROR);
            }

            return;
        }
        $start = CarbonImmutable::parse($trial->started_at, 'UTC');
        $end = $until ?? ($trial->finished_at ? CarbonImmutable::parse($trial->finished_at, 'UTC') : CarbonImmutable::now('UTC'));
        $end = $end->min($start->addHours(2));
        if ($trial->phase === 'REST') {
            $end = $end->min($start->addSeconds(30));
        }
        $reference = $trial->reference_id ? json_decode(DB::table('sensor_field_trials')->where('id', $trial->reference_id)->value('summary'), true) : null;
        $labels = DB::table('sensor_field_labels')->where('trial_id', $trial->id)->orderBy('marked_at')->orderBy('id')->get();
        $index = 0;
        $label = 'NORMAL';
        $previous = null;
        foreach (DB::table('diagnostic_samples')->where('session_id', $trial->session_id)
            ->where('sampled_at', '>=', $start->format('Y-m-d H:i:s.v'))->where('sampled_at', '<=', $end->format('Y-m-d H:i:s.v'))
            ->orderBy('sampled_at')->orderBy('sequence')->cursor() as $sample) {
            $time = CarbonImmutable::parse($sample->sampled_at, 'UTC')->valueOf();
            while ($index < $labels->count() && CarbonImmutable::parse($labels[$index]->marked_at, 'UTC')->valueOf() <= $time) {
                $label = $labels[$index++]->label;
            }
            $values = array_map(fn ($axis, $i) => $sample->$axis / ($i < 3 ? 16384 : 131), self::AXES, range(0, 5));
            $base = array_map(fn ($i) => $reference['axes'][$i]['mean'] ?? '', range(0, 5));
            $delta = array_map(fn ($v, $b) => $b === '' ? '' : $v - $b, $values, $base);
            // Server reception times have their own columns: never pretend these are Android/GPS timestamps.
            yield array_merge(['SAMPLE', '', '', $time - $start->valueOf(), $trial->device_id, $trial->mounting, $trial->phase,
                $trial->phase === 'REST' ? 'REST' : $label], array_map(fn ($axis) => $sample->$axis, self::AXES), $values, $base, $delta,
                ['UNAVAILABLE', '', '', '', '', '', $previous !== null && $time - $previous > 750 ? 1 : 0, '', $time]);
            $previous = $time;
        }
    }

    public function summary(iterable $rows): array
    {
        $n = 0;
        $means = $m2 = array_fill(0, 6, 0.0);
        $mins = $maxs = array_fill(0, 6, null);
        $gaps = 0;
        $first = $last = null;
        $labels = [];
        foreach ($rows as $row) {
            if ($row[0] !== 'SAMPLE') {
                continue;
            }
            $n++;
            $elapsed = (float) $row[3];
            $first ??= $elapsed;
            $last = $elapsed;
            $gaps += (int) $row[38];
            $labels[$row[7]] = ($labels[$row[7]] ?? 0) + 1;
            foreach (self::AXES as $i => $axis) {
                $value = (int) $row[8 + $i] / ($i < 3 ? 16384 : 131);
                $d = $value - $means[$i];
                $means[$i] += $d / $n;
                $m2[$i] += $d * ($value - $means[$i]);
                $mins[$i] = $mins[$i] === null ? $value : min($mins[$i], $value);
                $maxs[$i] = $maxs[$i] === null ? $value : max($maxs[$i], $value);
            }
        }
        $axes = [];
        $stable = $n >= 100 && $gaps === 0;
        foreach (self::AXES as $i => $axis) {
            $sd = $n > 1 ? sqrt(max(0, $m2[$i] / ($n - 1))) : null;
            $axes[] = ['axis' => $axis, 'mean' => $n ? $means[$i] : null, 'sd' => $sd, 'min' => $mins[$i], 'max' => $maxs[$i], 'unit' => $i < 3 ? 'g' : '°/s'];
            $stable = $stable && $sd !== null && $sd <= ($i < 3 ? 0.02 : 1);
        }

        return ['samples' => $n, 'duration_ms' => $last === null ? 0 : $last - $first,
            'hz' => $last > $first ? ($n - 1) * 1000 / ($last - $first) : 0, 'gaps' => $gaps, 'stable' => $stable, 'axes' => $axes, 'labels' => $labels];
    }

    public function finish(object $trial): void
    {
        if ($trial->status !== 'ACTIVE') {
            return;
        }
        $now = CarbonImmutable::now('UTC');
        $start = CarbonImmutable::parse($trial->started_at, 'UTC');
        $end = $trial->phase === 'REST' ? $now->min($start->addSeconds(30)) : $now->min($start->addHours(2));
        $summary = $this->summary($this->rows($trial, $end));
        $status = $trial->phase === 'REST' ? ($now->gte($start->addSeconds(30)) && $summary['stable'] && $summary['duration_ms'] >= 29_000 ? 'READY' : 'REJECTED') : 'COMPLETED';
        DB::table('sensor_field_trials')->where('id', $trial->id)->update(['finished_at' => $end->format('Y-m-d H:i:s.v'), 'status' => $status, 'summary' => json_encode($summary, JSON_THROW_ON_ERROR)]);
    }

    public function import(string $path, object $device, int $userId): int
    {
        return DB::transaction(function () use ($path, $device, $userId) {
            $handle = fopen($path, 'rb');
            try {
                $header = fgetcsv($handle, 0, ',', '"', '');
                $this->ensure($header === explode(',', self::HEADER), 'El CSV no tiene el formato exportado por Prueba del sensor de Android.');
                $trial = null;
                $count = 0;
                $last = null;
                $mount = null;
                $phase = null;
                $ended = false;
                $gap = 0;
                while (($row = fgetcsv($handle, 0, ',', '"', '')) !== false) {
                    if ($row === [null]) {
                        continue;
                    }
                    $this->ensure(count($row) === 40 && ! $ended, 'CSV incompleto o con columnas inválidas.');
                    if ($row[0] === 'END') {
                        $this->ensure($trial !== null, 'El CSV no contiene muestras.');
                        $ended = true;
                    } else {
                        $this->ensure($row[0] === 'SAMPLE' && ++$count <= 40000, 'El CSV contiene filas desconocidas o demasiadas muestras.');
                        $this->ensure(in_array($row[6], ['REST', 'RECORDING'], true) && strlen($row[5]) <= 500 && trim($row[5]) !== '', 'Fase o montaje inválido.');
                        $this->ensure(in_array($row[7], array_merge(['REST'], array_keys(self::LABELS)), true), 'Etiqueta inválida.');
                        $this->ensure(ctype_digit($row[3]) && ($last === null || (int) $row[3] > $last), 'Los tiempos de las muestras deben ser crecientes.');
                        foreach (range(8, 13) as $i) {
                            $this->ensure(preg_match('/\A-?\d+\z/D', $row[$i]) && (int) $row[$i] >= -32768 && (int) $row[$i] <= 32767, 'Valor RAW fuera de rango.');
                        }
                        foreach (array_merge(range(14, 31), [33, 34, 35, 36, 37]) as $i) {
                            $this->ensure($row[$i] === '' || (is_numeric($row[$i]) && is_finite((float) $row[$i])), 'Valor numérico inválido.');
                        }
                        $this->ensure(in_array($row[38], ['0', '1'], true), 'Marca de interrupción inválida.');
                        if ($last !== null && (int) $row[3] - $last > 750) {
                            $row[38] = '1';
                        }
                        $last = (int) $row[3];
                        if ($trial === null) {
                            $mount = $row[5];
                            $phase = $row[6];
                            $trial = DB::table('sensor_field_trials')->insertGetId(['company_id' => $device->company_id, 'device_id' => $device->id, 'user_id' => $userId,
                                'source' => 'ANDROID_CSV', 'phase' => $phase, 'status' => 'IMPORTED', 'mounting' => $mount, 'started_at' => now('UTC')]);
                        }
                        $this->ensure($mount === $row[5] && $phase === $row[6], 'No mezcles montajes ni fases en un archivo.');
                    }
                    DB::table('sensor_field_rows')->insert(['trial_id' => $trial, 'payload' => json_encode($row, JSON_THROW_ON_ERROR)]);
                }
                $this->ensure($trial !== null && $count > 0, 'El archivo no contiene muestras válidas.');
                $record = DB::table('sensor_field_trials')->find($trial);
                $summary = $this->summary($this->rows($record));
                $summary['explicit_end'] = $ended;
                $status = ! $ended ? 'INCOMPLETE' : ($phase === 'REST' ? ($summary['stable'] && $summary['duration_ms'] >= 29_000 ? 'READY' : 'REJECTED') : 'COMPLETED');
                DB::table('sensor_field_trials')->where('id', $trial)->update(['status' => $status, 'finished_at' => now('UTC'), 'summary' => json_encode($summary, JSON_THROW_ON_ERROR)]);

                return $trial;
            } finally {
                fclose($handle);
            }
        });
    }

    private function ensure(bool $condition, string $message): void
    {
        if (! $condition) {
            throw ValidationException::withMessages(['csv' => $message]);
        }
    }
}
