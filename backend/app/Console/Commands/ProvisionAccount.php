<?php

namespace App\Console\Commands;

use App\Models\Company;
use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rules\Password;

class ProvisionAccount extends Command
{
    protected $signature = 'account:provision {--company-id= : Existing company ID; omit to create one}';

    protected $description = 'Provision an actual account interactively; no default identities or credentials';

    public function handle(): int
    {
        if (! $this->input->isInteractive()) {
            $this->error('Se requiere entrada interactiva del operador.');

            return self::FAILURE;
        }
        $company = $this->option('company-id') ? Company::findOrFail($this->option('company-id')) : null;
        $data = ['company_name' => $company?->name ?? $this->ask('Nombre real de la empresa'),
            'name' => $this->ask('Nombre del usuario'), 'email' => strtolower((string) $this->ask('Email del usuario')),
            'role' => $this->choice('Permisos de la cuenta', ['member', 'fleet_admin'], 0),
            'password' => $this->secret('Contraseña (mínimo 12 caracteres)'),
            'password_confirmation' => $this->secret('Repetir contraseña')];
        $validator = Validator::make($data, ['company_name' => 'required|string|max:255', 'name' => 'required|string|max:255',
            'email' => 'required|email|max:255|unique:users,email', 'role' => 'required|in:member,fleet_admin',
            'password' => ['required', 'confirmed', Password::min(12)]]);
        if ($validator->fails()) {
            foreach ($validator->errors()->all() as $error) {
                $this->error($error);
            }

            return self::FAILURE;
        }
        DB::transaction(function () use ($company, $data) {
            $company ??= Company::create(['name' => $data['company_name']]);
            $user = new User;
            $user->forceFill(['company_id' => $company->id, 'name' => $data['name'], 'email' => $data['email'],
                'password' => $data['password'], 'role' => $data['role']])->save();
        });
        $this->info('Cuenta creada. No se ha emitido ni mostrado ningún token.');

        return self::SUCCESS;
    }
}
