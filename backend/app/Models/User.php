<?php

namespace App\Models;

// use Illuminate\Contracts\Auth\MustVerifyEmail;
use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

class User extends Authenticatable
{
    protected $attributes = ['access_status' => 'ACTIVE'];

    /** @use HasFactory<UserFactory> */
    use HasApiTokens, HasFactory, Notifiable;

    public function company(): BelongsTo
    {
        return $this->belongsTo(Company::class);
    }

    public function trips(): HasMany
    {
        return $this->hasMany(Trip::class, 'created_by_user_id');
    }

    public function capabilities(): array
    {
        if (! $this->accessEnabled()) {
            return [];
        }

        return match ($this->role) {
            'member', 'driver' => ['trips:read', 'trips:write'],
            'fleet_admin' => ['trips:read', 'trips:write', 'trips:read-company'],
            default => [],
        };
    }

    public function accessEnabled(): bool
    {
        return ($this->access_status ?? 'ACTIVE') === 'ACTIVE'
            && ($this->role === 'platform_admin' || $this->company()->where('status', 'ACTIVE')->exists());
    }

    public function driver()
    {
        return $this->hasOne(Driver::class);
    }

    public function permits(string $ability): bool
    {
        return in_array($ability, $this->capabilities(), true) && $this->tokenCan($ability);
    }

    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'name',
        'email',
        'password',
    ];

    /**
     * The attributes that should be hidden for serialization.
     *
     * @var list<string>
     */
    protected $hidden = [
        'password',
        'remember_token',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
        ];
    }
}
