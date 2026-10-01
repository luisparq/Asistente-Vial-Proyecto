<?php

namespace App\Support;

use Carbon\CarbonImmutable;
use DateTimeInterface;

class DashboardDisplay
{
    public const ZONE = 'America/Lima';

    public static function date(?DateTimeInterface $date): string
    {
        return $date ? CarbonImmutable::instance($date)->setTimezone(self::ZONE)->format('d/m/Y H:i:s') : '—';
    }

    public static function type(string $type): string
    {
        return match ($type) {
            'HARSH_BRAKING' => 'Frenado brusco',
            'HARSH_ACCELERATION' => 'Aceleración brusca',
            'SHARP_TURN' => 'Giro brusco',
            default => $type,
        };
    }

    public static function status(string $status): string
    {
        return match ($status) {
            'ACTIVE' => 'Activo registrado', 'COMPLETED' => 'Finalizado', default => $status
        };
    }

    public static function origin(string $origin): string
    {
        return $origin === 'SIMULATED' ? 'Simulado · dato de prueba' : 'SENSOR · reservado, sin validación física';
    }
}
