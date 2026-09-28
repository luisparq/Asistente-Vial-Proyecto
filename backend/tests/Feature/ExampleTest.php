<?php

namespace Tests\Feature;

use Tests\TestCase;

class ExampleTest extends TestCase
{
    public function test_service_and_health_routes_are_available_without_frontend_assets(): void
    {
        $response = $this->get('/');

        $response->assertOk()->assertExactJson(['service' => 'Asistente Vial API', 'version' => 'v1']);
        $this->get('/up')->assertOk();
    }
}
