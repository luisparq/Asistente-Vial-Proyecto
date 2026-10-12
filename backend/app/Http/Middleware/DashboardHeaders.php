<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

class DashboardHeaders
{
    public function handle(Request $request, Closure $next)
    {
        abort_if(app()->environment('production') && ! $request->secure(), 400, 'Se requiere HTTPS.');
        $response = $next($request);
        $response->headers->set('Cache-Control', 'no-store, private');
        $response->headers->set('X-Frame-Options', 'DENY');
        $response->headers->set('X-Content-Type-Options', 'nosniff');
        $response->headers->set('Referrer-Policy', 'same-origin');
        if (! $response->headers->has('Content-Security-Policy')) {
            if ($request->routeIs('dashboard.map', 'platform.analytics')) {
                $origins = implode(' ', config('maps.allowed_origins', []));
                $response->headers->set('Content-Security-Policy', "default-src 'self'; script-src 'self'; worker-src 'self' blob:; connect-src 'self' $origins; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: $origins; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
            } else $response->headers->set('Content-Security-Policy', "default-src 'self'; script-src 'none'; style-src 'self'; img-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
        }

        return $response;
    }
}
