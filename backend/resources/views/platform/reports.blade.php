@extends('platform.layout')
@section('title','Reportes descriptivos')
@section('content')
<form class="panel fleet-form" method="get"><label>Desde (America/Lima)<input type="date" name="from" value="{{ request('from') }}"></label><label>Hasta (inclusive)<input type="date" name="to" value="{{ request('to') }}"></label><button class="button">Filtrar actividad</button></form>
<section class="panel table-wrap"><h2>Registros por empresa</h2><p>Conductores y vehículos: total actual. Recorridos y eventos: fecha de ocurrencia dentro del período seleccionado.</p><table><thead><tr><th>Empresa</th><th>Conductores</th><th>Vehículos</th><th>Recorridos</th><th>Eventos</th></tr></thead><tbody>@foreach($companies as $company)<tr><td>{{ $company->name }}</td><td>{{ $drivers[$company->id]??0 }}</td><td>{{ $vehicles[$company->id]??0 }}</td><td>{{ $company->trips_count }}</td><td>{{ $company->events_count }}</td></tr>@endforeach</tbody></table>@include('dashboard.partials.pagination',['rows'=>$companies])</section>
<section class="panel"><h2>Eventos por tipo y origen</h2>@forelse($events as $event)<p>{{ \App\Support\DashboardDisplay::type($event->type) }} · {{ \App\Support\DashboardDisplay::origin($event->origin) }}: {{ $event->total }}</p>@empty<p>Sin eventos en el período.</p>@endforelse</section>
@endsection
