<?php

namespace App\Http\Middleware;

use App\Models\PersonalAccessToken;
use App\Support\ApiProblem;
use Closure;
use Illuminate\Http\Request;

class TenantContext
{
    public function handle(Request $request, Closure $next)
    {
        $user = $request->user();
        $token = $user?->currentAccessToken();
        if (! $token instanceof PersonalAccessToken || (int) $token->company_id !== (int) $user->company_id) {
            throw new ApiProblem(401, 'invalid_token_context', 'La sesión no corresponde al contexto autorizado.');
        }

        if (! $user->accessEnabled() || $user->role === 'platform_admin') {
            throw new ApiProblem(403, 'access_disabled', 'Acceso no disponible para esta cuenta o empresa.');
        }

        return $next($request);
    }
}
