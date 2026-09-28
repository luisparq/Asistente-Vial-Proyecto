<?php

namespace App\Domain;

enum DataOrigin: string
{
    case SIMULATED = 'SIMULATED';
    // Reserved vocabulary; writes are disabled until the physical pipeline is approved.
    case SENSOR = 'SENSOR';
}
