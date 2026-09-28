<?php

namespace App\Models;

class PersonalAccessToken extends \Laravel\Sanctum\PersonalAccessToken
{
    protected static function booted(): void
    {
        static::creating(function (self $token) {
            // Bind at issuance, never to whichever company the user has later.
            $token->company_id = $token->tokenable->company_id;
        });
    }
}
