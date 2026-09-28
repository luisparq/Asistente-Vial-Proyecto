<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;

class ResourceIds
{
    public function handle(Request $request, Closure $next)
    {
        foreach (['client_trip_id', 'client_event_id'] as $key) {
            if ($request->route($key) !== null) {
                Validator::make([$key => $request->route($key)], [$key => ['required', 'uuid:4']])->validate();
                $request->route()->setParameter($key, strtolower($request->route($key)));
            }
        }

        return $next($request);
    }
}
