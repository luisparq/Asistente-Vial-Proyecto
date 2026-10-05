<?php

namespace App\Http\Controllers;

use App\Services\AccessInvitations;
use Illuminate\Http\Request;

class AccessController extends Controller
{
    public function create()
    {
        return view('access');
    }

    public function store(Request $request, AccessInvitations $invitations)
    {
        $data = $request->validate($this->rules());
        $invitations->consume($data['activation_code'], $data['password'], 'web');

        return redirect('/access')->with('success', 'Contraseña establecida. Ya puedes iniciar sesión en el área administrativa correspondiente.');
    }

    public function activateDriver(Request $request, AccessInvitations $invitations)
    {
        $data = $request->validate($this->rules());
        $invitations->consume($data['activation_code'], $data['password'], 'android');

        return response()->noContent()->header('Cache-Control', 'no-store');
    }

    private function rules(): array
    {
        return ['activation_code' => 'required|string|size:64|regex:/^[a-f0-9]+$/', 'password' => 'required|string|min:12|max:255|confirmed'];
    }
}
