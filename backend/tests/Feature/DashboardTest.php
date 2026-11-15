<?php

namespace Tests\Feature;

use App\Models\Company;
use App\Models\Driver;
use App\Models\DrivingEvent;
use App\Models\Trip;
use App\Models\User;
use App\Models\Vehicle;
use App\Support\DashboardDisplay;
use Illuminate\Foundation\Http\Middleware\ValidateCsrfToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;
use Tests\TestCase;

/** All records are TEST ONLY fixtures in phpunit's isolated SQLite database. */
class DashboardTest extends TestCase
{
    use RefreshDatabase;

    public function test_event_location_is_visible_in_trip_and_events_without_leaking_other_company(): void
    {
        $admin = $this->admin();
        $trip = $this->trip($admin);
        // Synthetic TEST GPS fixtures, not physical observations.
        $located = $this->event($trip, ['latitude' => -16.1234567, 'longitude' => -71.1234567,
            'location_origin' => 'GPS_NEO6M', 'gps_utc_seconds' => 8825,
            'gps_received_at' => '2026-09-30 02:27:04.900', 'gps_age_ms' => 557]);
        $this->event($trip); // Historical / unlocated event remains supported.
        $foreign = $this->event($this->trip($this->admin()), ['latitude' => -17.9876543,
            'longitude' => -72.9876543, 'location_origin' => 'GPS_NEO6M']);
        $this->actingAs($admin);
        foreach (['/dashboard/events', '/dashboard/trips/'.$trip->client_trip_id] as $path) {
            $this->get($path)->assertOk()->assertSee('-16.1234567')->assertSee('-71.1234567')
                ->assertSee('GPS_NEO6M')->assertSee('557 ms')->assertSee('02:27:05')
                ->assertSee('29/09/2026 21:27:05')->assertSee('Ubicación no disponible')
                ->assertSee('Dato de prueba')->assertSee($located->client_event_id)
                ->assertDontSee($foreign->client_event_id)->assertDontSee('-17.9876543');
        }
        $this->assertSame('2026-09-30 02:27:04.900', $located->fresh()->getRawOriginal('gps_received_at'));
    }

    public function test_incident_map_is_scoped_filters_valid_coordinates_and_existing_vehicle_association(): void
    {
        $admin=$this->admin();
        $vehicle=\App\Models\Vehicle::forceCreate(['company_id'=>$admin->company_id,'plate'=>'TEST-MAP','brand'=>'TEST','model'=>'TEST']);
        $trip=$this->trip($admin,['vehicle_id'=>$vehicle->id]);
        // TEST fixtures only in isolated SQLite; never inserted into application data.
        $event=$this->event($trip,['latitude'=>-16.0,'longitude'=>-71.0,'location_origin'=>'GPS_NEO6M']);
        $this->event($trip); $this->event($trip,['latitude'=>91,'longitude'=>0]);
        $this->event($trip,['latitude'=>-16.1,'longitude'=>-71.1,'type'=>'SHARP_TURN']);
        $foreignAdmin=$this->admin();
        $foreign=$this->event($this->trip($foreignAdmin),['latitude'=>-18.0,'longitude'=>-72.0]);
        $foreignVehicle=\App\Models\Vehicle::forceCreate(['company_id'=>$foreignAdmin->company_id,'plate'=>'TEST-FOREIGN','brand'=>'TEST','model'=>'TEST']);
        $this->actingAs($admin);
        $url='/dashboard/map?from=2026-09-29&to=2026-09-29&type=HARSH_BRAKING&vehicle_id='.$vehicle->id;
        $mapUrl = str_replace('/dashboard/map?', '/dashboard/events?tab=map&', $url);
        $this->get($url)->assertRedirect($mapUrl);
        $this->get(str_replace('?tab=map&', '?', $mapUrl))->assertOk()
            ->assertSee('Últimos eventos')->assertSee('Mapa de incidencias')
            ->assertViewHas('events', fn ($rows) => $rows->total() === 3);
        $this->get($mapUrl)->assertOk()->assertSee('Mapa de incidencias')->assertSee('Mapa de calor')
            ->assertSee('Filtrar por tipo')->assertSee('Frenado brusco')->assertSee('Giro brusco')->assertSee('Aceleración brusca')
            ->assertDontSee('id="event-select"', false)
            ->assertSee('1 evento con ubicación')->assertSee($event->client_event_id)->assertDontSee($foreign->client_event_id)
            ->assertViewHas('geojson',fn ($data)=>count($data['features'])===1 && $data['features'][0]['geometry']['coordinates']===[-71.0,-16.0])
            ->assertViewHas('vehicles',fn ($rows)=>$rows->pluck('id')->all()===[$vehicle->id]);
        $this->get('/dashboard/events?tab=map&vehicle_id='.$foreignVehicle->id)->assertSessionHasErrors('vehicle_id');
        $this->get('/dashboard/events?tab=map&from=2026-10-01')->assertOk()->assertSee('No hay eventos con ubicación para estos filtros.');
    }

