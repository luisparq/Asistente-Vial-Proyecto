<?php

use App\Http\Controllers\AccessController;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\DriverProfileController;
use App\Http\Controllers\EventController;
use App\Http\Controllers\TripController;
use App\Http\Middleware\ApiBoundary;
use App\Http\Middleware\ResourceIds;
use App\Http\Middleware\TenantContext;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->middleware(ApiBoundary::class)->group(function () {
    Route::post('auth/token', [AuthController::class, 'issue'])->middleware('throttle:login');
    Route::post('auth/activate', [AccessController::class, 'activateDriver'])->middleware('throttle:activation');
    Route::middleware(['auth:sanctum', TenantContext::class, 'throttle:api', ResourceIds::class])->group(function () {
        Route::get('me', [AuthController::class, 'me']);
        Route::get('me/driver', [DriverProfileController::class, 'show']);
        Route::delete('auth/token', [AuthController::class, 'revoke']);
        Route::put('trips/{client_trip_id}', [TripController::class, 'put']);
        Route::get('trips', [TripController::class, 'index'])->name('trips.index');
        Route::get('trips/{client_trip_id}', [TripController::class, 'show']);
        Route::put('trips/{client_trip_id}/events/{client_event_id}', [EventController::class, 'put']);
        Route::get('trips/{client_trip_id}/events', [EventController::class, 'index'])->name('events.index');
    });
});

// Rutas Telemétricas y Analítica
Route::middleware('auth:sanctum')->group(function () {
    Route::post('/trips/{trip}/telemetry-batch', [App\Http\Controllers\Api\TelemetryController::class, 'syncBatch']);
    Route::get('/dashboard/stats', [App\Http\Controllers\Api\TelemetryController::class, 'dashboardStats']);
});

// Rutas Telemétricas y Analítica
Route::middleware('auth:sanctum')->group(function () {
    Route::post('/trips/{trip}/telemetry-batch', [App\Http\Controllers\Api\TelemetryController::class, 'syncBatch']);
    Route::get('/dashboard/stats', [App\Http\Controllers\Api\TelemetryController::class, 'dashboardStats']);
});

// Rutas Telemétricas y Analítica v1
Route::prefix('v1')->middleware('auth:sanctum')->group(function () {
    Route::post('/trips/{trip}/telemetry-batch', [App\Http\Controllers\Api\TelemetryController::class, 'syncBatch']);
    Route::get('/dashboard/stats', [App\Http\Controllers\Api\TelemetryController::class, 'dashboardStats']);
});