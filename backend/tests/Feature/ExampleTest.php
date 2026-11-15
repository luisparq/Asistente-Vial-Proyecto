<?php

namespace Tests\Feature;

use Tests\TestCase;

class ExampleTest extends TestCase
{
    public function test_root_redirects_to_admin_login_and_health_route_is_available(): void
    {
        $this->get('/')->assertRedirect('/admin/login');

        $this->get('/admin/login')->assertOk();
        $this->get('/up')->assertOk();
    }
}
