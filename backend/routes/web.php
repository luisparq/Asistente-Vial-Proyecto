<?php

use Illuminate\Support\Facades\Route;

Route::get('/', fn () => response()->json(['service' => 'Asistente Vial API', 'version' => 'v1']));