    public function test_trip_details_return_to_the_originating_view_with_its_filters(): void
    {
        $admin = $this->admin();
        $trip = $this->trip($admin);
        $detail = '/dashboard/trips/'.$trip->client_trip_id;

        $this->actingAs($admin)->get($detail.'?from_view=summary&from=2026-09-01')
            ->assertOk()->assertSee('Volver al resumen')
            ->assertViewHas('returnRoute', 'dashboard.index')
            ->assertViewHas('returnFilters', ['from' => '2026-09-01']);
        $this->get($detail.'?from_view=events&type=HARSH_BRAKING&source_page=2&page=3')
            ->assertOk()->assertSee('Volver a eventos')
            ->assertViewHas('returnFilters', ['type' => 'HARSH_BRAKING', 'page' => '2']);
    }

    public function test_incident_map_requires_fleet_admin_and_has_page_specific_script_policy(): void
    {
        $this->get('/dashboard/map')->assertRedirect('/dashboard/login');
        $this->get('/dashboard/events?tab=map')->assertRedirect('/dashboard/login');
        $this->actingAs(User::factory()->create(['role'=>'member']))->get('/dashboard/map')->assertForbidden();
        $this->get('/dashboard/events?tab=map')->assertForbidden();
        $admin=$this->admin();
        $response=$this->actingAs($admin)->get('/dashboard/events?tab=map')->assertOk();
        $this->assertStringContainsString("script-src 'self'",$response->headers->get('Content-Security-Policy'));
        $this->assertStringContainsString('https://tiles.openfreemap.org',$response->headers->get('Content-Security-Policy'));
        $response->assertSee('id="incident-style"', false);
    }

    private function admin(?Company $company = null): User
    {
        return User::factory()->create(['role' => 'fleet_admin', 'company_id' => ($company ?? Company::factory()->create())->id]);
    }

    private function trip(User $user, array $attributes = []): Trip
    {
        return Trip::create(array_replace(['company_id' => $user->company_id, 'created_by_user_id' => $user->id,
            'client_trip_id' => (string) Str::uuid(), 'started_at' => '2026-09-30 02:26:55.999',
            'ended_at' => '2026-09-30 02:27:11.905', 'status' => 'COMPLETED', 'client_revision' => 2,
            'first_received_at' => '2026-09-30 02:27:20.000', 'last_changed_at' => '2026-09-30 02:27:20.000'], $attributes));
    }

    private function event(Trip $trip, array $attributes = []): DrivingEvent
    {
        return DrivingEvent::create(array_replace(['company_id' => $trip->company_id, 'trip_id' => $trip->id,
            'client_event_id' => (string) Str::uuid(), 'type' => 'HARSH_BRAKING', 'origin' => 'SIMULATED',
            'occurred_at' => '2026-09-30 02:27:05.016', 'first_received_at' => '2026-09-30 02:27:20.000'], $attributes));
    }

    public function test_guests_cannot_access_any_dashboard_page(): void
    {
        foreach (['', '/trips', '/events', '/analytics', '/settings', '/trips/'.Str::uuid()] as $path) {
            $this->get('/dashboard'.$path)->assertRedirect('/dashboard/login');
        }
        $this->get('/dashboard/login')->assertOk()->assertSee('Iniciar sesión');
    }

    public function test_member_is_denied_every_protected_page_even_with_direct_urls(): void
    {
        $member = User::factory()->create();
        $trip = $this->trip($member);
        $this->actingAs($member, 'web');
        foreach (['', '/trips', '/events', '/analytics', '/settings', '/trips/'.$trip->client_trip_id] as $path) {
            $this->get('/dashboard'.$path)->assertForbidden();
        }
    }

