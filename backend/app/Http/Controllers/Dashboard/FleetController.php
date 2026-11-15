<?php

namespace App\Http\Controllers\Dashboard;

use App\Http\Controllers\Controller;
use App\Models\AccessInvitation;
use App\Models\Company;
use App\Models\DocumentType;
use App\Models\Driver;
use App\Models\DriverDocument;
use App\Models\DrivingEvent;
use App\Models\EmergencyContact;
use App\Models\Trip;
use App\Models\User;
use App\Models\Vehicle;
use App\Services\AccessInvitations;
use App\Services\AdministrativeAudit;
use App\Services\DriverScores;
use App\Services\VehicleAssignments;
use Illuminate\Http\Request;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Arr;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class FleetController extends Controller
{
    private const REGISTRATION_DOCUMENTS = [
        'DRIVING_LICENSE' => 'Licencia de conducir',
        'DRIVER_CREDENTIAL' => 'Credencial del conductor',
        'NATIONAL_ID' => 'DNI vigente',
        'DRIVER_CONTRACT' => 'Contrato',
    ];

    private function driver(Request $r, int $id): Driver
    {
        return Driver::where('company_id', $r->user()->company_id)->findOrFail($id);
    }

    private function vehicle(Request $r, int $id): Vehicle
    {
        return Vehicle::where('company_id', $r->user()->company_id)->findOrFail($id);
    }

    private function contactRules(): array
    {
        return ['contact_name' => 'nullable|string|max:255|required_with:contact_relationship,contact_phone', 'contact_relationship' => 'nullable|string|max:100|required_with:contact_name,contact_phone', 'contact_phone' => 'nullable|string|max:40|required_with:contact_name,contact_relationship'];
    }

    private function saveContact(Driver $driver, array $data): void
    {
        if (! empty($data['contact_name'])) {
            $contact = $driver->emergencyContact()->first() ?? new EmergencyContact;
            $contact->forceFill(['company_id' => $driver->company_id, 'driver_id' => $driver->id, 'name' => $data['contact_name'], 'relationship' => $data['contact_relationship'], 'phone' => $data['contact_phone']])->save();
        } else {
            $driver->emergencyContact()->delete();
        }
    }

    private function profileRules(): array
    {
        return [
            'address' => 'nullable|string|max:255',
            'phone' => 'nullable|string|max:40',
            'photo' => 'nullable|file|mimes:jpg,jpeg,png|extensions:jpg,jpeg,png|max:2048',
            'documents' => 'nullable|array:'.implode(',', array_keys(self::REGISTRATION_DOCUMENTS)),
            'documents.*' => 'nullable|file|mimes:pdf,jpg,jpeg,png|extensions:pdf,jpg,jpeg,png|max:5120',
        ] + $this->contactRules();
    }

    private function saveProfileFiles(Request $request, Driver $driver, array $data, array &$stored): ?string
    {
        $oldPhoto = null;
        if (isset($data['photo'])) {
            $oldPhoto = $driver->photo_path;
            $stored[] = $path = $data['photo']->store($driver->company_id.'/profiles', 'driver_documents');
            $driver->update(['photo_path' => $path]);
        }

        if (! empty($data['documents'])) {
            $types = DocumentType::whereIn('code', array_keys($data['documents']))->pluck('id', 'code');
            foreach (array_filter($data['documents']) as $code => $file) {
                $stored[] = $path = $file->store((string) $driver->company_id, 'driver_documents');
                $this->recordDocument($request, $driver, $file, $path, ['document_type_id' => $types[$code]]);
            }
        }

        return $oldPhoto;
    }

    public function drivers(Request $r, DriverScores $scores)
    {
        return view('fleet.drivers', ['drivers' => $scores->query($r->user()->company_id)
            ->with(['user', 'currentAssignment.vehicle'])->withCount([
                'documents',
                'documents as pending_documents_count' => fn ($documents) => $documents->where('review_status', 'PENDING'),
            ])->orderBy('full_name')->orderBy('id')->paginate(20)]);
    }

    public function createDriver(Request $r)
    {
        return view('fleet.driver-form', ['driver' => null, 'documentLabels' => self::REGISTRATION_DOCUMENTS,
            'vehicles' => Vehicle::where('company_id', $r->user()->company_id)->where('status', 'ACTIVE')->whereDoesntHave('currentAssignment')->orderBy('plate')->get()]);
    }

    public function storeDriver(Request $r, AccessInvitations $invitations, VehicleAssignments $assignments)
    {
        $r->merge(['email' => strtolower((string) $r->input('email'))]);
        $data = $r->validate(['full_name' => 'required|string|max:255', 'email' => 'required|email|max:255|unique:users,email', 'vehicle_id' => ['nullable', 'integer', Rule::exists('vehicles', 'id')->where('company_id', $r->user()->company_id)]] + $this->profileRules());
        $stored = [];
        try {
            [$driver, $code] = DB::transaction(function () use ($r, $data, $invitations, $assignments, &$stored) {
                $user = new User;
                $user->forceFill(['company_id' => $r->user()->company_id, 'name' => $data['full_name'], 'email' => $data['email'], 'role' => 'driver', 'access_status' => 'PENDING', 'password' => bin2hex(random_bytes(32))])->save();
                $driver = new Driver;
                $driver->forceFill(['company_id' => $user->company_id, 'user_id' => $user->id, 'full_name' => $data['full_name'], 'status' => 'ACTIVE',
                    'address' => $data['address'] ?? null, 'phone' => $data['phone'] ?? null])->save();
                $this->saveContact($driver, $data);
                if (! empty($data['vehicle_id'])) {
                    $assignments->assign($r->user(), $driver, (int) $data['vehicle_id']);
                }
                $this->saveProfileFiles($r, $driver, $data, $stored);
                AdministrativeAudit::record($r->user(), $user->company_id, 'driver.created', $driver, ['full_name', 'email', 'contact', 'address', 'phone']);

                return [$driver, $invitations->issue($r->user(), $user)];
            });
        } catch (\Throwable $e) {
            Storage::disk('driver_documents')->delete($stored);
            throw $e;
        }

        return redirect()->route('dashboard.drivers.show', $driver->id)->with('activation_code', $code)->with('activation_for_driver', true);
    }

    public function showDriver(Request $r, int $id, DriverScores $scores)
    {
        $driver = $scores->query($r->user()->company_id)
            ->with(['user', 'emergencyContact', 'documents.type', 'currentAssignment.vehicle'])->findOrFail($id);
        $history = Trip::where('company_id', $driver->company_id)->where(function ($trips) use ($driver) {
            $trips->where('driver_id', $driver->id)->orWhere(fn ($legacy) => $legacy
                ->whereNull('driver_id')->where('created_by_user_id', $driver->user_id));
        });
        $events = DrivingEvent::where('company_id', $driver->company_id)->whereIn('trip_id', (clone $history)->select('id'))
            ->with('trip')->latest('occurred_at')->orderByDesc('id')->paginate(10, ['*'], 'events_page')->withQueryString();
        $trips = $history->with('vehicle')->withCount('events')->latest('started_at')->orderByDesc('id')
            ->paginate(10, ['*'], 'trips_page')->withQueryString();

        return view('fleet.driver', ['driver' => $driver, 'trips' => $trips, 'events' => $events, 'assignments' => $driver->assignments()->with('vehicle')->latest('started_at')->paginate(10, ['*'], 'assignments_page')->withQueryString(), 'types' => DocumentType::all(), 'vehicles' => Vehicle::where('company_id', $driver->company_id)->where('status', 'ACTIVE')->get()]);
    }

    public function driverPhoto(Request $r, int $id)
    {
        $driver = $this->driver($r, $id);
        abort_unless($driver->photo_path && Storage::disk('driver_documents')->exists($driver->photo_path), 404);
        $mime = str_ends_with($driver->photo_path, '.png') ? 'image/png' : 'image/jpeg';

        return Storage::disk('driver_documents')->response($driver->photo_path, null, ['Content-Type' => $mime]);
    }

    public function editDriver(Request $r, int $id)
    {
        return view('fleet.driver-form', ['driver' => $this->driver($r, $id)->load(['user', 'emergencyContact']), 'vehicles' => collect(), 'documentLabels' => self::REGISTRATION_DOCUMENTS]);
    }

    public function updateDriver(Request $r, int $id, VehicleAssignments $assignments)
    {
        $driver = $this->driver($r, $id);
        $data = $r->validate(['full_name' => 'required|string|max:255', 'status' => ['required', Rule::in(['ACTIVE', 'INACTIVE'])]] + $this->profileRules());
        $stored = [];
        try {
            $oldPhoto = DB::transaction(function () use ($r, $driver, $data, $assignments, &$stored) {
                Company::whereKey($driver->company_id)->update(['name' => DB::raw('name')]);
                $driver->refresh();
                $driver->update(Arr::only($data, ['full_name', 'status', 'address', 'phone']));
                $driver->user->update(['name' => $data['full_name']]);
                $this->saveContact($driver, $data);
                if ($data['status'] === 'INACTIVE') {
                    $assignments->assign($r->user(), $driver, null);
                    $driver->user->forceFill(['access_status' => 'DISABLED'])->save();
                    AccessInvitation::where('user_id', $driver->user_id)->whereNull('consumed_at')->update(['consumed_at' => now()]);
                }
                $oldPhoto = $this->saveProfileFiles($r, $driver, $data, $stored);
                AdministrativeAudit::record($r->user(), $driver->company_id, 'driver.updated', $driver, ['full_name', 'status', 'contact', 'access_status', 'address', 'phone']);

                return $oldPhoto;
            });
        } catch (\Throwable $e) {
            Storage::disk('driver_documents')->delete($stored);
            throw $e;
        }
        if ($oldPhoto) {
            Storage::disk('driver_documents')->delete($oldPhoto);
        }

        return redirect()->route('dashboard.drivers.show', $id)->with('success', 'Perfil actualizado.');
    }

    public function access(Request $r, int $id, AccessInvitations $invitations)
    {
        $driver = $this->driver($r, $id);
        $data = $r->validate(['action' => ['required', Rule::in(['invite', 'enable', 'disable'])]]);
        $expected = ['invite' => 'PENDING', 'disable' => 'ACTIVE', 'enable' => 'DISABLED'];
        if ($driver->user->access_status !== $expected[$data['action']]) {
            throw ValidationException::withMessages(['access' => 'Esta acción no corresponde al estado actual del acceso.']);
        }
        if ($data['action'] === 'invite') {
            return back()->with('activation_code', $invitations->issue($r->user(), $driver->user))->with('activation_for_driver', true);
        }
        if ($data['action'] === 'enable' && $driver->status !== 'ACTIVE') {
            throw ValidationException::withMessages(['access' => 'Activa primero el perfil.']);
        }
        DB::transaction(function () use ($r, $driver, $data) {
            // Enabling always requires a new activation; an unknown initial password never becomes usable.
            $driver->user->forceFill(['access_status' => $data['action'] === 'enable' ? 'PENDING' : 'DISABLED'])->save();
            AccessInvitation::where('user_id', $driver->user_id)->whereNull('consumed_at')->update(['consumed_at' => now()]);
            AdministrativeAudit::record($r->user(), $driver->company_id, 'user.access_changed', $driver->user, ['access_status']);
        });

        return back()->with('success', $data['action'] === 'enable'
            ? 'Acceso pendiente. Genera un código para que el conductor se active desde Android.'
            : 'Acceso deshabilitado. El conductor no puede iniciar sesión en la aplicación.');
    }

    public function assign(Request $r, int $id, VehicleAssignments $assignments)
    {
        $driver = $this->driver($r, $id);
        $data = $r->validate(['vehicle_id' => 'nullable|integer']);
        $assignments->assign($r->user(), $driver, empty($data['vehicle_id']) ? null : (int) $data['vehicle_id']);

        return back()->with('success', 'Asignación actualizada; historial conservado.');
    }

    public function upload(Request $r, int $id)
    {
        $driver = $this->driver($r, $id);
        $data = $r->validate(['document_type_id' => 'required|integer|exists:document_types,id', 'reference' => 'nullable|string|max:255', 'issued_on' => 'nullable|date_format:Y-m-d', 'expires_on' => ['nullable', 'date_format:Y-m-d', ...($r->filled('issued_on') ? ['after_or_equal:issued_on'] : [])], 'document' => 'required|file|mimes:pdf,jpg,jpeg,png|extensions:pdf,jpg,jpeg,png|max:5120']);
        $file = $r->file('document');
        $path = $file->store((string) $driver->company_id, 'driver_documents');
        try {
            DB::transaction(function () use ($r, $driver, $data, $file, $path) {
                unset($data['document']);
                $this->recordDocument($r, $driver, $file, $path, $data);
            });
        } catch (\Throwable $e) {
            Storage::disk('driver_documents')->delete($path);
            throw $e;
        }

        return back()->with('success', 'Documento privado guardado. Pendiente de revisión administrativa.');
    }

    private function recordDocument(Request $r, Driver $driver, UploadedFile $file, string $path, array $data): void
    {
        $document = new DriverDocument;
        $document->forceFill($data + ['company_id' => $driver->company_id, 'driver_id' => $driver->id, 'path' => $path, 'mime' => $file->getMimeType(), 'size' => $file->getSize(), 'review_status' => 'PENDING'])->save();
        AdministrativeAudit::record($r->user(), $driver->company_id, 'document.uploaded', $document, ['document']);
    }

    public function download(Request $r, int $id)
    {
        $document = DriverDocument::where('company_id', $r->user()->company_id)->findOrFail($id);
        $extension = match ($document->mime) {
            'application/pdf' => 'pdf','image/png' => 'png',default => 'jpg'
        };

        return Storage::disk('driver_documents')->download($document->path, 'documento-'.$document->id.'.'.$extension, ['X-Content-Type-Options' => 'nosniff', 'Content-Security-Policy' => "sandbox; default-src 'none'", 'Cache-Control' => 'private, no-store']);
    }

    public function review(Request $r, int $id)
    {
        $document = DriverDocument::where('company_id', $r->user()->company_id)->findOrFail($id);
        $data = $r->validate(['review_status' => ['required', Rule::in(['PENDING', 'VERIFIED', 'REJECTED'])]]);
        DB::transaction(function () use ($r, $document, $data) {
            $document->update($data);
            AdministrativeAudit::record($r->user(), $document->company_id, 'document.reviewed', $document, ['review_status']);
        });

        return back()->with('success', 'Revisión administrativa registrada.');
    }

    public function vehicles(Request $r)
    {
        return view('fleet.vehicles', ['vehicles' => Vehicle::where('company_id', $r->user()->company_id)->with('currentAssignment.driver')->orderBy('plate')->paginate(20)]);
    }

    public function createVehicle(Request $r)
    {
        return view('fleet.vehicle-form', ['vehicle' => null, 'drivers' => Driver::where('company_id', $r->user()->company_id)
            ->where('status', 'ACTIVE')->whereDoesntHave('currentAssignment')->orderBy('full_name')->get()]);
    }

    private function vehicleData(Request $r, ?Vehicle $vehicle = null): array
    {
        $r->merge(['plate' => strtoupper((string) $r->input('plate'))]);

        return $r->validate(['plate' => ['required', 'string', 'max:30', Rule::unique('vehicles')->where('company_id', $r->user()->company_id)->ignore($vehicle?->id)], 'brand' => 'required|string|max:100', 'model' => 'required|string|max:100', 'status' => ['required', Rule::in(['ACTIVE', 'INACTIVE'])]]);
    }

    public function storeVehicle(Request $r, VehicleAssignments $assignments)
    {
        $data = $this->vehicleData($r);
        $initial = $r->validate(['driver_id' => ['nullable', 'integer', Rule::exists('drivers', 'id')->where('company_id', $r->user()->company_id)]]);
        $driver = isset($initial['driver_id']) ? $this->driver($r, $initial['driver_id']) : null;
        if ($driver && $data['status'] !== 'ACTIVE') {
            throw ValidationException::withMessages(['driver_id' => 'Activa el vehículo para asignarle un conductor.']);
        }
        $vehicle = DB::transaction(function () use ($r, $data, $driver, $assignments) {
            $v = new Vehicle;
            $v->forceFill($data + ['company_id' => $r->user()->company_id])->save();
            if ($driver) {
                $assignments->assign($r->user(), $driver, $v->id, requireUnassigned: true);
            }
            AdministrativeAudit::record($r->user(), $v->company_id, 'vehicle.created', $v, array_keys($data));

            return $v;
        });

        return redirect()->route('dashboard.vehicles.show', $vehicle->id);
    }

    public function showVehicle(Request $r, int $id)
    {
        $vehicle = $this->vehicle($r, $id)->load('currentAssignment.driver');

        return view('fleet.vehicle', ['vehicle' => $vehicle, 'assignments' => $vehicle->assignments()->with('driver')->latest('started_at')->paginate(10, ['*'], 'assignments_page'), 'trips' => $vehicle->trips()->orderByDesc('started_at')->paginate(10, ['*'], 'trips_page')]);
    }

    public function editVehicle(Request $r, int $id)
    {
        return view('fleet.vehicle-form', ['vehicle' => $this->vehicle($r, $id)]);
    }

    public function updateVehicle(Request $r, int $id)
    {
        $vehicle = $this->vehicle($r, $id);
        $data = $this->vehicleData($r, $vehicle);
        DB::transaction(function () use ($r, $vehicle, $data) {
            Company::whereKey($vehicle->company_id)->update(['name' => DB::raw('name')]);
            if ($data['status'] === 'INACTIVE' && $vehicle->currentAssignment()->exists()) {
                throw ValidationException::withMessages(['status' => 'Finaliza primero la asignación actual.']);
            }
            $vehicle->update($data);
            AdministrativeAudit::record($r->user(), $vehicle->company_id, 'vehicle.updated', $vehicle, array_keys($data));
        }, 5);

        return redirect()->route('dashboard.vehicles.show', $id);
    }
}
