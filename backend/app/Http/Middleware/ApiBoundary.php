<?php

namespace App\Http\Middleware;

use App\Support\ApiProblem;
use Closure;
use Illuminate\Http\Request;
use JsonException;

class ApiBoundary
{
    public function handle(Request $request, Closure $next)
    {
        if (app()->environment('production') && ! $request->secure()) {
            throw new ApiProblem(400, 'https_required', 'Se requiere HTTPS.');
        }
        if ($request->routeIs('driver-profile.update')
            && str_starts_with(strtolower($request->header('Content-Type', '')), 'multipart/form-data;')) {
            if ((int) $request->header('Content-Length', 0) > 2_113_536) {
                throw new ApiProblem(413, 'payload_too_large', 'La petición excede el límite permitido.');
            }

            return $next($request);
        }
        if (strlen($request->getContent()) > ($request->routeIs('sensor-samples.store') ? 65536 : 16384)) {
            throw new ApiProblem(413, 'payload_too_large', 'La petición excede el límite permitido.');
        }
        if (in_array($request->method(), ['PUT', 'POST'], true)) {
            if (! $request->isJson()) {
                throw new ApiProblem(415, 'json_required', 'Se requiere application/json.');
            }
            try {
                $body = json_decode($request->getContent(), false, 32, JSON_THROW_ON_ERROR);
            } catch (JsonException) {
                throw new ApiProblem(400, 'invalid_json', 'JSON ilegible.');
            }
            if (! is_object($body)) {
                throw new ApiProblem(400, 'invalid_json', 'El cuerpo debe ser un objeto JSON.');
            }
        }

        return $next($request);
    }
}
