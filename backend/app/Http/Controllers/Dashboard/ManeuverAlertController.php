<?php

namespace App\Http\Controllers\Dashboard;

use App\Http\Controllers\Controller;
use App\Services\DashboardQuery;
use Illuminate\Http\Request;

class ManeuverAlertController extends Controller
{
    public function show(Request $request, string $uuid, DashboardQuery $query)
    {
        $user = $request->user('web');
        $alert = $query->alert($user, $uuid);
        $events = $alert->events()->where('driving_events.company_id', $user->company_id)
            ->where('driving_events.trip_id', $alert->trip_id)->orderBy('occurred_at')->orderBy('driving_events.id')->get();
        // The driver directory already grants contact access to active Fleet administrators.
        $canViewContact = $user->role === 'fleet_admin' && $user->accessEnabled();
        $phone = $canViewContact ? $alert->driver?->emergencyContact()
            ->where('company_id', $user->company_id)->value('phone') : null;

        return view('dashboard.alert', compact('alert', 'events', 'canViewContact', 'phone'));
    }

    public function review(Request $request, string $uuid, DashboardQuery $query)
    {
        $user = $request->user('web');
        $alert = $query->alert($user, $uuid);
        abort_if($alert->test_only, 404);
        $query->alerts($user)->whereKey($alert->id)->whereNull('reviewed_at')->update([
            'reviewed_at' => now(), 'reviewed_by_user_id' => $user->id,
        ]);

        return redirect()->route('dashboard.alerts.show', $uuid);
    }
}
