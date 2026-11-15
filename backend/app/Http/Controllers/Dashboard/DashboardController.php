<?php

namespace App\Http\Controllers\Dashboard;

use App\Http\Controllers\Controller;
use App\Http\Requests\DashboardFilterRequest;
use App\Models\Driver;
use App\Models\User;
use App\Models\Vehicle;
use App\Services\DashboardQuery;
use App\Services\DriverScores;
use App\Support\DashboardDisplay;
use Carbon\CarbonImmutable;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class DashboardController extends Controller
{
    public function __construct(private DashboardQuery $query) {}

    public function index(DashboardFilterRequest $request, DriverScores $scores)
    {
        $user = $request->user('web');
        $trips = $this->query->trips($user);
        $events = $this->query->events($user);
        $metrics = ['Recorridos recibidos' => (clone $trips)->count(),
            'Finalizados' => (clone $trips)->where('status', 'COMPLETED')->count(),
            'Activos registrados' => (clone $trips)->where('status', 'ACTIVE')->count(),
            'Eventos recibidos' => $events->count()];

        return view('dashboard.overview', ['metrics' => $metrics,
            'trips' => $this->withTripDetails($trips, $user)->orderByDesc('first_received_at')->orderByDesc('id')->limit(5)->get(),
            'notifications' => $this->notificationData($request),
            'driverLeaders' => $scores->leaders($user->company_id),
            'drivers' => Driver::where('company_id', $user->company_id)->orderBy('full_name')->get(['id', 'full_name']),
            'vehicles' => Vehicle::where('company_id', $user->company_id)->orderBy('plate')->get(['id', 'plate']),
            ...$this->analyticsData($request)]);
    }

    public function notifications(DashboardFilterRequest $request)
    {
        return response()->view('dashboard.partials.notifications', [
            'notifications' => $this->notificationData($request),
        ])->header('X-Fleet-Notifications', '1');
    }

    private function notificationData(DashboardFilterRequest $request): Collection
    {
        $user = $request->user('web');
        $filters = $request->validated();
        $companyScope = fn ($query) => $query->where('company_id', $user->company_id);
        $events = $this->query->events($user)->whereHas('trip', $companyScope)
            ->with(['trip' => $companyScope, 'trip.driver' => $companyScope, 'trip.vehicle' => $companyScope]);

        foreach (['notice_driver' => 'driver_id', 'notice_vehicle' => 'vehicle_id'] as $filter => $column) {
            if (isset($filters[$filter])) {
                $events->whereHas('trip', fn ($query) => $query->where($column, $filters[$filter]));
            }
        }

        return $events->orderByDesc('first_received_at')->orderByDesc('id')->limit(5)->get();
    }

    public function trips(DashboardFilterRequest $request)
    {
        $user = $request->user('web');

        return view('dashboard.trips', ['trips' => $this->withTripDetails($this->query->trips($user, $request->validated()), $user)
            ->orderByDesc('started_at')->orderByDesc('id')->paginate(20)->withQueryString(),
            'users' => $user->company->users()->orderBy('name')->get(['id', 'name'])]);
    }

    public function trip(Request $request, string $uuid)
    {
        $user = $request->user('web');
        $trip = $this->query->trips($user)->with($this->tripRelations($user))
            ->where('client_trip_id', strtolower($uuid))->firstOrFail();

        [$returnRoute, $returnLabel, $filterKeys] = match ($request->query('from_view')) {
            'summary' => ['dashboard.index', 'Volver al resumen', ['from', 'to', 'notice_driver', 'notice_vehicle']],
            'events' => ['dashboard.events', 'Volver a eventos', ['from', 'to', 'type', 'origin', 'vehicle_id']],
            default => ['dashboard.trips', 'Volver a recorridos', ['from', 'to', 'status', 'user_id']],
        };
        $returnFilters = $request->only($filterKeys);
        if ($returnRoute !== 'dashboard.index' && $request->filled('source_page')) {
            $returnFilters['page'] = $request->query('source_page');
        }

        return view('dashboard.trip', [
            'trip' => $trip,
            'track' => DB::table('trip_track_points')->where('trip_id', $trip->id)
                ->where('captured_at', '>=', $trip->getRawOriginal('started_at'))
                ->when($trip->ended_at, fn ($points) => $points->where('captured_at', '<=', $trip->getRawOriginal('ended_at')))
                ->orderBy('captured_at')->orderBy('id')->get(['latitude', 'longitude', 'captured_at'])
                ->map(fn ($point) => [
                    'coordinates' => [(float) $point->longitude, (float) $point->latitude],
                    'time' => CarbonImmutable::parse($point->captured_at, 'UTC')->getTimestampMs(),
                ]),
            'events' => $this->query->events($user)->where('trip_id', $trip->id)
                ->orderBy('occurred_at')->orderBy('id')->paginate(20)->withQueryString(),
            'returnRoute' => $returnRoute,
            'returnLabel' => $returnLabel,
            'returnFilters' => $returnFilters,
        ]);
    }

    public function analytics(Request $request)
    {
        return redirect()->route('dashboard.index', $request->query());
    }

    private function analyticsData(DashboardFilterRequest $request): array
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
        $states = $this->query->trips($request->user('web'), $filters)->selectRaw('status, COUNT(*) AS total')->groupBy('status')->pluck('total', 'status');
        $days = [];
        // Stream timestamps; group by local day without depending on SQLite-specific SQL.
        foreach ((clone $events)->select(['id', 'occurred_at'])->lazyById(500) as $event) {
            $day = $event->occurred_at->setTimezone(DashboardDisplay::ZONE)->format('Y-m-d');
            $days[$day] = ($days[$day] ?? 0) + 1;
        }
        ksort($days);

        return compact('filters', 'types', 'states', 'days');
    }

    private function withTripDetails(Builder $trips, User $user): Builder
    {
        return $trips->with($this->tripRelations($user))
            ->withCount(['events' => fn ($events) => $events->where('company_id', $user->company_id)]);
    }

    private function tripRelations(User $user): array
    {
        $companyScope = fn ($relation) => $relation->where('company_id', $user->company_id);

        return ['driver' => $companyScope, 'vehicle' => $companyScope];
    }

    public function settings(Request $request)
    {
        return view('dashboard.settings', ['account' => $request->user('web')]);
    }

    public function updateSettings(Request $request)
    {
        $settings = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'fleet_notification_interval' => ['required', 'integer', 'in:0,15,30,60'],
        ]);
        $request->user('web')->forceFill($settings)->save();

        return redirect()->route('dashboard.settings')->with('success', 'Configuración guardada.');
    }
}
