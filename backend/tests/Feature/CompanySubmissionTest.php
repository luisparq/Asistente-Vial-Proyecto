<?php

namespace Tests\Feature;

use App\Models\Company;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/** TEST ONLY: in-memory SQLite; never creates companies in the local database. */
class CompanySubmissionTest extends TestCase
{
    use RefreshDatabase;

    public function test_one_submission_creates_one_company_and_repeat_does_not_duplicate_after_redirect(): void
    {
        $admin = User::factory()->create(['role' => 'platform_admin', 'company_id' => null]);
        $this->actingAs($admin);
        $token = $this->get('/admin/companies')->assertOk()->viewData('submissionToken');
        $before = Company::count();
        $payload = ['name' => 'TEST ONLY company submission', 'submission_token' => $token];
        $this->post('/admin/companies', $payload)->assertRedirect()->assertSessionHasNoErrors();
        $this->assertSame($before + 1, Company::count());
        $this->get('/admin/companies')->assertOk();
        $this->post('/admin/companies', $payload)->assertRedirect()->assertSessionHasNoErrors();
        $this->assertSame($before + 1, Company::count());
        $this->assertDatabaseCount('administrative_audits', 1);
        $this->assertSame(10, app('router')->getRoutes()->getByName('platform.companies.store')->locksFor());
    }

    public function test_missing_unissued_or_reused_modified_submission_creates_no_extra_company(): void
    {
        $admin = User::factory()->create(['role' => 'platform_admin', 'company_id' => null]);
        $this->actingAs($admin);
        $this->post('/admin/companies', ['name' => 'TEST ONLY'])->assertSessionHasErrors('submission_token');
        $this->post('/admin/companies', ['name' => 'TEST ONLY', 'submission_token' => '00000000-0000-4000-8000-000000000000'])->assertSessionHasErrors('name');
        $this->assertDatabaseCount('companies', 0);
        $token = $this->get('/admin/companies')->viewData('submissionToken');
        $this->post('/admin/companies', ['name' => 'TEST ONLY', 'submission_token' => $token])->assertSessionHasNoErrors();
        $this->post('/admin/companies', ['name' => 'TEST ONLY changed', 'submission_token' => $token])->assertSessionHasErrors('name');
        $this->assertDatabaseCount('companies', 1);
    }
}
