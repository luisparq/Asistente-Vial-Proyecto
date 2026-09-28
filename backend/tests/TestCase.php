<?php

namespace Tests;

use Illuminate\Foundation\Testing\TestCase as BaseTestCase;

abstract class TestCase extends BaseTestCase
{
    public function createApplication()
    {
        $app = parent::createApplication();
        if ($app->configurationIsCached()) {
            throw new \RuntimeException('Run php artisan config:clear before tests; never test against a cached local database configuration.');
        }

        return $app;
    }
}
