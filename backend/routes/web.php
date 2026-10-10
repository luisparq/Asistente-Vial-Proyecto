<?php

use App\Http\Controllers\AccessController;
use App\Http\Controllers\Dashboard\DashboardController;
use App\Http\Controllers\Dashboard\FleetController;
use App\Http\Controllers\Dashboard\SessionController;
use App\Http\Controllers\PlatformController;
use App\Http\Middleware\DashboardHeaders;
use App\Http\Middleware\FleetAdmin;
use App\Http\Middleware\PlatformAdmin;
use Illuminate\Support\Facades\Route;

Route::get('/', fn () => response()->json(['service' => 'Asistente Vial API', 'version' => 'v1']));

Route::middleware(DashboardHeaders::class)->group(function () {
    Route::get('/dashboard/login', [SessionController::class, 'create'])->name('login');
    Route::post('/dashboard/login', [SessionController::class, 'store'])->name('dashboard.login');
    Route::post('/dashboard/logout', [SessionController::class, 'destroy'])->middleware('auth:web')->name('dashboard.logout');
    Route::prefix('dashboard')->name('dashboard.')->middleware(['auth:web', 'auth.session', FleetAdmin::class])->group(function () {
        $fleet = FleetController::class;
        Route::get('/drivers', [$fleet, 'drivers'])->name('drivers');
        Route::get('/drivers/create', [$fleet, 'createDriver'])->name('drivers.create');
        Route::post('/drivers', [$fleet, 'storeDriver'])->name('drivers.store');
        Route::get('/drivers/{id}', [$fleet, 'showDriver'])->whereNumber('id')->name('drivers.show');
        Route::get('/drivers/{id}/edit', [$fleet, 'editDriver'])->whereNumber('id')->name('drivers.edit');
        Route::put('/drivers/{id}', [$fleet, 'updateDriver'])->whereNumber('id')->name('drivers.update');
        Route::post('/drivers/{id}/access', [$fleet, 'access'])->whereNumber('id')->name('drivers.access');
        Route::post('/drivers/{id}/assignment', [$fleet, 'assign'])->whereNumber('id')->name('drivers.assign');
        Route::post('/drivers/{id}/documents', [$fleet, 'upload'])->whereNumber('id')->name('drivers.documents');
        Route::get('/documents/{id}', [$fleet, 'download'])->whereNumber('id')->name('documents.download');
        Route::post('/documents/{id}/review', [$fleet, 'review'])->whereNumber('id')->name('documents.review');
        Route::get('/vehicles', [$fleet, 'vehicles'])->name('vehicles');
        Route::get('/vehicles/create', [$fleet, 'createVehicle'])->name('vehicles.create');
        Route::post('/vehicles', [$fleet, 'storeVehicle'])->name('vehicles.store');
        Route::get('/vehicles/{id}', [$fleet, 'showVehicle'])->whereNumber('id')->name('vehicles.show');
        Route::get('/vehicles/{id}/edit', [$fleet, 'editVehicle'])->whereNumber('id')->name('vehicles.edit');
        Route::put('/vehicles/{id}', [$fleet, 'updateVehicle'])->whereNumber('id')->name('vehicles.update');
        Route::get('/', [DashboardController::class, 'index'])->name('index');
        Route::get('/trips', [DashboardController::class, 'trips'])->name('trips');
        Route::get('/trips/{uuid}', [DashboardController::class, 'trip'])->whereUuid('uuid')->name('trip');
        Route::get('/events', [DashboardController::class, 'events'])->name('events');
        Route::get('/map', \App\Http\Controllers\Dashboard\IncidentMapController::class)->name('map');
        Route::get('/analytics', [DashboardController::class, 'analytics'])->name('analytics');
        Route::get('/settings', [DashboardController::class, 'settings'])->name('settings');
    });
    Route::get('/platform/{path?}', fn (?string $path = null) => redirect('/admin'.($path ? '/'.$path : ''), 302))->where('path', '.*');
    Route::get('/access', [AccessController::class, 'create'])->name('access');
    Route::post('/access', [AccessController::class, 'store'])->middleware('throttle:6,1')->name('access.store');
    Route::get('/admin/login', [SessionController::class, 'create'])->name('platform.login');
    Route::post('/admin/login', [SessionController::class, 'store'])->name('platform.login.store');
    Route::post('/admin/logout', [SessionController::class, 'destroy'])->middleware('auth:web')->name('platform.logout');
    Route::prefix('admin')->name('platform.')->middleware(['auth:web', 'auth.session', PlatformAdmin::class])->group(function () {
        $controller = PlatformController::class;
        Route::get('/', [$controller, 'companies'])->name('index');
        Route::get('/companies', [$controller, 'companies'])->name('companies');
        Route::post('/companies', [$controller, 'storeCompany'])->block(10, 10)->name('companies.store');
        Route::get('/companies/{id}', [$controller, 'company'])->whereNumber('id')->name('companies.show');
        Route::post('/companies/{id}/state', [$controller, 'companyState'])->whereNumber('id')->name('companies.state');
        Route::get('/users', [$controller, 'users'])->name('users');
        Route::post('/users', [$controller, 'storeAdmin'])->name('users.store');
        Route::post('/users/{id}/access', [$controller, 'userAccess'])->whereNumber('id')->name('users.access');
        Route::get('/reports', [$controller, 'reports'])->name('reports');
        Route::get('/audits', [$controller, 'audits'])->name('audits');
    });
});
