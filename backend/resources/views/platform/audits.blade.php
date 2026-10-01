@extends('platform.layout')
@section('title','Auditoría administrativa')
@section('content')
<section class="panel table-wrap"><table><thead><tr><th>Fecha</th><th>Actor</th><th>Empresa ID</th><th>Acción</th><th>Entidad</th><th>Campos</th></tr></thead><tbody>@forelse($audits as $audit)<tr><td>{{ \App\Support\DashboardDisplay::date($audit->occurred_at) }}</td><td>{{ $audit->actor?->name ?? 'Operador' }}</td><td>{{ $audit->company_id }}</td><td>{{ $audit->action }}</td><td>{{ $audit->entity }} #{{ $audit->entity_id }}</td><td>{{ implode(', ',$audit->changes['fields']??[]) }}</td></tr>@empty<tr><td colspan="6">Sin acciones administrativas registradas.</td></tr>@endforelse</tbody></table>@include('dashboard.partials.pagination',['rows'=>$audits])</section>
@endsection
