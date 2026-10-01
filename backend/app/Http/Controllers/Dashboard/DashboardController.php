<?php

namespace App\Http\Controllers\Dashboard;

use App\Http\Controllers\Controller;
use App\Http\Requests\DashboardFilterRequest;
use App\Services\DashboardQuery;
use App\Support\DashboardDisplay;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

class DashboardController extends Controller
{
    public function __construct(private DashboardQuery $query) {}

    public function index(Request $request)
    {
        $trips = $this->query->trips($request->user('web'));
        $events = $this->query->events($request->user('web'));
        $metrics = ['Recorridos recibidos' => (clone $trips)->count(),
            'Finalizados' => (clone $trips)->where('status', 'COMPLETED')->count(),
            'Activos registrados' => (clone $trips)->where('status', 'ACTIVE')->count(),
            'Eventos recibidos' => (clone $events)->count(),
            'Eventos simulados' => (clone $events)->where('origin', 'SIMULATED')->count()];

        return view('dashboard.overview', ['metrics' => $metrics,
            'trips' => $trips->with('creator')->orderByDesc('first_received_at')->orderByDesc('id')->limit(5)->get(),
            'events' => $events->with('trip')->orderByDesc('first_received_at')->orderByDesc('id')->limit(5)->get()]);
    }

    public function trips(DashboardFilterRequest $request)
    {
        return view('dashboard.trips', ['trips' => $this->query->trips($request->user('web'), $request->validated())
            ->with('creator')->orderByDesc('started_at')->orderByDesc('id')->paginate(20)->withQueryString(),
            'users' => $request->user('web')->company->users()->orderBy('name')->get(['id', 'name'])]);
    }

    public function trip(Request $request, string $uuid)
    {
        $trip = $this->query->trips($request->user('web'))->with('creator')->where('client_trip_id', strtolower($uuid))->firstOrFail();

        return view('dashboard.trip', ['trip' => $trip, 'events' => $this->query->events($request->user('web'))
            ->where('trip_id', $trip->id)->orderBy('occurred_at')->orderBy('id')->paginate(20)]);
    }

    public function events(DashboardFilterRequest $request)
    {
        return view('dashboard.events', ['events' => $this->query->events($request->user('web'), $request->validated())
            ->with('trip')->orderByDesc('occurred_at')->orderByDesc('id')->paginate(20)->withQueryString()]);
    }

    public function analytics(DashboardFilterRequest $request)
    {
        $filters = $request->validated();
        $today = CarbonImmutable::today(DashboardDisplay::ZONE);
        $filters['to'] = $filters['to'] ?? $today->format('Y-m-d');
        $filters['from'] = $filters['from'] ?? CarbonImmutable::parse($filters['to'], DashboardDisplay::ZONE)->subDays(29)->format('Y-m-d');
        $from = CarbonImmutable::parse($filters['from'], DashboardDisplay::ZONE);
        $to = CarbonImmutable::parse($filters['to'], DashboardDisplay::ZONE);
        if ($from->gt($to) || $from->diffInDays($to) > 365) {
            throw ValidationException::withMessages(['from' => 'Selecciona un período de hasta 366 días, con inicio anterior o igual al final.']);
        }
        $events = $this->query->events($request->user('web'), $filters);
        $types = (clone $events)->selectRaw('type, COUNT(*) AS total')->groupBy('type')->pluck('total', 'type');
        $origins = (clone $events)->selectRaw('origin, COUNT(*) AS total')->groupBy('origin')->pluck('total', 'origin');
        $states = $this->query->trips($request->user('web'), $filters)->selectRaw('status, COUNT(*) AS total')->groupBy('status')->pluck('total', 'status');
        $days = [];
        // Stream timestamps; group by local day without depending on SQLite-specific SQL.
        foreach ((clone $events)->select(['id', 'occurred_at'])->lazyById(500) as $event) {
            $day = $event->occurred_at->setTimezone(DashboardDisplay::ZONE)->format('Y-m-d');
            $days[$day] = ($days[$day] ?? 0) + 1;
        }
        ksort($days);

        return view('dashboard.analytics', compact('filters', 'types', 'origins', 'states', 'days'));
    }

    public function settings()
    {
        return view('dashboard.settings');
    }
}