    public function test_admin_can_login_without_issuing_an_api_token(): void
    {
        $admin = $this->admin();
        $this->post('/dashboard/login', ['email' => strtoupper($admin->email), 'password' => 'password'])->assertRedirect('/dashboard');
        $this->assertAuthenticatedAs($admin, 'web');
        $this->assertDatabaseCount('personal_access_tokens', 0);
        $this->get('/dashboard')->assertOk()->assertHeader('X-Frame-Options', 'DENY');
        $this->get('/dashboard/login')->assertRedirect('/dashboard');
    }

    public function test_invalid_password_and_member_login_are_denied(): void
    {
        $member = User::factory()->create();
        $admin = $this->admin();
        foreach ([[$member->email, 'password'], [$admin->email, 'wrong']] as [$email, $password]) {
            $this->post('/dashboard/login', compact('email', 'password'))->assertSessionHasErrors('email');
            $this->assertGuest('web');
        }
    }

    public function test_web_login_is_rate_limited(): void
    {
        $admin = $this->admin();
        for ($i = 0; $i < 5; $i++) {
            $this->post('/dashboard/login', ['email' => $admin->email, 'password' => 'wrong'])->assertSessionHasErrors('email');
        }
        $this->post('/dashboard/login', ['email' => $admin->email, 'password' => 'password'])
            ->assertSessionHasErrors(['email' => 'Demasiados intentos. Espera un minuto y vuelve a intentarlo.']);
        $this->assertGuest();
    }

    public function test_csrf_is_required_for_login_and_logout(): void
    {
        // Laravel skips CSRF in normal tests. Explicitly enforce it for this test.
        $this->app->bind(ValidateCsrfToken::class, fn ($app) => new class($app, $app['encrypter']) extends ValidateCsrfToken
        {
            protected function runningUnitTests()
            {
                return false;
            }
        });
        $admin = $this->admin();
        $this->post('/dashboard/login', ['email' => $admin->email, 'password' => 'password'])->assertStatus(419);
        $this->withSession(['_token' => 'TEST-csrf'])->post('/dashboard/login', ['_token' => 'TEST-csrf', 'email' => $admin->email, 'password' => 'password'])->assertRedirect('/dashboard');
        $this->post('/dashboard/logout')->assertStatus(419);
    }

    public function test_logout_invalidates_web_session_but_preserves_mobile_tokens(): void
    {
        $admin = $this->admin();
        $admin->createToken('TEST ONLY mobile', $admin->capabilities());
        $this->actingAs($admin)->withSession(['private-test' => 'TEST ONLY'])->post('/dashboard/logout')->assertRedirect('/dashboard/login')->assertSessionMissing('private-test');
        $this->assertGuest('web');
        $this->assertDatabaseCount('personal_access_tokens', 1);
        $this->get('/dashboard')->assertRedirect('/dashboard/login');
    }

    public function test_empty_dashboard_lists_and_analytics_are_honest(): void
    {
        $this->actingAs($this->admin());
        $this->get('/dashboard')->assertOk()->assertSee('No hay recorridos registrados todavía.')->assertViewHas('metrics', fn ($m) => array_sum($m) === 0);
        $this->get('/dashboard/trips')->assertOk()->assertSee('0 recorridos encontrados');
        $this->get('/dashboard/events')->assertOk()->assertSee('No hay eventos disponibles para este período.');
        $this->get('/dashboard/analytics')->assertRedirect('/dashboard');
        $this->get('/dashboard')->assertOk()->assertSee('No hay datos disponibles para este período.');
    }

    public function test_metrics_and_recent_activity_are_company_scoped(): void
    {
        $admin = $this->admin();
        $own = $this->trip($admin);
        $this->event($own);
        $foreign = $this->trip($this->admin());
        $foreignEvent = $this->event($foreign);
        $this->actingAs($admin)->get('/dashboard')->assertOk()->assertSee($own->client_trip_id)
            ->assertDontSee($foreign->client_trip_id)->assertDontSee($foreignEvent->client_event_id)
            ->assertViewHas('metrics', fn ($m) => $m['Recorridos recibidos'] === 1 && $m['Finalizados'] === 1 && $m['Activos registrados'] === 0 && $m['Eventos recibidos'] === 1 && count($m) === 4);
    }

