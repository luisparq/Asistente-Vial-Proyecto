<?php

namespace App\Http\Controllers;

use App\Models\Driver;
use App\Models\DocumentType;
use App\Models\DrivingEvent;
use App\Models\Trip;
use App\Services\DriverScores;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;

class DriverProfileController extends Controller
{
    private function driver(Request $request): Driver
    {
        $user = $request->user();
        abort_unless(in_array($user->role, ['driver', 'member'], true) && $user->permits('trips:read'), 403);

        return $user->driver()->where('company_id', $user->company_id)->firstOrFail();
    }

    public function photo(Request $request)
    {
        $driver = $this->driver($request);
        abort_unless($driver->photo_path && Storage::disk('driver_documents')->exists($driver->photo_path), 404);

        return Storage::disk('driver_documents')->response($driver->photo_path, null, [
            'Cache-Control' => 'private, no-store',
            'Content-Type' => str_ends_with($driver->photo_path, '.png') ? 'image/png' : 'image/jpeg',
        ]);
    }

    public function update(Request $request)
    {
        $driver = $this->driver($request);
        $data = $request->validate([
            'phone' => 'nullable|string|max:40',
            'address' => 'nullable|string|max:255',
            'photo' => 'nullable|image|mimes:jpg,jpeg,png|extensions:jpg,jpeg,png|max:2048|dimensions:max_width=5000,max_height=5000',
        ]);
        $file = $data['photo'] ?? null;
        unset($data['photo']);
        $path = $file?->store($driver->company_id.'/profiles', 'driver_documents');
        abort_if($file && ! $path, 500);
        try {
            $previous = DB::transaction(function () use ($driver, $data, $path) {
                $current = Driver::query()->lockForUpdate()->findOrFail($driver->id);
                $previous = $current->photo_path;
                $current->update($path ? $data + ['photo_path' => $path] : $data);

                return $previous;
            });
        } catch (\Throwable $error) {
            if ($path) {
                Storage::disk('driver_documents')->delete($path);
            }
            throw $error;
        }
        if ($path && $previous) {
            Storage::disk('driver_documents')->delete($previous);
        }

        return response()->noContent();
    }

    public function show(Request $request)
    {
        $user = $request->user();
        $driver = $this->driver($request)->load(['emergencyContact', 'documents.type', 'currentAssignment.vehicle']);
        $month = now('America/Lima')->toImmutable()->startOfMonth();
        $tripIds = Trip::where('company_id', $driver->company_id)
            ->where(fn ($q) => $q->where('driver_id', $driver->id)
                ->orWhere(fn ($q) => $q->whereNull('driver_id')->where('created_by_user_id', $user->id)))
            ->select('id');
        $counts = DrivingEvent::where('company_id', $driver->company_id)->whereIn('trip_id', $tripIds)
            ->where('origin', 'SENSOR')->where('occurred_at', '>=', $month->utc())
            ->where('occurred_at', '<', $month->addMonth()->utc())
            ->selectRaw('type, COUNT(*) AS total')->groupBy('type')->pluck('total', 'type');
        $missing = DocumentType::whereIn('code', ['DRIVING_LICENSE', 'DRIVER_CREDENTIAL', 'NATIONAL_ID', 'DRIVER_CONTRACT'])
            ->whereNotIn('id', $driver->documents->pluck('document_type_id'))->orderBy('name')->pluck('name');
        $contact = $driver->emergencyContact;
        $vehicle = $driver->currentAssignment?->vehicle;

        return response()->json(['data' => [
            'name' => $driver->full_name, 'email' => $user->email, 'status' => $driver->status, 'access_status' => $user->access_status,
            'phone' => $driver->phone, 'address' => $driver->address, 'has_photo' => (bool) $driver->photo_path,
            'missing_documents' => $missing,
            'monthly_maneuvers' => (object) $counts->all(),
            'monthly_score' => $counts->isEmpty() ? null : max(0, 100 - DriverScores::PENALTY * $counts->sum()),
            'emergency_contact' => $contact ? ['name' => $contact->name, 'relationship' => $contact->relationship, 'phone' => $contact->phone] : null,
            'documents' => $driver->documents->map(fn ($d) => ['id' => $d->id, 'type' => $d->type->code, 'type_name' => $d->type->name, 'review_status' => $d->review_status, 'expires_on' => $d->expires_on?->format('Y-m-d'), 'expired' => $d->expired()])->values(),
            'vehicle' => $vehicle ? ['plate' => $vehicle->plate, 'brand' => $vehicle->brand, 'model' => $vehicle->model, 'status' => $vehicle->status] : null,
        ]])->header('Cache-Control', 'private, no-store');
    }
}
