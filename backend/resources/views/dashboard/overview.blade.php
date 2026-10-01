@extends('dashboard.layout')
@section('title', 'Resumen de actividad')
@section('description', 'Recorridos y eventos que tu empresa ha recibido hasta ahora.')
@section('content')
<section class="metrics" aria-label="Indicadores">@foreach($metrics as $label => $value)<article class="metric"><span>{{ $label }}</span><strong>{{ number_format($value, 0, ',', '.') }}</strong><small>{{ $label === 'Eventos simulados' ? 'Generados manualmente' : 'Total recibido' }}</small></article>@endforeach</section>
<p class="context">“Activo registrado” es el último estado enviado por Android; no confirma que alguien esté conduciendo ahora.</p>
<section class="panel"><div class="panel-heading"><div><h2>Recorridos recibidos recientemente</h2><p>Hasta cinco registros, ordenados por primera recepción.</p></div><a href="{{ route('dashboard.trips') }}">Ver recorridos →</a></div>@include('dashboard.partials.trips')</section>
<section class="panel"><div class="panel-heading"><div><h2>Eventos recibidos recientemente</h2><p>Hasta cinco registros. Pueden llegar después de finalizar el recorrido.</p></div><a href="{{ route('dashboard.events') }}">Ver eventos →</a></div>@include('dashboard.partials.events')</section>
@endsection
