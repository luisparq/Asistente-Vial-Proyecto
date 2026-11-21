<?php

namespace App\Domain;

enum DataOrigin: string
{
    case SIMULATED = 'SIMULATED';
    case SENSOR = 'SENSOR';
}
