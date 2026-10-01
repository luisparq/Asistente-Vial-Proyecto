<?php

namespace App\Services;

use App\Models\AccessInvitation;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class AccessInvitations
{
    public function issue(User $actor, User $user): string
    {
        return DB::transaction(function () use ($actor, $user) {
            User::whereKey($user->id)->update(['access_status' => DB::raw('access_status')]);
            $user->refresh();
            if ($user->access_status === 'DISABLED') {
                throw ValidationException::withMessages(['access' => 'Primero habilita la cuenta.']);
            }
            AccessInvitation::where('user_id', $user->id)->whereNull('consumed_at')->update(['consumed_at' => now()]);
            $code = bin2hex(random_bytes(32));
            AccessInvitation::create(['company_id' => $user->company_id, 'user_id' => $user->id, 'token_hash' => hash('sha256', $code), 'expires_at' => now()->addDay()]);
            AdministrativeAudit::record($actor, $user->company_id, 'access.invitation', $user, ['password_reset']);

            return $code;
        }, 5);
    }

    public function consume(string $code, string $password): void
    {
        DB::transaction(function () use ($code, $password) {
            $hash = hash('sha256', $code);
            $claimed = AccessInvitation::where('token_hash', $hash)->whereNull('consumed_at')->where('expires_at', '>', now())->update(['consumed_at' => now()]);
            if ($claimed !== 1) {
                throw ValidationException::withMessages(['activation_code' => 'Código no válido o vencido.']);
            }
            $invitation = AccessInvitation::where('token_hash', $hash)->firstOrFail();
            $user = User::findOrFail($invitation->user_id);
            if ($user->access_status === 'DISABLED' || $user->company->status !== 'ACTIVE') {
                throw ValidationException::withMessages(['activation_code' => 'Acceso no disponible.']);
            }
            $user->forceFill(['password' => $password, 'access_status' => 'ACTIVE', 'remember_token' => Str::random(60)])->save();
            $user->tokens()->delete();
            // File sessions are invalidated by AuthenticateSession's password-hash check.
            AdministrativeAudit::record($user, $user->company_id, 'access.activated', $user, ['password_reset', 'access_status']);
        }, 5);
    }
}