    public function test_recent_notifications_show_received_maneuvers_and_filter_by_driver_and_vehicle(): void
    {
        $admin = $this->admin();
        $driver = Driver::forceCreate(['company_id' => $admin->company_id, 'user_id' => $admin->id, 'full_name' => 'TEST Driver']);
        $vehicle = Vehicle::forceCreate(['company_id' => $admin->company_id, 'plate' => 'TEST-001', 'brand' => 'TEST', 'model' => 'TEST']);
        $matching = $this->trip($admin, ['driver_id' => $driver->id, 'vehicle_id' => $vehicle->id]);
        $this->event($matching);
        $this->event($this->trip($admin, ['vehicle_id' => $vehicle->id]), ['type' => 'HARSH_ACCELERATION']);
        $this->event($this->trip($admin, ['driver_id' => $driver->id]), ['type' => 'SHARP_TURN']);
        $foreign = $this->trip($this->admin());
        $this->event($foreign);

        $this->get('/dashboard/notifications')->assertRedirect('/dashboard/login');
        $this->actingAs($admin)->get('/dashboard/notifications')->assertOk()
            ->assertSee('Aceleración brusca')->assertSee('Giro brusco')->assertDontSee($foreign->client_trip_id);
        $filters = '?notice_driver='.$driver->id.'&notice_vehicle='.$vehicle->id;
        $this->get('/dashboard/notifications'.$filters)->assertOk()->assertHeader('X-Fleet-Notifications', '1')
            ->assertSee('Frenado brusco')->assertSee('Dato de prueba')->assertSee('TEST-001')->assertSee('TEST Driver')
            ->assertDontSee('Aceleración brusca')->assertDontSee('Giro brusco');
        $this->get('/dashboard'.$filters)->assertOk()->assertSee('Notificaciones recientes')->assertSee('Analítica por período');
    }

    public function test_lists_show_company_members_but_never_other_companies(): void
    {
        $admin = $this->admin();
        $member = User::factory()->create(['company_id' => $admin->company_id]);
        $trip = $this->trip($member);
        $event = $this->event($trip);
        $foreign = $this->trip($this->admin());
        $foreignEvent = $this->event($foreign);
        $this->actingAs($admin)->get('/dashboard/trips')->assertOk()->assertSee($trip->client_trip_id)->assertDontSee($foreign->client_trip_id);
        $this->get('/dashboard/events')->assertOk()->assertSee($event->client_event_id)->assertDontSee($foreignEvent->client_event_id);
        $this->get('/dashboard/trips/'.$foreign->client_trip_id)->assertNotFound();
    }

    public function test_trip_detail_has_events_and_local_dates_without_mutating_utc(): void
    {
        $admin = $this->admin();
        $trip = $this->trip($admin);
        $event = $this->event($trip);
        $this->actingAs($admin)->get('/dashboard/trips/'.$trip->client_trip_id)->assertOk()
            ->assertSee('29/09/2026 21:26:55')->assertSee('29/09/2026 21:27:11')->assertSee('29/09/2026 21:27:05')
            ->assertSee($event->client_event_id)->assertSee('Dato de prueba')->assertSee('Frenado brusco');
        $this->assertSame('2026-09-30 02:26:55.999', $trip->fresh()->started_at->format('Y-m-d H:i:s.v'));
        $this->assertSame('America/Lima', DashboardDisplay::ZONE);
    }

