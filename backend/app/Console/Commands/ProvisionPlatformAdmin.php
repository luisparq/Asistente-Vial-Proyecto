<?php

namespace App\Console\Commands;

use App\Models\User;
use App\Services\AdministrativeAudit;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;

class ProvisionPlatformAdmin extends Command
{
    protected $signature = 'platform:provision';

    protected $description = 'Crear una cuenta global explícita, sin modificar cuentas existentes';

    public function handle(): int
    {
        $this->info('Administrador global sin empresa.');
        $data = ['name' => $this->ask('Nombre'), 'email' => strtolower((string) $this->ask('Email')), 'password' => $this->secret('Contraseña (mínimo 12 caracteres)'), 'password_confirmation' => $this->secret('Repite la contraseña')];
        $validation = Validator::make($data, ['name' => 'required|string|max:255', 'email' => 'required|email|max:255|unique:users,email', 'password' => 'required|string|min:12|max:255|confirmed']);
        if ($validation->fails()) {
            foreach ($validation->errors()->all() as $error) {
                $this->error($error);
            }

            return self::FAILURE;
        }
        DB::transaction(function () use ($data) {
            $u = new User;
            unset($data['password_confirmation']);
            $u->forceFill($data + ['company_id' => null, 'role' => 'platform_admin', 'access_status' => 'ACTIVE'])->save();
            AdministrativeAudit::record($u, null, 'platform_admin.provisioned', $u, ['name', 'email']);
        });
        $this->info('Cuenta creada. Accede en /admin/login. No se emitieron tokens API.');

        return self::SUCCESS;
    }
}
