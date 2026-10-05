@extends('platform.layout')
@section('title','Empresas')
@section('content')
<section class="panel table-wrap"><table><thead><tr><th>Empresa</th><th>Estado</th><th>Cuentas</th><th>Recorridos</th><th>Eventos</th></tr></thead><tbody>@forelse($companies as $company)<tr><td><a href="{{ route('platform.companies.show',$company->id) }}">{{ $company->name }}</a></td><td>{{ $company->status }}</td><td>{{ $company->users_count }}</td><td>{{ $company->trips_count }}</td><td>{{ $company->events_count }}</td></tr>@empty<tr><td colspan="5">Sin empresas.</td></tr>@endforelse</tbody></table>@include('dashboard.partials.pagination',['rows'=>$companies])</section>
<section class="panel"><h2>Registrar empresa</h2><form method="post" action="{{ route('platform.companies.store') }}" class="fleet-form">@csrf<input type="hidden" name="submission_token" value="{{ $submissionToken }}"><label>Nombre<input name="name" required maxlength="255" value="{{ old('name') }}"></label><button class="button">Registrar empresa</button></form></section>
@endsection
