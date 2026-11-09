@php($max=max(1,collect($rows)->max(fn($r)=>$r['simulated']+$r['sensor'])??0))
@if(collect($rows)->sum(fn($r)=>$r['simulated']+$r['sensor'])===0)<div class="global-empty">Sin eventos para estos filtros.</div>@else
@php($width=640/max(1,count($rows)))
<svg viewBox="0 0 700 250" role="img" aria-label="Cantidades por categoría y origen; detalle debajo"><line x1="35" y1="195" x2="680" y2="195" class="c-axis"/><text x="5" y="25" class="c-label" font-size="12">{{ $max }}</text>@foreach($rows as $row)
@php($x=38+$loop->index*$width) @php($s=$row['simulated']/$max*160) @php($r=$row['sensor']/$max*160)
<rect x="{{ $x }}" y="{{ 195-$s }}" width="{{ max(2,$width-8) }}" height="{{ $s }}" class="c-test"><title>{{ $row['label'] }}: {{ $row['simulated'] }} SIMULATED</title></rect><rect x="{{ $x }}" y="{{ 195-$s-$r }}" width="{{ max(2,$width-8) }}" height="{{ $r }}" class="c-sensor"><title>{{ $row['label'] }}: {{ $row['sensor'] }} SENSOR</title></rect>
@if(count($rows)<13 || $loop->index%3===0)<text x="{{ $x }}" y="215" font-size="11" class="c-label">{{ $row['label'] }}</text>@endif
@endforeach</svg>@endif
<details><summary>Ver cifras y horarios con más registros</summary><div class="table-wrap"><table><thead><tr><th>Categoría</th><th>SIMULATED</th><th>SENSOR</th></tr></thead><tbody>@foreach(collect($rows)->sortByDesc(fn($r)=>$r['simulated']+$r['sensor']) as $row)<tr><td>{{ $row['label'] }}</td><td>{{ $row['simulated'] }}</td><td>{{ $row['sensor'] }}</td></tr>@endforeach</tbody></table></div></details>
