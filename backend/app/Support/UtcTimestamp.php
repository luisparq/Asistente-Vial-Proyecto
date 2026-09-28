<?php

namespace App\Support;

use Closure;
use DateTimeImmutable;
use DateTimeInterface;
use DateTimeZone;
use Illuminate\Contracts\Validation\ValidationRule;

class UtcTimestamp implements ValidationRule
{
    public const FORMAT = 'Y-m-d\TH:i:s.v\Z';

    public function validate(string $attribute, mixed $value, Closure $fail): void
    {
        if (! is_string($value) || ! preg_match('/^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}Z$/D', $value)) {
            $fail('Debe ser una fecha UTC con milisegundos: YYYY-MM-DDTHH:mm:ss.SSSZ.');

            return;
        }
        $date = DateTimeImmutable::createFromFormat('!'.self::FORMAT, $value, new DateTimeZone('UTC'));
        if (! $date || $date->format(self::FORMAT) !== $value) {
            $fail('Fecha inexistente o fuera del rango 1000–9999.');
        }
    }

    public static function format(?DateTimeInterface $date): ?string
    {
        return $date ? DateTimeImmutable::createFromInterface($date)->setTimezone(new DateTimeZone('UTC'))->format(self::FORMAT) : null;
    }
}
