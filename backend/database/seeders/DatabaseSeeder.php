<?php

namespace Database\Seeders;

use App\Domain\DrivingEventType;
use App\Domain\TripStatus;
use App\Models\Company;
use App\Models\DocumentType;
use App\Models\Driver;
use App\Models\DriverDocument;
use App\Models\DrivingEvent;
use App\Models\Trip;
use App\Models\User;
use App\Models\Vehicle;
use App\Models\VehicleAssignment;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        // 1. Empresa Principal
        $company = Company::firstOrCreate(
            ["name" => "Transportes Arequipa S.A.C."],
            [
                "status" => "ACTIVE",
            ]
        );

        // 2. Usuarios del sistema
        $adminPlatform = User::firstOrCreate(
            ["email" => "admin@platform.com"],
            [
                "name" => "Administrador Plataforma",
                "password" => bcrypt("password123"),
                "role" => "platform_admin",
                "company_id" => null,
                "access_status" => "ACTIVE",
            ]
        );

        $adminFleet = User::firstOrCreate(
            ["email" => "admin@transarequipa.com"],
            [
                "name" => "Gestor de Flota Arequipa",
                "password" => bcrypt("password123"),
                "role" => "fleet_admin",
                "company_id" => $company->id,
                "access_status" => "ACTIVE",
            ]
        );

        // 3. Usuarios Conductores
        $driverUsers = [
            User::firstOrCreate(
                ["email" => "carlos.mendoza@transarequipa.com"],
                [
                    "name" => "Carlos Mendoza",
                    "password" => bcrypt("password123"),
                    "role" => "driver",
                    "company_id" => $company->id,
                    "access_status" => "ACTIVE",
                ]
            ),
            User::firstOrCreate(
                ["email" => "luis.perez@transarequipa.com"],
                [
                    "name" => "Luis Pérez",
                    "password" => bcrypt("password123"),
                    "role" => "driver",
                    "company_id" => $company->id,
                    "access_status" => "ACTIVE",
                ]
            ),
        ];

        // 4. Conductores asociados
        $drivers = [];
        foreach ($driverUsers as $user) {
            $drivers[] = Driver::firstOrCreate(
                ["user_id" => $user->id],
                [
                    "company_id" => $company->id,
                    "full_name" => $user->name,
                    "status" => "ACTIVE",
                ]
            );
        }

        // 5. Vehículos
        $vehiclesData = [
            ["plate" => "V1A-782", "brand" => "Toyota", "model" => "Hilux"],
            ["plate" => "B3X-910", "brand" => "Hyundai", "model" => "H100"],
        ];

        $vehicles = [];
        foreach ($vehiclesData as $vData) {
            $vehicles[] = Vehicle::firstOrCreate(
                ["company_id" => $company->id, "plate" => $vData["plate"]],
                [
                    "brand" => $vData["brand"],
                    "model" => $vData["model"],
                    "status" => "ACTIVE",
                ]
            );
        }

        // 6. Asignaciones de Vehículos
        foreach ($drivers as $idx => $driver) {
            if (isset($vehicles[$idx])) {
                VehicleAssignment::firstOrCreate(
                    [
                        "company_id" => $company->id,
                        "driver_id" => $driver->id,
                        "vehicle_id" => $vehicles[$idx]->id,
                        "active_slot" => 1,
                    ],
                    [
                        "started_at" => now()->subDays(15),
                        "ended_at" => null,
                    ]
                );
            }
        }

        // 7. Documento de Licencia
        $docType = DocumentType::where("code", "DRIVING_LICENSE")->first();
        if ($docType && isset($drivers[0])) {
            DriverDocument::firstOrCreate(
                ["company_id" => $company->id, "driver_id" => $drivers[0]->id, "document_type_id" => $docType->id],
                [
                    "reference" => "Q-45678912",
                    "issued_on" => now()->subYears(2),
                    "expires_on" => now()->addYears(3),
                    "review_status" => "APPROVED",
                    "path" => "documents/licencia_carlos.pdf",
                    "mime" => "application/pdf",
                    "size" => 1024500,
                ]
            );
        }

        // 8. Viaje y Eventos Telemétricos
        $now = now();
        $trip = Trip::create([
            "company_id" => $company->id,
            "created_by_user_id" => $driverUsers[0]->id,
            "driver_id" => $drivers[0]->id,
            "vehicle_id" => $vehicles[0]->id,
            "client_trip_id" => (string) Str::uuid(),
            "started_at" => $now->copy()->subHours(2),
            "ended_at" => $now->copy()->subHour(),
            "status" => TripStatus::COMPLETED->value,
            "client_revision" => 1,
            "first_received_at" => $now->copy()->subHours(2),
            "last_changed_at" => $now->copy()->subHour(),
        ]);

        DrivingEvent::create([
            "company_id" => $company->id,
            "trip_id" => $trip->id,
            "client_event_id" => (string) Str::uuid(),
            "type" => DrivingEventType::HARSH_BRAKING->value,
            "origin" => "APP",
            "occurred_at" => $now->copy()->subHours(1)->subMinutes(45),
            "first_received_at" => $now->copy()->subHours(1)->subMinutes(45),
        ]);

        DrivingEvent::create([
            "company_id" => $company->id,
            "trip_id" => $trip->id,
            "client_event_id" => (string) Str::uuid(),
            "type" => DrivingEventType::HARSH_ACCELERATION->value,
            "origin" => "APP",
            "occurred_at" => $now->copy()->subHours(1)->subMinutes(30),
            "first_received_at" => $now->copy()->subHours(1)->subMinutes(30),
        ]);
    }
}