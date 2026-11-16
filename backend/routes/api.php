<?php

use App\Http\Controllers\AccessController;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\DriverProfileController;
use App\Http\Controllers\EventController;
use App\Http\Controllers\ManeuverAlertController;
use App\Http\Controllers\RoadCatalogController;
use App\Http\Controllers\TripController;
use App\Http\Controllers\TrafficFlowTileController;
use App\Http\Middleware\ApiBoundary;
use App\Http\Middleware\ResourceIds;
use App\Http\Middleware\TenantContext;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->middleware(ApiBoundary::class)->group(function () {
    Route::get('road-catalog', RoadCatalogController::class)->middleware('throttle:60,1')->name('road-catalog');
    Route::get('traffic-flow/{z}/{x}/{y}.png', TrafficFlowTileController::class)
        ->whereNumber(['z', 'x', 'y'])->middleware('throttle:240,1')->name('traffic-flow.tile');
    Route::get('road/reverse-geocode', [\App\Http\Controllers\RoadAssistanceController::class, 'reverseGeocode'])->middleware('throttle:60,1');
    Route::get('road/route', [\App\Http\Controllers\RoadAssistanceController::class, 'calculateRoute'])->middleware('throttle:30,1');
    Route::get('road/traffic-flow-segment', [\App\Http\Controllers\RoadAssistanceController::class, 'flowSegment'])->middleware('throttle:60,1');
    Route::get('road/emergency-pois', [\App\Http\Controllers\RoadAssistanceController::class, 'emergencyPois'])->middleware('throttle:60,1');
    Route::get('road/traffic-incidents', [\App\Http\Controllers\RoadAssistanceController::class, 'trafficIncidents'])->middleware('throttle:60,1')->name('road.traffic-incidents');
    Route::get('road/schools', [\App\Http\Controllers\RoadAssistanceController::class, 'schools'])->middleware('throttle:60,1')->name('road.schools');
    Route::post('auth/token', [AuthController::class, 'issue'])->middleware('throttle:login');
    Route::post('auth/activate', [AccessController::class, 'activateDriver'])->middleware('throttle:activation');
    Route::middleware(['auth:sanctum', TenantContext::class, 'throttle:api', ResourceIds::class])->group(function () {
        Route::put('devices/{client_device_id}/status', [\App\Http\Controllers\DeviceController::class, 'report']);
        Route::get('me', [AuthController::class, 'me']);
        Route::get('me/driver', [DriverProfileController::class, 'show']);
        Route::delete('auth/token', [AuthController::class, 'revoke']);
        Route::put('trips/{client_trip_id}', [TripController::class, 'put']);
        Route::get('trips', [TripController::class, 'index'])->name('trips.index');
        Route::get('trips/{client_trip_id}', [TripController::class, 'show']);
        Route::put('trips/{client_trip_id}/track-points/{client_point_id}', [\App\Http\Controllers\TripTrackController::class, 'put']);
        Route::put('trips/{client_trip_id}/events/{client_event_id}', [EventController::class, 'put']);
        Route::put('trips/{client_trip_id}/alerts/{client_alert_id}', [ManeuverAlertController::class, 'put']);
        Route::get('trips/{client_trip_id}/events', [EventController::class, 'index'])->name('events.index');
    });
});