    public function test_trip_gps_points_are_idempotent_scoped_and_rendered_in_order(): void
    {
        $admin = $this->admin();
        $owner = User::factory()->create(['company_id' => $admin->company_id, 'role' => 'driver']);
        $trip = $this->trip($owner);
        $other = $this->admin();
        $ownerToken = $owner->createToken('GPS test', $owner->capabilities())->plainTextToken;
        $otherToken = $other->createToken('GPS test', $other->capabilities())->plainTextToken;
        $path = '/api/v1/trips/'.$trip->client_trip_id.'/track-points/';
        $firstId = (string) Str::uuid();
        $first = ['captured_at' => '2026-09-30T02:26:56.000Z', 'latitude' => -16.39891, 'longitude' => -71.53695];
        $last = ['captured_at' => '2026-09-30T02:27:11.905Z', 'latitude' => -16.40017, 'longitude' => -71.53257];
        $this->putJson($path.$firstId, $first, ['Authorization' => 'Bearer '.$otherToken])->assertNotFound();
        Auth::forgetGuards();
        $headers = ['Authorization' => 'Bearer '.$ownerToken];
        $this->putJson($path.Str::uuid(), $last, $headers)->assertCreated()->assertJsonPath('data.captured_at', $last['captured_at']);
        $this->putJson($path.$firstId, $first, $headers)->assertCreated();
        $this->putJson($path.$firstId, $first, $headers)->assertOk();
        $this->putJson($path.$firstId, array_replace($first, ['latitude' => -16.0]), $headers)->assertConflict();
        $this->putJson($path.Str::uuid(), array_replace($last, ['latitude' => 999]), $headers)->assertUnprocessable();
        $this->putJson($path.Str::uuid(), array_replace($last, ['captured_at' => '2026-09-30T02:27:12.000Z']), $headers)->assertUnprocessable();
        $this->assertDatabaseCount('trip_track_points', 2);

        $detail = '/dashboard/trips/'.$trip->client_trip_id;
        $this->actingAs($admin, 'web')->get($detail)->assertOk()
            ->assertSee('Salida GPS')->assertSee('Llegada GPS')->assertSee('trip-track.js')
            ->assertViewHas('track', fn ($track) => $track->pluck('coordinates')->all() === [
                [$first['longitude'], $first['latitude']], [$last['longitude'], $last['latitude']],
            ]);
        $this->actingAs($other, 'web')->get($detail)->assertNotFound();
        $legacy = $this->trip($other);
        $this->get('/dashboard/trips/'.$legacy->client_trip_id)->assertOk()->assertSee('Este viaje aún no tiene trazado GPS');
    }

    public function test_trip_filters_validate_status_user_and_local_day_boundaries(): void
    {
        $admin = $this->admin();
        $own = $this->trip($admin);
        $other = User::factory()->create(['company_id' => $admin->company_id]);
        $excluded = $this->trip($other, ['status' => 'ACTIVE', 'client_revision' => 1, 'ended_at' => null]);
        $nextDay = $this->trip($admin, ['started_at' => '2026-09-30 05:00:00.000']);
        $this->actingAs($admin)->get('/dashboard/trips?status=COMPLETED&from=2026-09-29&to=2026-09-29&user_id='.$admin->id)
            ->assertOk()->assertSee($own->client_trip_id)->assertDontSee($excluded->client_trip_id)->assertDontSee($nextDay->client_trip_id);
        $this->get('/dashboard/trips?user_id='.$this->admin()->id)->assertSessionHasErrors('user_id');
        $this->get('/dashboard/trips?status=INVALID')->assertSessionHasErrors('status');
        $this->get('/dashboard/trips?from=2026-09-30&to=2026-09-29')->assertSessionHasErrors('to');
    }

    public function test_event_filters_and_reserved_sensor_have_no_fabricated_results(): void
    {
        $admin = $this->admin();
        $trip = $this->trip($admin);
        $included = $this->event($trip);
        $excluded = $this->event($trip, ['type' => 'SHARP_TURN']);
        $nextDay = $this->event($trip, ['occurred_at' => '2026-09-30 05:00:00.000']);
        $this->actingAs($admin)->get('/dashboard/events?type=HARSH_BRAKING&origin=SIMULATED&from=2026-09-29&to=2026-09-29')
            ->assertOk()->assertSee($included->client_event_id)->assertDontSee($excluded->client_event_id)->assertDontSee($nextDay->client_event_id);
        $this->get('/dashboard/events?origin=SENSOR')->assertOk()->assertSee('0 eventos encontrados');
        $this->get('/dashboard/events?type=INVALID')->assertSessionHasErrors('type');
        $this->get('/dashboard/events?from=2026-02-30')->assertSessionHasErrors('from');
    }

    public function test_analytics_groups_local_days_and_isolates_company(): void
    {
        $admin = $this->admin();
        $this->event($this->trip($admin));
        $this->event($this->trip($this->admin()));
        $period = '?from=2026-09-29&to=2026-09-29';
        $this->actingAs($admin)->get('/dashboard/analytics'.$period)->assertRedirect('/dashboard'.$period);
        $this->get('/dashboard'.$period)->assertOk()
            ->assertViewHas('days', ['2026-09-29' => 1])
            ->assertViewHas('types', fn ($types) => $types->all() === ['HARSH_BRAKING' => 1])
            ->assertViewHas('states', fn ($states) => $states->all() === ['COMPLETED' => 1]);
        $this->get('/dashboard?from=2020-01-01&to=2026-09-29')->assertSessionHasErrors('from');
    }

