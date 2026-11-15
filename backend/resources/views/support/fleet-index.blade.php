@extends('dashboard.layout')
@section('title', 'Centro de soporte técnico')
@section('description', 'Registra incidencias y consulta su seguimiento en tu empresa.')
@section('data-notice', '')
@section('page-action')
<a class="button primary" href="{{ route('dashboard.support.create') }}">Registrar incidencia</a>
@endsection
@section('content')
@php($ticketModel = \App\Models\SupportTicket::class)
<div class="fleet-support-overview">
    <section class="panel fleet-service-summary" aria-labelledby="support-summary">
        <h2 id="support-summary">Estado de las incidencias</h2>
        <dl class="fleet-service-metrics">
            @foreach($ticketModel::STATUSES as $status => $label)
                <div>
                    <dt><a href="{{ route($base, ['status' => $status]) }}"><span class="support-tag {{ strtolower($status) }}">{{ $label }}</span></a></dt>
                    <dd>{{ $metrics[$status] ?? 0 }}</dd>
                </div>
            @endforeach
        </dl>
    </section>
    <aside class="panel fleet-service-device">
        <x-nav-icon name="devices"/>
        <h2>Soporte de dispositivos</h2>
        <p>Consulta el último contacto y las incidencias asociadas a cada equipo.</p>
        <a class="button quiet" href="{{ route('dashboard.support.devices') }}">Ver dispositivos</a>
    </aside>
</div>
<div class="fleet-support-workbench">
    <div>
        @include('support.filters')
        <section class="panel" aria-labelledby="support-tickets">
            <div class="panel-heading"><div><h2 id="support-tickets">Incidencias registradas</h2><p>{{ $tickets->total() }} resultados</p></div></div>
            @if($tickets->isEmpty())
                <div class="empty fleet-support-empty">
                    <x-nav-icon name="support"/>
                    <h3>{{ $metrics->sum() ? 'No hay incidencias que coincidan' : 'Aún no hay incidencias' }}</h3>
                    @if($metrics->sum())
                        <a href="{{ route($base) }}">Quitar filtros</a>
                    @else
                        <a href="{{ route($base.'.create') }}">Registrar la primera incidencia</a>
                    @endif
                </div>
            @else
                <div class="table-wrap">
                    <table>
                        <thead><tr><th>Incidencia</th><th>Estado y prioridad</th><th>Responsable</th><th>Actualización · Lima</th></tr></thead>
                        <tbody>
                            @foreach($tickets as $ticket)
                                <tr>
                                    <td><a href="{{ route($base.'.show', $ticket->id) }}"><strong>{{ $ticket->subject }}</strong></a><small>{{ $ticket->code }}</small></td>
                                    <td><span class="support-tag {{ strtolower($ticket->status) }}">{{ $ticketModel::STATUSES[$ticket->status] }}</span><span class="support-tag {{ strtolower($ticket->priority) }}">{{ $ticketModel::PRIORITIES[$ticket->priority] }}</span></td>
                                    <td>{{ $ticket->responsible?->name ?? 'Sin asignar' }}</td>
                                    <td class="date">{{ \App\Support\DashboardDisplay::date($ticket->updated_at) }}</td>
                                </tr>
                            @endforeach
                        </tbody>
                    </table>
                </div>
                @include('dashboard.partials.pagination', ['rows' => $tickets])
            @endif
        </section>
    </div>
    <aside class="panel fleet-service-queue" aria-labelledby="support-queue">
        <div class="panel-heading"><div><h2 id="support-queue">Pendientes de atención</h2><p>Incidencias abiertas, por prioridad</p></div></div>
        @forelse($pending as $ticket)
            <a class="fleet-service-queue-item" href="{{ route($base.'.show', $ticket->id) }}">
                <strong>{{ $ticket->subject }}</strong><span class="support-tag {{ strtolower($ticket->priority) }}">{{ $ticketModel::PRIORITIES[$ticket->priority] }}</span>
            </a>
        @empty
            <p class="fleet-service-queue-item">No hay incidencias pendientes.</p>
        @endforelse
    </aside>
</div>
@endsection
