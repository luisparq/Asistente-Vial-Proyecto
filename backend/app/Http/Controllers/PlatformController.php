<?php

namespace App\Http\Controllers;

use App\Models\AccessInvitation;
use App\Models\AdministrativeAudit as Audit;
use App\Models\Company;
use App\Models\Driver;
use App\Models\DrivingEvent;
use App\Models\User;
use App\Models\Vehicle;
use App\Services\AccessInvitations;
use App\Services\AdministrativeAudit;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class PlatformController extends Controller
{
    public function companies()
    {
        return view('platform.companies', ['companies' => Company::withCount(['users', 'trips', 'events'])->orderBy('name')->paginate(20)]);
    }

    public function storeCompany(Request $r)
    {
        $data = $r->validate(['name' => 'required|string|max:255']);
        DB::transaction(function () use ($r, $data) {
            $c = Company::create($data);
            AdministrativeAudit::record($r->user(), $c->id, 'company.created', $c, ['name']);
        });

        return back()->with('success', 'Empresa registrada.');
    }

    public function company(Request $r, int $id)
    {
        $company = Company::findOrFail($id);

        return view('platform.company', ['company' => $company, 'users' => $company->users()->orderBy('name')->paginate(15, ['*'], 'users_page'), 'drivers' => Driver::where('company_id', $id)->with('user')->paginate(15, ['*'], 'drivers_page'), 'vehicles' => Vehicle::where('company_id', $id)->paginate(15, ['*'], 'vehicles_page'), 'trips' => $company->trips()->latest('started_at')->paginate(15, ['*'], 'trips_page'), 'events' => $company->events()->latest('occurred_at')->paginate(15, ['*'], 'events_page')]);
    }

    public function companyState(Request $r, int $id)
    {
        $company = Company::findOrFail($id);
        $data = $r->validate(['status' => ['required', Rule::in(['ACTIVE', 'INACTIVE'])]]);
        DB::transaction(function () use ($r, $company, $data) {
            $company->forceFill($data)->save();
            AdministrativeAudit::record($r->user(), $company->id, 'company.state_changed', $company, ['status']);
        });

        return back()->with('success', 'Estado de empresa actualizado. Los accesos de flota y API se comprueban en cada petición.');
    }

    public function users()
    {
        return view('platform.users', ['users' => User::with('company')->orderBy('name')->paginate(20), 'companies' => Company::where('status', 'ACTIVE')->orderBy('name')->get()]);
    }

    public function storeAdmin(Request $r, AccessInvitations $invitations)
    {
        $r->merge(['email' => strtolower((string) $r->input('email'))]);
        $data = $r->validate(['name' => 'required|string|max:255', 'email' => 'required|email|max:255|unique:users,email', 'company_id' => ['required', 'integer', Rule::exists('companies', 'id')->where('status', 'ACTIVE')]]);
        $code = DB::transaction(function () use ($r, $data, $invitations) {
            $u = new User;
            $u->forceFill($data + ['role' => 'fleet_admin', 'access_status' => 'PENDING', 'password' => bin2hex(random_bytes(32))])->save();
            AdministrativeAudit::record($r->user(), $u->company_id, 'fleet_admin.created', $u, ['name', 'email']);

            return $invitations->issue($r->user(), $u);
        });

        return back()->with('activation_code', $code);
    }

    public function userAccess(Request $r, int $id, AccessInvitations $invitations)
    {
        $user = User::findOrFail($id);
        // Platform accounts are provisioned/recovered by an operator, never through another web account.
        abort_if($user->role === 'platform_admin', 403);
        $data = $r->validate(['action' => ['required', Rule::in(['invite', 'enable', 'disable'])]]);
        if ($data['action'] === 'invite') {
            return back()->with('activation_code', $invitations->issue($r->user(), $user));
        }
        if ($data['action'] === 'enable' && $user->driver && $user->driver->status !== 'ACTIVE') {
            throw ValidationException::withMessages(['access' => 'El perfil del conductor está inactivo.']);
        }
        DB::transaction(function () use ($r, $user, $data) {
            $user->forceFill(['access_status' => $data['action'] === 'enable' ? 'PENDING' : 'DISABLED'])->save();
            AccessInvitation::where('user_id', $user->id)->whereNull('consumed_at')->update(['consumed_at' => now()]);
            AdministrativeAudit::record($r->user(), $user->company_id, 'user.access_changed', $user, ['access_status']);
        });

        return back()->with('success', 'Estado de acceso actualizado. Habilitar requiere un nuevo código y contraseña.');
    }

    public function reports(Request $r)
    {
        $data = $r->validate(['from' => 'nullable|date_format:Y-m-d', 'to' => ['nullable', 'date_format:Y-m-d', ...($r->filled('from') ? ['after_or_equal:from'] : [])]]);
        $range = function ($q, string $field) use ($data) {
            if (! empty($data['from'])) {
                $q->where($field, '>=', CarbonImmutable::parse($data['from'], 'America/Lima')->startOfDay()->utc());
            }
            if (! empty($data['to'])) {
                $q->where($field, '<', CarbonImmutable::parse($data['to'], 'America/Lima')->addDay()->startOfDay()->utc());
            }
        };
        $companies = Company::withCount(['trips' => fn ($q) => $range($q, 'started_at'), 'events' => fn ($q) => $range($q, 'occurred_at')])->orderBy('name')->paginate(20)->withQueryString();
        $events = DrivingEvent::query();
        $range($events, 'occurred_at');

        return view('platform.reports', ['companies' => $companies, 'drivers' => Driver::selectRaw('company_id, count(*) as total')->groupBy('company_id')->pluck('total', 'company_id'), 'vehicles' => Vehicle::selectRaw('company_id, count(*) as total')->groupBy('company_id')->pluck('total', 'company_id'), 'events' => $events->selectRaw('type, origin, count(*) as total')->groupBy('type', 'origin')->get()]);
    }

    public function audits()
    {
        return view('platform.audits', ['audits' => Audit::with('actor')->latest('id')->paginate(30)]);
    }
}
