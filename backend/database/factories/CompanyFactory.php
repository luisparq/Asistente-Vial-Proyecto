<?php

namespace Database\Factories;

use Illuminate\Database\Eloquent\Factories\Factory;

class CompanyFactory extends Factory
{
    public function definition(): array
    {
        return ['name' => 'TEST ONLY company '.fake()->uuid()];
    }
}
