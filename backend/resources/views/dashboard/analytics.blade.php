@extends('dashboard.layout')
@section('title', 'Analítica')
@section('description', 'Conteos de registros recibidos. No representan una evaluación de conducción.')
@section('content')
<form class="filters panel" method="get" action="{{ route('dashboard.analytics') }}">@include('dashboard.partials.dates')<button class="button primary">Consultar período</button><a href="{{ route('dashboard.analytics') }}">Últimos 30 días</a></form>
<p class="context">Eventos agrupados por su fecha de ocurrencia; recorridos por su fecha de inicio, en America/Lima. Los días sin registros no se dibujan. Máximo 366 días.</p>
<div class="charts">
@include('dashboard.partials.chart', ['title' => 'Eventos por tipo', 'description' => 'Categorías preliminares, sin validación física.', 'values' => $types, 'labels' => [\App\Support\DashboardDisplay::class, 'type']])
@include('dashboard.partials.chart', ['title' => 'Recorridos por estado', 'description' => 'Último estado recibido de Android.', 'values' => $states, 'labels' => [\App\Support\DashboardDisplay::class, 'status']])
@include('dashboard.partials.chart', ['title' => 'Origen de los eventos', 'description' => 'SIMULATED identifica datos de prueba.', 'values' => $origins, 'labels' => [\App\Support\DashboardDisplay::class, 'origin']])
@include('dashboard.partials.chart', ['title' => 'Eventos por día', 'description' => 'Conteo recibido por día local; no implica recepción completa.', 'values' => $days, 'labels' => null])
</div>
@endsection
