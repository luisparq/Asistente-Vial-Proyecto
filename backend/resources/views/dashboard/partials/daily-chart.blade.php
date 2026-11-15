<section class="panel chart fleet-daily-chart">
    <h2>Tendencia de eventos</h2>
    <p class="muted">Eventos recibidos por día de ocurrencia</p>
    @if($days === [])
        <p class="empty">No hay datos disponibles para este período.</p>
    @else
        @php
            $start = \Carbon\CarbonImmutable::parse($filters['from'], \App\Support\DashboardDisplay::ZONE);
            $end = \Carbon\CarbonImmutable::parse($filters['to'], \App\Support\DashboardDisplay::ZONE);
            $span = (int) $start->diffInDays($end);
            $maximum = max($days);
            $points = [];
            for ($day = 0; $day <= $span; $day++) {
                $count = $days[$start->addDays($day)->toDateString()] ?? 0;
                $x = $span ? 36 + 428 * $day / $span : 250;
                $y = 148 - 126 * $count / $maximum;
                $points[] = [round($x, 2), round($y, 2)];
            }
            $line = implode(' ', array_map(fn ($point) => implode(',', $point), $points));
        @endphp
        <svg viewBox="0 0 480 158" role="img" aria-label="{{ array_sum($days) }} eventos en el período, con un máximo de {{ $maximum }} por día">
            <path class="fleet-chart-gridlines" d="M36 22H464 M36 85H464 M36 148H464"/>
            <text x="28" y="26" text-anchor="end">{{ $maximum }}</text>
            <text x="28" y="151" text-anchor="end">0</text>
            @if($span)
                <polygon class="fleet-trend-area" points="36,148 {{ $line }} 464,148"/>
                <polyline class="fleet-trend-line" points="{{ $line }}" fill="none"/>
            @else
                <circle class="fleet-trend-marker" cx="{{ $points[0][0] }}" cy="{{ $points[0][1] }}" r="5"/>
            @endif
        </svg>
        <div class="fleet-trend-dates"><span>{{ $start->format('d/m/Y') }}</span><span>{{ $end->format('d/m/Y') }}</span></div>
        <details class="fleet-daily-values">
            <summary>Ver conteos diarios</summary>
            <ul>
                @foreach($days as $date => $count)
                    <li><time datetime="{{ $date }}">{{ \Carbon\CarbonImmutable::parse($date)->format('d/m/Y') }}</time> <strong>{{ $count }}</strong></li>
                @endforeach
            </ul>
        </details>
    @endif
</section>
