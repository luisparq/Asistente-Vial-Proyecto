<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

class PlatformAdmin
{
    public function handle(Request $request, Closure $next)
    {
        abort_unless($request->user('web')?->role === 'platform_admin' && $request->user('web')->accessEnabled(), 403);

        return $next($request);
    }
}
