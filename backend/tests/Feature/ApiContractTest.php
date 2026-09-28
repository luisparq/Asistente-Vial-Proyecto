<?php

namespace Tests\Feature;

use App\Models\Company;
use App\Models\DrivingEvent;
use App\Models\Trip;
use App\Models\User;
use Illuminate\Database\QueryException;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\TestCase;

/** All identities, timestamps and events here are TEST FIXTURES, not physical observations. */
class ApiContractTest extends TestCase
{
    use RefreshDatabase;

    private function bearer(User $user, ?array $abilities = null): array
    {
        Auth::forgetGuards();

        return ['Authorization' => 'Bearer '.$user->createToken('TEST ONLY', $abilities ?? $user->capabilities(), now()->addHour())->plainTextToken];
    }

    private function active(array $override = []): array
    {
        return array_replace(['started_at' => '2026-09-28T10:00:00.123Z', 'ended_at' => null, 'status' => 'ACTIVE', 'client_revision' => 1], $override);
    }

    private function completed(array $override = []): array
    {
        return array_replace($this->active(), ['ended_at' => '2026-09-28T10:30:00.456Z', 'status' => 'COMPLETED', 'client_revision' => 2], $override);
    }

    private function event(array $override = []): array
    {
        return array_replace(['type' => 'HARSH_BRAKING', 'occurred_at' => '2026-09-28T10:10:00.789Z', 'origin' => 'SIMULATED'], $override);
    }

    public function test_authentication_login_me_revocation_and_hashed_storage(): void
    {
        $user = User::factory()->create();
        $this->getJson('/api/v1/me')->assertUnauthorized()->assertJsonPath('error.code', 'unauthenticated');
        $this->postJson('/api/v1/auth/token', ['email' => $user->email, 'password' => 'wrong'])->assertUnauthorized();
        $response = $this->postJson('/api/v1/auth/token', ['email' => $user->email, 'password' => 'password'])->assertCreated();
        $token = $response->json('data.access_token');
        $this->assertStringNotContainsString($token, DB::table('personal_access_tokens')->first()->token);
        $headers = ['Authorization' => 'Bearer '.$token];
        Auth::forgetGuards();
        $this->getJson('/api/v1/me', $headers)->assertOk()->assertJsonPath('data.company.id', $user->company_id)->assertJsonMissingPath('data.user.password');
        Auth::forgetGuards();
        $this->deleteJson('/api/v1/auth/token', [], $headers)->assertNoContent();
        Auth::forgetGuards();
        $this->getJson('/api/v1/me', $headers)->assertUnauthorized();
    }

