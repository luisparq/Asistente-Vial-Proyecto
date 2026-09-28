<?php

namespace App\Domain;

enum DrivingEventType: string
{
    case HARSH_BRAKING = 'HARSH_BRAKING';
    case HARSH_ACCELERATION = 'HARSH_ACCELERATION';
    case SHARP_TURN = 'SHARP_TURN';
}
