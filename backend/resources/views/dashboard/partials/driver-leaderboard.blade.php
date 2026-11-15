<section class="panel fleet-leaderboard" aria-labelledby="driver-leaderboard-title">
    <div class="panel-heading">
        <div>
            <h2 id="driver-leaderboard-title">Conductores con menos eventos</h2>
            <p>Últimos 30 días · {{ \App\Services\DriverScores::PENALTY }} puntos menos por cada maniobra recibida.</p>
        </div>
        <a href="{{ route('dashboard.drivers') }}">Ver conductores</a>
    </div>
    @if($driverLeaders->isEmpty())
        <p class="empty">Aún no hay actividad de las cuentas Driver para calcular puntajes.</p>
    @else
        @if($driverLeaders->first()->score_origin === 'SIMULATED')
            <p class="fleet-leaderboard-note"><span class="badge simulated">Puntajes de prueba</span> Basados en la actividad de la cuenta Driver y los eventos simulados recibidos.</p>
        @else
            <p class="fleet-leaderboard-note">Puntajes provisionales basados en eventos de sensor recibidos. La sincronización puede estar pendiente.</p>
        @endif
        <ol class="fleet-leaders">
            @foreach($driverLeaders as $driver)
                <li>
                    <a href="{{ route('dashboard.drivers.show', $driver->id) }}">
                        @include('fleet.driver-avatar')
                        <span><strong>{{ $driver->full_name }}</strong><small>{{ $driver->scoring_events_count }} eventos · {{ $driver->scoring_trips_count }} recorridos</small></span>
                        <strong>{{ $driver->score() }}/100</strong>
                    </a>
                </li>
            @endforeach
        </ol>
    @endif
</section>
