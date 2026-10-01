<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

class FleetAdmin
{
    public function handle(Request $request, Closure $next)
    {
        abort_unless($request->user('web')?->role === 'fleet_admin' && $request->user('web')->accessEnabled(), 403);

        return $next($request);
    }
}
