<section class="panel chart"><h2>{{ $title }}</h2><p class="muted">{{ $description }}</p>
@if(count($values) === 0)<p class="empty">No hay datos disponibles para este período.</p>
@else
@php($maximum = max(1, max(collect($values)->values()->all())))
<ul class="bars">@foreach($values as $label => $value)<li><div><span>{{ $labels ? $labels($label) : $label }}</span><strong>{{ $value }}</strong></div><meter min="0" max="{{ $maximum }}" value="{{ $value }}" aria-label="{{ $labels ? $labels($label) : $label }}: {{ $value }} registros">{{ $value }}</meter></li>@endforeach</ul>
@endif</section>
