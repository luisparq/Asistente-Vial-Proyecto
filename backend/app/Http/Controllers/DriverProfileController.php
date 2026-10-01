<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;

class DriverProfileController extends Controller
{
    public function show(Request $request)
    {
        $user = $request->user();
        abort_unless(in_array($user->role, ['driver', 'member'], true) && $user->permits('trips:read'), 403);
        $driver = $user->driver()->where('company_id', $user->company_id)->with(['emergencyContact', 'documents.type', 'currentAssignment.vehicle'])->firstOrFail();
        $contact = $driver->emergencyContact;
        $vehicle = $driver->currentAssignment?->vehicle;

        return response()->json(['data' => [
            'name' => $driver->full_name, 'email' => $user->email, 'status' => $driver->status, 'access_status' => $user->access_status,
            'emergency_contact' => $contact ? ['name' => $contact->name, 'relationship' => $contact->relationship, 'phone' => $contact->phone] : null,
            'documents' => $driver->documents->map(fn ($d) => ['id' => $d->id, 'type' => $d->type->code, 'type_name' => $d->type->name, 'review_status' => $d->review_status, 'expires_on' => $d->expires_on?->format('Y-m-d'), 'expired' => $d->expired()])->values(),
            'vehicle' => $vehicle ? ['plate' => $vehicle->plate, 'brand' => $vehicle->brand, 'model' => $vehicle->model, 'status' => $vehicle->status] : null,
        ]])->header('Cache-Control', 'private, no-store');
    }
}
