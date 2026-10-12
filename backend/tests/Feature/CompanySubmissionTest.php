<?php
namespace Tests\Feature;
use App\Models\{Company,User,AccessInvitation};
use App\Services\AccessInvitations;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Validation\ValidationException;
use Tests\TestCase;
/** TEST fixtures only: isolated in-memory SQLite, never the local business database. */
class CompanySubmissionTest extends TestCase {
    use RefreshDatabase;
    private function admin(): User { return User::factory()->create(['role'=>'platform_admin','company_id'=>null]); }
    private function start(): string {
        $response=$this->get('/admin/companies/create')->assertRedirect();
        parse_str(parse_url($response->headers->get('Location'),PHP_URL_QUERY),$query);
        return $query['submission_token'];
    }
    private function complete(string $token,string $email='test-onboarding@example.invalid'): void {
        foreach ([1=>['name'=>'TEST ONLY company','trade_name'=>'TEST ONLY trade'],2=>['admin_name'=>'TEST ONLY responsible','admin_email'=>$email],3=>['status'=>'ACTIVE']] as $step=>$data)
            $this->post('/admin/companies/registration',$data+['submission_token'=>$token,'step'=>$step])->assertRedirect()->assertSessionHasNoErrors();
    }
    public function test_four_steps_create_company_account_invitation_atomically_and_retry_once(): void {
        $this->actingAs($this->admin());$token=$this->start();$this->complete($token);
        $this->get('/admin/companies/create?submission_token='.$token.'&step=4')->assertOk()->assertSee('TEST ONLY responsible');
        $this->assertDatabaseCount('companies',0);$this->assertDatabaseCount('users',1);
        $this->post('/admin/companies',['submission_token'=>$token])->assertRedirect()->assertSessionHas('activation_code')->assertSessionHasNoErrors();
        $company=Company::first();$user=User::where('role','fleet_admin')->first();
        $this->assertSame($company->id,$user->company_id);$this->assertSame('PENDING',$user->access_status);$this->assertSame('TEST ONLY trade',$company->trade_name);
        $this->assertDatabaseCount('access_invitations',1);$this->assertDatabaseCount('administrative_audits',3);
        $code=session('activation_code');$this->assertSame(hash('sha256',$code),AccessInvitation::first()->token_hash);
        $this->get('/admin/companies/'.$company->id)->assertOk();
        $this->post('/admin/companies',['submission_token'=>$token])->assertRedirect('/admin/companies/'.$company->id)->assertSessionMissing('activation_code');
        $this->assertDatabaseCount('companies',1);$this->assertDatabaseCount('users',2);
        $this->assertSame(10,app('router')->getRoutes()->getByName('platform.companies.store')->locksFor());
        $this->post('/access',['activation_code'=>$code,'password'=>'TEST-only-password-2026','password_confirmation'=>'TEST-only-password-2026'])->assertRedirect()->assertSessionHasNoErrors();
        $this->assertSame('ACTIVE',$user->fresh()->access_status);
    }
    public function test_invalid_steps_or_email_collision_leave_no_partial_records(): void {
        $this->actingAs($this->admin());$token=$this->start();
        $this->post('/admin/companies',['submission_token'=>$token])->assertSessionHasErrors('registration');
        $this->post('/admin/companies/registration',['submission_token'=>$token,'step'=>1,'name'=>''])->assertSessionHasErrors('name');
        $this->assertDatabaseCount('companies',0);
        $this->complete($token);
        User::factory()->create(['role'=>'platform_admin','company_id'=>null,'email'=>'test-onboarding@example.invalid']);
        $this->post('/admin/companies',['submission_token'=>$token])->assertSessionHasErrors('admin_email');
        $this->assertDatabaseCount('companies',0);$this->assertDatabaseCount('access_invitations',0);$this->assertDatabaseCount('administrative_audits',0);
        $this->post('/admin/companies',['submission_token'=>'00000000-0000-4000-8000-000000000000'])->assertSessionHasErrors('registration');
    }
    public function test_invitation_failure_rolls_back_company_account_and_audit(): void {
        $this->actingAs($this->admin());$token=$this->start();$this->complete($token);
        $this->mock(AccessInvitations::class)->shouldReceive('issue')->once()->andThrow(ValidationException::withMessages(['access'=>'TEST failure']));
        $this->post('/admin/companies',['submission_token'=>$token])->assertSessionHasErrors('access');
        $this->assertDatabaseCount('companies',0);$this->assertDatabaseCount('users',1);$this->assertDatabaseCount('access_invitations',0);$this->assertDatabaseCount('administrative_audits',0);
    }
    public function test_existing_companies_accounts_and_profile_remain_accessible_without_drivers_in_directory(): void {
        $platform=$this->admin();$fleet=User::factory()->create(['role'=>'fleet_admin']);
        $driver=User::factory()->create(['role'=>'driver','company_id'=>$fleet->company_id,'name'=>'TEST ONLY excluded driver']);
        $this->actingAs($platform)->get('/admin/companies')->assertOk()->assertSee($fleet->email);
        $this->get('/admin/accounts')->assertOk()->assertSee($fleet->email)->assertDontSee($driver->name)->assertDontSee($platform->email);
        $this->get('/admin/profile')->assertOk()->assertSee($platform->email);
        $this->get('/admin/companies/'.$fleet->company_id)->assertOk()->assertSee($fleet->email);
        $this->get('/admin/companies/'.$fleet->company_id.'/accounts')->assertOk()->assertSee($fleet->email)->assertDontSee($driver->email);
        $this->put('/admin/companies/'.$fleet->company_id,['name'=>'TEST updated company','trade_name'=>null])->assertRedirect();
        $this->assertSame('TEST updated company',$fleet->company->fresh()->name);$this->assertSame($fleet->company_id,$driver->fresh()->company_id);
        $this->actingAs($fleet)->get('/admin/companies')->assertForbidden();
        $this->post('/admin/companies',['submission_token'=>'00000000-0000-4000-8000-000000000000'])->assertForbidden();
        $this->get('/admin/accounts')->assertForbidden();
    }
    public function test_account_actions_are_contextual_scoped_and_preserve_other_company(): void {
        $platform=$this->admin();$a=User::factory()->create(['role'=>'fleet_admin','access_status'=>'ACTIVE']);$b=User::factory()->create(['role'=>'fleet_admin']);
        $base='/admin/companies/'.$a->company_id.'/accounts/';$this->actingAs($platform);
        $this->get('/admin/companies/'.$a->company_id.'/accounts')->assertOk()->assertSee('Deshabilitar acceso')->assertDontSee('Permitir nueva activación');
        $this->post($base.$b->id.'/access',['action'=>'disable'])->assertNotFound();
        $this->post($base.$a->id.'/access',['action'=>'invite'])->assertSessionHasErrors('access');
        $this->post($base.$a->id.'/access',['action'=>'disable'])->assertRedirect();$this->assertSame('DISABLED',$a->fresh()->access_status);
        $this->get('/admin/companies/'.$a->company_id.'/accounts')->assertSee('Permitir nueva activación')->assertDontSee('Generar código de activación');
        $this->post($base.$a->id.'/access',['action'=>'enable'])->assertRedirect();$this->assertSame('PENDING',$a->fresh()->access_status);
        $this->post($base.$a->id.'/access',['action'=>'invite'])->assertRedirect()->assertSessionHas('activation_code');
        $this->assertSame('ACTIVE',$b->fresh()->access_status);
        $this->post('/admin/users/'.$platform->id.'/access',['action'=>'disable'])->assertForbidden();
    }
}