    public function test_invalid_expired_and_changed_tenant_tokens_are_denied(): void
    {
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer invalid'])->assertUnauthorized();
        $user = User::factory()->create();
        $expired = $user->createToken('TEST ONLY', ['*'], now()->subMinute());
        Auth::forgetGuards();
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$expired->plainTextToken])->assertUnauthorized();
        $headers = $this->bearer($user);
        $user->company_id = Company::factory()->create()->id;
        $user->save();
        $this->getJson('/api/v1/me', $headers)->assertUnauthorized()->assertJsonPath('error.code', 'invalid_token_context');
    }

    public function test_trip_creation_and_idempotence_preserve_first_reception_and_milliseconds(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $url = '/api/v1/trips/'.Str::uuid();
        $created = $this->putJson($url, $this->active(), $headers)->assertCreated()->assertJsonPath('sync.disposition', 'created')->assertJsonPath('data.started_at', '2026-09-28T10:00:00.123Z');
        $this->travel(1)->minutes();
        $this->putJson($url, $this->active(), $headers)->assertOk()->assertExactJson(array_replace_recursive($created->json(), ['sync' => ['disposition' => 'already_applied']]));
        $this->assertDatabaseCount('trips', 1);
        $this->getJson($url, $headers)->assertOk()->assertJsonPath('data.received_event_count', 0);
    }

    public function test_completion_stale_revision_and_conflicts_do_not_reopen_trip(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $url = '/api/v1/trips/'.Str::uuid();
        $this->putJson($url, $this->active(), $headers)->assertCreated();
        $this->putJson($url, $this->completed(), $headers)->assertOk()->assertJsonPath('sync.disposition', 'updated')->assertJsonPath('data.ended_at', '2026-09-28T10:30:00.456Z');
        $this->putJson($url, $this->completed(), $headers)->assertOk()->assertJsonPath('sync.disposition', 'already_applied');
        $this->putJson($url, $this->active(), $headers)->assertOk()->assertJsonPath('sync.disposition', 'stale_ignored')->assertJsonPath('sync.acknowledged_revision', 1)->assertJsonPath('sync.current_revision', 2)->assertJsonPath('data.status', 'COMPLETED');
        $this->putJson($url, $this->completed(['ended_at' => '2026-09-28T11:00:00.000Z']), $headers)->assertConflict();
        $this->putJson($url, $this->active(['started_at' => '2026-09-28T09:00:00.000Z']), $headers)->assertConflict();
        $this->assertSame(2, Trip::first()->client_revision);
    }

    public function test_completed_trip_can_arrive_first_and_events_can_arrive_later(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $url = '/api/v1/trips/'.Str::uuid();
        $this->putJson($url, $this->completed(), $headers)->assertCreated();
        $this->putJson($url.'/events/'.Str::uuid(), $this->event(), $headers)->assertCreated()->assertJsonPath('data.origin', 'SIMULATED');
        $this->getJson($url, $headers)->assertOk()->assertJsonPath('data.received_event_count', 1);
    }

    public function test_events_are_idempotent_immutable_and_cannot_change_parent(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $first = '/api/v1/trips/'.Str::uuid();
        $second = '/api/v1/trips/'.Str::uuid();
        $uuid = Str::uuid();
        $this->putJson($first, $this->active(), $headers)->assertCreated();
        $this->putJson($second, $this->active(), $headers)->assertCreated();
        $created = $this->putJson($first.'/events/'.$uuid, $this->event(), $headers)->assertCreated();
        $this->travel(2)->minutes();
        $this->putJson($first.'/events/'.$uuid, $this->event(), $headers)->assertOk()->assertExactJson(array_replace_recursive($created->json(), ['sync' => ['disposition' => 'already_applied']]));
        $this->putJson($first.'/events/'.$uuid, $this->event(['type' => 'SHARP_TURN']), $headers)->assertConflict();
        $this->putJson($second.'/events/'.$uuid, $this->event(), $headers)->assertConflict();
        $this->assertDatabaseCount('driving_events', 1);
    }

    public function test_sensor_is_reserved_and_orphans_are_rejected(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $url = '/api/v1/trips/'.Str::uuid();
        $eventUrl = $url.'/events/'.Str::uuid();
        $this->putJson($eventUrl, $this->event(), $headers)->assertNotFound();
        $this->putJson($url, $this->active(), $headers)->assertCreated();
        $this->putJson($eventUrl, $this->event(['origin' => 'SENSOR']), $headers)->assertUnprocessable()->assertJsonPath('error.code', 'origin_not_enabled');
        $this->assertDatabaseCount('driving_events', 0);
    }

    public function test_company_isolation_and_same_uuid_in_different_companies(): void
    {
        $a = User::factory()->create();
        $b = User::factory()->create(['role' => 'fleet_admin']);
        $url = '/api/v1/trips/'.Str::uuid();
        $eventUrl = $url.'/events/'.Str::uuid();
        $this->putJson($url, $this->active(), $this->bearer($a))->assertCreated();
        $this->putJson($eventUrl, $this->event(), $this->bearer($a))->assertCreated();
        $headers = $this->bearer($b);
        $this->getJson($url, $headers)->assertNotFound();
        $this->getJson($url.'/events', $headers)->assertNotFound();
        $this->putJson($eventUrl, $this->event(), $headers)->assertNotFound();
        $this->getJson('/api/v1/trips', $headers)->assertOk()->assertJsonCount(0, 'data');
        $this->putJson($url, $this->completed(), $headers)->assertCreated();
        $this->putJson($eventUrl, $this->event(), $headers)->assertCreated();
        $this->assertDatabaseCount('trips', 2);
        $this->assertDatabaseCount('driving_events', 2);
        $this->getJson($url, $this->bearer($a))->assertOk()->assertJsonPath('data.status', 'ACTIVE');
    }

    public function test_same_company_members_cannot_read_or_overwrite_each_other_and_admin_is_read_only_for_others(): void
    {
        $owner = User::factory()->create();
        $member = User::factory()->create(['company_id' => $owner->company_id]);
        $admin = User::factory()->create(['company_id' => $owner->company_id, 'role' => 'fleet_admin']);
        $url = '/api/v1/trips/'.Str::uuid();
        $this->putJson($url, $this->active(), $this->bearer($owner))->assertCreated();
        $headers = $this->bearer($member);
        $this->getJson($url, $headers)->assertNotFound();
        $this->putJson($url, $this->active(), $headers)->assertNotFound();
        $this->getJson('/api/v1/trips', $headers)->assertJsonCount(0, 'data');
        $headers = $this->bearer($admin);
        $this->getJson($url, $headers)->assertOk();
        $this->getJson('/api/v1/trips', $headers)->assertJsonCount(1, 'data');
        $this->putJson($url, $this->completed(), $headers)->assertForbidden();
        $this->putJson($url.'/events/'.Str::uuid(), $this->event(), $headers)->assertForbidden();
    }

    public function test_token_abilities_and_current_role_both_limit_access(): void
    {
        $user = User::factory()->create();
        $headers = $this->bearer($user, ['trips:read']);
        $this->putJson('/api/v1/trips/'.Str::uuid(), $this->active(), $headers)->assertForbidden();
        $this->getJson('/api/v1/trips', $headers)->assertOk();
        $user->role = 'disabled';
        $user->save();
        Auth::forgetGuards();
        $this->getJson('/api/v1/trips', $headers)->assertForbidden();
    }

    public function test_uuid_unknown_fields_enums_revisions_and_dates_are_validated(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $this->putJson('/api/v1/trips/1', $this->active(), $headers)->assertUnprocessable();
        $url = '/api/v1/trips/'.Str::uuid();
        foreach ([['company_id' => 99], ['status' => 'CANCELLED'], ['client_revision' => 2], ['client_revision' => '1'],
            ['started_at' => '2026-02-30T10:00:00.123Z'], ['started_at' => '2026-09-28T10:00:00Z'],
            ['started_at' => '2026-09-28T10:00:00.123+00:00'], ['started_at' => '2026-09-28T10:00:00.123Z '],
            ['ended_at' => '2026-09-28T10:30:00.456Z'], ['ended_at' => '']] as $invalid) {
            $this->putJson($url, $this->active($invalid), $headers)->assertUnprocessable();
        }
        $missing = $this->active();
        unset($missing['ended_at']);
        $this->putJson($url, $missing, $headers)->assertUnprocessable();
        $this->putJson($url, $this->completed(['ended_at' => null]), $headers)->assertUnprocessable();
        $this->putJson($url.'?company_id=1', $this->active(), $headers)->assertUnprocessable();
        $this->assertDatabaseCount('trips', 0);
        $this->putJson($url, $this->active(), $headers)->assertCreated();
        foreach ([['type' => 'INFRACTION'], ['origin' => 'UNKNOWN'], ['tripId' => 1], ['occurred_at' => 'bad']] as $invalid) {
            $this->putJson($url.'/events/'.Str::uuid(), $this->event($invalid), $headers)->assertUnprocessable();
        }
    }

    public function test_clock_anomalies_are_preserved_and_reported_after_late_events(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $url = '/api/v1/trips/'.Str::uuid();
        $this->putJson($url, $this->completed(['ended_at' => '2026-09-28T09:00:00.000Z']), $headers)->assertCreated();
        $this->getJson($url, $headers)->assertJsonPath('data.temporal_issues', ['end_before_start']);
        $this->putJson($url.'/events/'.Str::uuid(), $this->event(), $headers)->assertCreated();
        $this->getJson($url, $headers)->assertJsonPath('data.temporal_issues', ['end_before_start', 'event_after_end']);
    }

    public function test_queries_filters_cursor_ties_and_scope_binding(): void
    {
        $user = User::factory()->create();
        $headers = $this->bearer($user);
        $ids = [(string) Str::uuid(), (string) Str::uuid(), (string) Str::uuid()];
        sort($ids);
        foreach ($ids as $id) {
            $this->putJson('/api/v1/trips/'.$id, $this->active(), $headers)->assertCreated();
        }
        $page = $this->getJson('/api/v1/trips?limit=2', $headers)->assertOk()->assertJsonCount(2, 'data')->assertJsonPath('data.0.client_trip_id', $ids[0]);
        $cursor = urlencode($page->json('next_cursor'));
        $this->getJson('/api/v1/trips?limit=2&cursor='.$cursor, $headers)->assertOk()->assertJsonCount(1, 'data')->assertJsonPath('data.0.client_trip_id', $ids[2])->assertJsonPath('next_cursor', null);
        $this->getJson('/api/v1/trips?limit=2&status=ACTIVE&cursor='.$cursor, $headers)->assertUnprocessable();
        $this->getJson('/api/v1/trips?limit=101', $headers)->assertUnprocessable();
        $this->getJson('/api/v1/trips?cursor=corrupted', $headers)->assertUnprocessable();
        $this->getJson('/api/v1/trips?started_from=2026-09-28T10:00:00.124Z', $headers)->assertJsonCount(0, 'data');
        $this->getJson('/api/v1/trips?started_before=2026-09-28T10:00:00.123Z', $headers)->assertJsonCount(0, 'data');
        $this->getJson('/api/v1/trips?status=COMPLETED', $headers)->assertJsonCount(0, 'data');
        $this->getJson('/api/v1/trips?limit=2&cursor='.$cursor, $this->bearer(User::factory()->create()))->assertUnprocessable();
    }

    public function test_event_queries_preserve_type_origin_time_and_pagination(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $url = '/api/v1/trips/'.Str::uuid();
        $this->putJson($url, $this->active(), $headers)->assertCreated();
        foreach (['HARSH_BRAKING', 'HARSH_ACCELERATION', 'SHARP_TURN'] as $type) {
            $this->putJson($url.'/events/'.Str::uuid(), $this->event(['type' => $type]), $headers)->assertCreated();
        }
        $page = $this->getJson($url.'/events?limit=2', $headers)->assertOk()->assertJsonCount(2, 'data');
        $this->getJson($url.'/events?limit=2&cursor='.urlencode($page->json('next_cursor')), $headers)->assertOk()->assertJsonCount(1, 'data');
        $this->getJson($url.'/events?type=SHARP_TURN&origin=SIMULATED', $headers)->assertOk()->assertJsonCount(1, 'data')->assertJsonPath('data.0.occurred_at', '2026-09-28T10:10:00.789Z');
        $this->getJson($url.'/events?origin=SENSOR', $headers)->assertJsonCount(0, 'data');
        $this->getJson($url.'/events?occurred_from=2026-09-28T10:10:00.790Z', $headers)->assertJsonCount(0, 'data');
    }

    public function test_database_foreign_key_prevents_cross_company_event_even_without_http(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $this->putJson('/api/v1/trips/'.Str::uuid(), $this->active(), $headers)->assertCreated();
        $otherCompany = Company::factory()->create();
        $this->expectException(QueryException::class);
        DrivingEvent::create(['company_id' => $otherCompany->id, 'trip_id' => Trip::first()->id, 'client_event_id' => (string) Str::uuid(),
            'type' => 'HARSH_BRAKING', 'origin' => 'SIMULATED', 'occurred_at' => now(), 'first_received_at' => now()]);
    }

    public function test_malformed_json_size_limit_and_login_throttling(): void
    {
        $this->call('POST', '/api/v1/auth/token', [], [], [], ['CONTENT_TYPE' => 'application/json'], '{broken')->assertStatus(400)->assertJsonPath('error.code', 'invalid_json');
        $this->postJson('/api/v1/auth/token', ['email' => str_repeat('x', 17000)])->assertStatus(413);
        for ($i = 0; $i < 5; $i++) {
            $this->postJson('/api/v1/auth/token', ['email' => 'test-missing@example.invalid', 'password' => 'test-only'])->assertUnauthorized();
        }
        $this->postJson('/api/v1/auth/token', ['email' => 'test-missing@example.invalid', 'password' => 'test-only'])->assertStatus(429)->assertHeader('Retry-After')->assertJsonPath('error.code', 'rate_limited');
    }

    public function test_all_contract_resources_require_authentication_even_without_accept_header(): void
    {
        $url = '/api/v1/trips/'.Str::uuid();
        foreach (['/api/v1/me', '/api/v1/trips', $url, $url.'/events'] as $resource) {
            $this->get($resource)->assertUnauthorized()->assertJsonPath('error.code', 'unauthenticated');
        }
        $this->putJson($url, $this->active())->assertUnauthorized();
        $this->putJson($url.'/events/'.Str::uuid(), $this->event())->assertUnauthorized();
    }

    public function test_invalid_login_shape_does_not_cause_server_error(): void
    {
        $this->postJson('/api/v1/auth/token', ['email' => ['invalid'], 'password' => 'test-only'])->assertUnprocessable();
    }

    public function test_uppercase_uuid_is_normalized_without_creating_a_second_record(): void
    {
        $headers = $this->bearer(User::factory()->create());
        $uuid = (string) Str::uuid();
        $this->putJson('/api/v1/trips/'.strtoupper($uuid), $this->active(), $headers)->assertCreated()->assertJsonPath('data.client_trip_id', $uuid);
        $this->putJson('/api/v1/trips/'.$uuid, $this->active(), $headers)->assertOk();
        $this->assertDatabaseCount('trips', 1);
    }

    public function test_write_only_token_can_repeat_its_event_without_read_permission(): void
    {
        $headers = $this->bearer(User::factory()->create(), ['trips:write']);
        $url = '/api/v1/trips/'.Str::uuid();
        $this->putJson($url, $this->active(), $headers)->assertCreated();
        $event = $url.'/events/'.Str::uuid();
        $this->putJson($event, $this->event(), $headers)->assertCreated();
        $this->putJson($event, $this->event(), $headers)->assertOk();
        $this->getJson($url, $headers)->assertForbidden();
    }

    public function test_reusing_an_invisible_members_event_does_not_disclose_a_conflict(): void
    {
        $first = User::factory()->create();
        $second = User::factory()->create(['company_id' => $first->company_id]);
        $uuid = (string) Str::uuid();
        $firstUrl = '/api/v1/trips/'.Str::uuid();
        $headers = $this->bearer($first);
        $this->putJson($firstUrl, $this->active(), $headers)->assertCreated();
        $this->putJson($firstUrl.'/events/'.$uuid, $this->event(), $headers)->assertCreated();
        $secondUrl = '/api/v1/trips/'.Str::uuid();
        $headers = $this->bearer($second);
        $this->putJson($secondUrl, $this->active(), $headers)->assertCreated();
        $this->putJson($secondUrl.'/events/'.$uuid, $this->event(), $headers)->assertNotFound();
    }

    public function test_production_requires_https_for_token_issuance(): void
    {
        $this->app->detectEnvironment(fn () => 'production');
        $this->postJson('/api/v1/auth/token', ['email' => 'test@example.invalid', 'password' => 'test-only'])->assertStatus(400)->assertJsonPath('error.code', 'https_required');
    }

    public function test_default_seeder_creates_no_identities_or_business_records(): void
    {
        $this->artisan('db:seed')->assertSuccessful();
        foreach (['companies', 'users', 'trips', 'driving_events'] as $table) {
            $this->assertDatabaseCount($table, 0);
        }
    }
}
