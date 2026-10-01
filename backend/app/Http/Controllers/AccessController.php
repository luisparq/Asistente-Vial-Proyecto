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
        $data = $request->validate(['activation_code' => 'required|string|size:64|regex:/^[a-f0-9]+$/', 'password' => 'required|string|min:12|max:255|confirmed']);
        $invitations->consume($data['activation_code'], $data['password']);

        return redirect('/access')->with('success', 'Contraseña establecida. Ya puedes iniciar sesión con tu email en Android o en el área administrativa que corresponda.');
    }
}
