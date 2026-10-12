@php
    $fields=$kind==='trips'?['trips']:['simulated','sensor'];
    $maximum=max(1,collect($series)->max(fn($row)=>array_sum(array_intersect_key($row,array_flip($fields)))));
    $total=collect($series)->sum(fn($row)=>array_sum(array_intersect_key($row,array_flip($fields))));
    $width=640/count($series);
@endphp
@if($total===0)<div class="global-empty">Sin registros para este periodo.</div>@else
<svg viewBox="0 0 700 240" role="img" aria-label="{{ $kind==='trips'?'Viajes':'Eventos por origen' }} por día. Total: {{ $total }}. Detalle disponible debajo.">
<line x1="35" y1="195" x2="680" y2="195" stroke="#ccd9e3"/><text x="5" y="32" fill="#526579" font-size="12">{{ $maximum }}</text>
@foreach($series as $row)
@php($x=38+$loop->index*$width)
@if($kind==='trips')
@php($height=$row['trips']/$maximum*160)
<rect x="{{ $x }}" y="{{ 195-$height }}" width="{{ max(2,$width-5) }}" height="{{ $height }}" rx="2" fill="#087f82"><title>{{ $row['day'] }}: {{ $row['trips'] }} viajes</title></rect>
@else
@php($sim=$row['simulated']/$maximum*160) @php($sensor=$row['sensor']/$maximum*160)
<rect x="{{ $x }}" y="{{ 195-$sim }}" width="{{ max(2,$width-5) }}" height="{{ $sim }}" fill="#ca871a"><title>{{ $row['day'] }}: {{ $row['simulated'] }} SIMULATED</title></rect>
<rect x="{{ $x }}" y="{{ 195-$sim-$sensor }}" width="{{ max(2,$width-5) }}" height="{{ $sensor }}" fill="#255b8f"><title>{{ $row['day'] }}: {{ $row['sensor'] }} SENSOR</title></rect>
@endif
@if(count($series)===7 || $loop->index%($labelEvery??5)===0 || $loop->last)<text x="{{ $x }}" y="218" fill="#526579" font-size="11">{{ $row['label'] }}</text>@endif
@endforeach</svg>
@endif
<details><summary>Ver cantidades por día</summary><div class="table-wrap"><table><thead><tr><th>Día</th>@foreach($fields as $field)<th>{{ $field==='trips'?'Viajes':strtoupper($field) }}</th>@endforeach</tr></thead><tbody>@foreach($series as $row)<tr><td>{{ $row['day'] }}</td>@foreach($fields as $field)<td>{{ $row[$field] }}</td>@endforeach</tr>@endforeach</tbody></table></div></details>
