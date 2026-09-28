<?php

namespace App\Http\Controllers;

use App\Http\Requests\TokenRequest;
use App\Models\User;
use App\Support\ApiProblem;
use App\Support\UtcTimestamp;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;

class AuthController extends Controller
{
    public function issue(TokenRequest $request)
    {
        $user = User::where('email', strtolower($request->validated('email')))->first();
        // Perform a password check even for unknown accounts (dummy hash, not a credential).
        $hash = $user?->password ?? '$2y$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';
        if (! Hash::check($request->validated('password'), $hash) || ! $user || ! $user->capabilities()) {
            throw new ApiProblem(401, 'invalid_credentials', 'Credenciales no válidas.');
        }
        $expiresAt = now()->addDays(30);
        $token = $user->createToken('mobile-api', $user->capabilities(), $expiresAt);

        return response()->json(['data' => ['token_type' => 'Bearer', 'access_token' => $token->plainTextToken,
            'expires_at' => UtcTimestamp::format($expiresAt)]], 201)->header('Cache-Control', 'no-store');
    }

    public function me(Request $request)
    {
        $user = $request->user();

        return response()->json(['data' => ['user' => ['id' => $user->id, 'name' => $user->name, 'email' => $user->email],
            'company' => ['id' => $user->company_id, 'name' => $user->company->name],
            'capabilities' => array_values(array_filter($user->capabilities(), fn ($ability) => $user->tokenCan($ability)))]]);
    }

    public function revoke(Request $request)
    {
        $request->user()->currentAccessToken()->delete();

        return response()->noContent();
    }
}
