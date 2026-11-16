<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

class DashboardHeaders
{
    public function handle(Request $request, Closure $next)
    {
        abort_if(app()->environment('production') && ! $request->secure(), 400, 'Se requiere HTTPS.');
        $nonce = $request->routeIs('dashboard.*') ? base64_encode(random_bytes(18)) : null;
        $request->attributes->set('fleet_script_nonce', $nonce);
        $response = $next($request);
        $response->headers->set('Cache-Control', 'no-store, private');
        $response->headers->set('X-Frame-Options', 'DENY');
        $response->headers->set('X-Content-Type-Options', 'nosniff');
        $response->headers->set('Referrer-Policy', 'same-origin');
        if (! $response->headers->has('Content-Security-Policy')) {
            if ($request->routeIs('dashboard.map', 'dashboard.events', 'dashboard.live-map', 'dashboard.trip', 'platform.analytics')) {
                $origins = implode(' ', config('maps.allowed_origins', []));
                $response->headers->set('Content-Security-Policy', "default-src 'self'; script-src 'self'; worker-src 'self' blob:; connect-src 'self' $origins; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: $origins; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
            } else {
                $scripts = $nonce ? "'nonce-$nonce'" : "'none'";
                $response->headers->set('Content-Security-Policy', "default-src 'self'; script-src $scripts; style-src 'self'; img-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
            }
        }

        return $response;
    }
}
