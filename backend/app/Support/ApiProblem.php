<?php

namespace App\Support;

use RuntimeException;

class ApiProblem extends RuntimeException
{
    public function __construct(public readonly int $status, public readonly string $errorCode, string $message)
    {
        parent::__construct($message);
    }
}
