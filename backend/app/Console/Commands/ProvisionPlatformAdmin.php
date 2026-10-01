<?php

namespace App\Console\Commands;

use App\Models\Company;
use App\Models\User;
use App\Services\AdministrativeAudit;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;

class ProvisionPlatformAdmin extends Command
{
    protected $signature = 'platform:provision {--company-id= : Empresa real de afiliación administrativa}';

    protected $description = 'Crear una cuenta global explícita, sin modificar cuentas existentes';

    public function handle(): int
    {
        $company = Company::find($this->option('company-id'));
        if (! $company) {
            $this->error('Indica --company-id con una empresa existente.');

            return self::FAILURE;
        }
        $this->info('Empresa administrativa: '.$company->name.'. El rol tendrá acceso global.');
        $data = ['name' => $this->ask('Nombre'), 'email' => strtolower((string) $this->ask('Email')), 'password' => $this->secret('Contraseña (mínimo 12 caracteres)'), 'password_confirmation' => $this->secret('Repite la contraseña')];
        $validation = Validator::make($data, ['name' => 'required|string|max:255', 'email' => 'required|email|max:255|unique:users,email', 'password' => 'required|string|min:12|max:255|confirmed']);
        if ($validation->fails()) {
            foreach ($validation->errors()->all() as $error) {
                $this->error($error);
            }

            return self::FAILURE;
        }
        DB::transaction(function () use ($data, $company) {
            $u = new User;
            unset($data['password_confirmation']);
            $u->forceFill($data + ['company_id' => $company->id, 'role' => 'platform_admin', 'access_status' => 'ACTIVE'])->save();
            AdministrativeAudit::record($u, $company->id, 'platform_admin.provisioned', $u, ['name', 'email']);
        });
        $this->info('Cuenta creada. Accede en /platform/login. No se emitieron tokens API.');

        return self::SUCCESS;
    }
}
