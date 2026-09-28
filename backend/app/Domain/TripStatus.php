<?php

namespace App\Domain;

enum TripStatus: string
{
    case ACTIVE = 'ACTIVE';
    case COMPLETED = 'COMPLETED';
}
