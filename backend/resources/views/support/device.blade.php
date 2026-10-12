@extends($global?'platform.layout':'dashboard.layout')
@section('title','Ficha del dispositivo')
@section('content')
@include('support.device-report')
<section class="panel"><h2>Tickets relacionados</h2><p><a href="{{ route($base,['device_id'=>$device->id]) }}">Filtrar en el centro de soporte</a> · <a href="{{ route($base.'.create') }}">Registrar incidencia</a></p>@forelse($tickets as $ticket)<article><a href="{{ route($base.'.show',$ticket->id) }}">{{ $ticket->code }} · {{ $ticket->subject }}</a><p>{{ \App\Models\SupportTicket::STATUSES[$ticket->status] }}</p></article>@empty<p>No hay tickets relacionados con este dispositivo.</p>@endforelse @include('dashboard.partials.pagination',['rows'=>$tickets])</section>
@endsection
