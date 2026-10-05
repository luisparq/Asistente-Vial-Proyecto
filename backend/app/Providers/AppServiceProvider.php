<?php

namespace App\Providers;

use App\Models\PersonalAccessToken;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;
use Laravel\Sanctum\Sanctum;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        // No additional container bindings are required.
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        Sanctum::usePersonalAccessTokenModel(PersonalAccessToken::class);
        RateLimiter::for('activation', fn ($request) => Limit::perMinute(6)->by('activation-ip:'.$request->ip()));
        RateLimiter::for('api', fn ($request) => Limit::perMinute(120)->by('api:'.$request->user()->id));
        RateLimiter::for('login', fn ($request) => [
            Limit::perMinute(20)->by('login-ip:'.$request->ip()),
            Limit::perMinute(5)->by('login-account:'.hash('sha256', is_string($request->input('email')) ? strtolower($request->input('email')) : 'invalid-input')),
        ]);
    }
}
