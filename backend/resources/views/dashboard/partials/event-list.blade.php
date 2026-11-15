@php($display = \App\Support\DashboardDisplay::class)
@if($events->isEmpty())
    <div class="empty"><x-nav-icon name="events"/><h3>No hay eventos disponibles para este período.</h3></div>
@else
    <ul class="fleet-event-list">
        @foreach($events as $event)
            <li>
                <div class="fleet-event-heading">
                    <div>
                        <h3>{{ $display::type($event->type) }}</h3>
                        <small>{{ $display::date($event->occurred_at) }} · Lima</small>
                    </div>
                    <span class="badge simulated">Dato de prueba</span>
                </div>
                @if($event->trip?->driver || $event->trip?->vehicle)
                    <small>{{ implode(' · ', array_filter([$event->trip->driver?->full_name, $event->trip->vehicle?->plate])) }}</small>
                @endif
                @include('dashboard.partials.event-location')
                <details><summary>Identificador del evento</summary><code class="uuid">{{ $event->client_event_id }}</code></details>
                @if($event->trip)
                    <a href="{{ route('dashboard.trip', ['uuid' => $event->trip->client_trip_id] + $tripReturn) }}">Ver recorrido</a>
                @endif
            </li>
        @endforeach
    </ul>
@endif
