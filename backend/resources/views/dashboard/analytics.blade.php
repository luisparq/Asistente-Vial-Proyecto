<section class="fleet-analytics" id="analitica" aria-labelledby="fleet-analytics-title">
    <div class="fleet-analytics-header">
        <div>
            <h2 id="fleet-analytics-title">Analítica por período</h2>
            <p class="muted">Fechas de Lima · Hasta 366 días</p>
        </div>
        <form class="filters" method="get" action="{{ route('dashboard.index') }}#analitica">
            @foreach(request()->only('notice_driver', 'notice_vehicle') as $name => $value)
                <input type="hidden" name="{{ $name }}" value="{{ $value }}">
            @endforeach
            @include('dashboard.partials.dates')
            <button class="button primary">Consultar período</button>
            <a href="{{ route('dashboard.index', request()->only('notice_driver', 'notice_vehicle')) }}#analitica">Últimos 30 días</a>
        </form>
    </div>
    <div class="fleet-analytics-grid">
        @include('dashboard.partials.chart', [
            'title' => 'Eventos por tipo',
            'description' => 'Maniobras registradas, incluidos los eventos de prueba.',
            'values' => $types,
            'labels' => [\App\Support\DashboardDisplay::class, 'type'],
        ])
        <section class="panel chart fleet-status-chart">
            <h2>Recorridos por estado</h2>
            <p class="muted">Último estado recibido de Driver</p>
            @if($states->isEmpty())
                <p class="empty">No hay datos disponibles para este período.</p>
            @else
                @php
                    $total = $states->sum();
                    $completedShare = 100 * ($states['COMPLETED'] ?? 0) / $total;
                @endphp
                <div class="fleet-donut">
                    <svg viewBox="0 0 120 120" aria-hidden="true">
                        <circle class="fleet-donut-track" cx="60" cy="60" r="47" fill="none" stroke-width="16"/>
                        <circle class="fleet-donut-value" cx="60" cy="60" r="47" fill="none" stroke-width="16"
                            pathLength="100" stroke-dasharray="{{ $completedShare }} 100" transform="rotate(-90 60 60)"/>
                    </svg>
                    <div><strong>{{ $total }}</strong><small>Recorridos</small></div>
                </div>
                <ul class="fleet-chart-legend">
                    <li><span class="fleet-status-dot completed"></span>Finalizados <strong>{{ $states['COMPLETED'] ?? 0 }}</strong></li>
                    <li><span class="fleet-status-dot"></span>Activos registrados <strong>{{ $states['ACTIVE'] ?? 0 }}</strong></li>
                </ul>
            @endif
        </section>
        @include('dashboard.partials.daily-chart')
    </div>
</section>
