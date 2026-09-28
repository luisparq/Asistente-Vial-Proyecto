<?php

namespace App\Http\Requests;

class TokenRequest extends ContractRequest
{
    public function rules(): array
    {
        return ['email' => ['required', 'string', 'email', 'max:255'], 'password' => ['required', 'string', 'max:1024']];
    }
}
