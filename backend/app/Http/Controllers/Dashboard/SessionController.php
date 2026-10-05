<?php

namespace App\Http\Controllers\Dashboard;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Validation\ValidationException;

class SessionController extends Controller
{
    public function create(Request $request)
    {
        $platform = $request->is('admin/*');

        return $request->user('web')?->role === ($platform ? 'platform_admin' : 'fleet_admin') && $request->user('web')->accessEnabled()
            ? redirect()->route($platform ? 'platform.index' : 'dashboard.index') : view($platform ? 'platform.login' : 'dashboard.login');
    }

    public function store(Request $request)
    {
        $data = $request->validate(['email' => 'required|string|email|max:255', 'password' => 'required|string|max:1024']);
        $data['email'] = strtolower($data['email']);
        $keys = ['web-ip:'.$request->ip() => 20, 'web-account:'.hash('sha256', $data['email']) => 5];
        foreach ($keys as $key => $limit) {
            if (RateLimiter::tooManyAttempts($key, $limit)) {
                throw ValidationException::withMessages(['email' => 'Demasiados intentos. Espera un minuto y vuelve a intentarlo.']);
            }
        }
        foreach ($keys as $key => $limit) {
            RateLimiter::hit($key, 60);
        }
        $platform = $request->is('admin/*');
        if (! Auth::guard('web')->attemptWhen($data + ['role' => $platform ? 'platform_admin' : 'fleet_admin'], fn ($user) => $user->accessEnabled(), false)) {
            throw ValidationException::withMessages(['email' => 'No se pudo iniciar sesión con esas credenciales y permisos.']);
        }
        foreach ($keys as $key => $limit) {
            RateLimiter::clear($key);
        }
        $request->session()->regenerate();

        return redirect()->route($platform ? 'platform.index' : 'dashboard.index');
    }

    public function destroy(Request $request)
    {
        Auth::guard('web')->logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route($request->is('admin/*') ? 'platform.login' : 'login');
    }
}