    public function test_pagination_preserves_filters_and_company_scope(): void
    {
        $admin = $this->admin();
        for ($i = 0; $i < 21; $i++) {
            $this->event($this->trip($admin));
        }
        $this->event($this->trip($this->admin()));
        $this->actingAs($admin)->get('/dashboard/trips?status=COMPLETED&page=2')->assertOk()->assertViewHas('trips', fn ($p) => $p->total() === 21 && $p->count() === 1)->assertSee('status=COMPLETED');
        $this->get('/dashboard/events?page=2')->assertOk()->assertViewHas('events', fn ($p) => $p->total() === 21 && $p->count() === 1);
    }

    public function test_settings_escape_content_and_do_not_offer_sensitive_edits(): void
    {
        $admin = $this->admin(Company::factory()->create(['name' => '<script>TEST ONLY</script>']));
        $this->actingAs($admin)->get('/dashboard/settings')->assertOk()->assertSee($admin->email)
            ->assertSee('&lt;script&gt;TEST ONLY&lt;/script&gt;', false)->assertDontSee('<script>TEST ONLY</script>', false)
            ->assertSee('Guardar cambios')->assertSee('Administrador de flota');
        $this->post('/dashboard/settings', ['name' => 'TEST change'])->assertStatus(405);
        $this->put('/dashboard/settings', ['name' => 'TEST change', 'fleet_notification_interval' => 1])->assertSessionHasErrors('fleet_notification_interval');
        $this->put('/dashboard/settings', [
            'name' => 'TEST updated', 'fleet_notification_interval' => 30,
            'email' => 'ignored@example.test', 'role' => 'platform_admin',
        ])->assertRedirect('/dashboard/settings');
        $this->assertDatabaseHas('users', ['id' => $admin->id, 'name' => 'TEST updated',
            'fleet_notification_interval' => 30, 'role' => 'fleet_admin', 'email' => $admin->email]);
        $this->get('/dashboard')->assertOk()->assertSee('data-refresh-seconds="30"', false);
        $this->put('/dashboard/settings', ['name' => 'TEST updated', 'fleet_notification_interval' => 0])->assertRedirect('/dashboard/settings');
        $this->get('/dashboard')->assertOk()->assertDontSee('fleet-notifications.js');
    }

    public function test_role_revocation_is_checked_on_each_web_request(): void
    {
        $admin = $this->admin();
        $this->actingAs($admin)->get('/dashboard')->assertOk();
        $admin->forceFill(['role' => 'member'])->save();
        $this->get('/dashboard')->assertForbidden();
    }

    public function test_web_session_cannot_replace_sanctum_token_context(): void
    {
        $admin = $this->admin();
        $this->actingAs($admin, 'web')->getJson('/api/v1/me')->assertUnauthorized();
        Auth::guard('web')->logout();
        Auth::forgetGuards();
        $token = $admin->createToken('TEST ONLY Android', $admin->capabilities())->plainTextToken;
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$token])->assertOk()->assertJsonPath('data.company.id', $admin->company_id);
    }

    public function test_dashboard_does_not_cache_private_pages_or_allow_external_scripts(): void
    {
        $response = $this->actingAs($this->admin())->get('/dashboard');
        $response->assertOk()->assertHeader('X-Frame-Options', 'DENY');
        $this->assertStringContainsString('no-store', $response->headers->get('Cache-Control'));
        $this->assertMatchesRegularExpression("/(?:^|; )script-src 'nonce-[A-Za-z0-9+\/]+';/", $response->headers->get('Content-Security-Policy'));
        $nonce = $response->baseRequest->attributes->get('fleet_script_nonce');
        $response->assertSee('js/fleet-theme.js" nonce="'.$nonce.'"', false);
    }

    public function test_production_dashboard_requires_https(): void
    {
        $this->app->detectEnvironment(fn () => 'production');
        $this->get('http://localhost/dashboard/login')->assertStatus(400);
        $this->get('https://localhost/dashboard/login')->assertOk();
    }
}
