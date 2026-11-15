@extends($global?'platform.layout':'dashboard.layout')
@section('title','Ficha del dispositivo')
@section('data-notice', '')
@section('page-action')
@unless($global)<a class="button quiet" href="{{ route(request()->routeIs('dashboard.support.devices.show') ? 'dashboard.support.devices' : 'dashboard.devices') }}">Volver a dispositivos</a>@endunless
@endsection
@section('content')
@include('support.device-report')
<section class="panel detail"><h2>Tickets relacionados</h2>
    <div class="actions">
        <a class="button primary" href="{{ route($base.'.create', $global ? [] : ['device_id' => $device->id]) }}">Registrar incidencia</a>
        <a href="{{ route($base, ['device_id' => $device->id]) }}">Ver en el centro de soporte</a>
    </div>
    @forelse($tickets as $ticket)
        <article class="support-entry">
            <a href="{{ route($base.'.show', $ticket->id) }}"><strong>{{ $ticket->subject }}</strong></a>
            <small>{{ $ticket->code }} · {{ \App\Models\SupportTicket::STATUSES[$ticket->status] }}</small>
        </article>
    @empty
        <p>No hay incidencias relacionadas con este dispositivo.</p>
    @endforelse
    @include('dashboard.partials.pagination', ['rows' => $tickets])
</section>
@endsection
