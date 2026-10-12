package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.data.devices.*
import com.tecsup.asistente.conduccion.data.network.*
import com.tecsup.asistente.conduccion.domain.sync.*
import java.time.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class DeviceReportTest {
    private class Store : DeviceReportStore {
        val data=mutableMapOf<String,DeviceReportEntry>()
        override fun read(owner: String)=data[owner]
        override fun write(owner: String,value: DeviceReportEntry) { data[owner]=value }
    }
    private class MutableClock(var now: Long=1_792_000_000_000L) : Clock() {
        override fun getZone()=ZoneOffset.UTC
        override fun withZone(zone: ZoneId)=this
        override fun instant(): Instant=Instant.ofEpochMilli(now)
    }
    private fun report(now: Long)=DeviceReport("TEST ONLY ESP32",Contract.timestamp(now),null,"CONNECTED","RECEIVING","NO_FIX",null)
    @Test fun cadence_eligibility_and_contact_do_not_fabricate_data() {
        val r=report(100_000); val e=DeviceReportEntry("TEST","00000000-0000-4000-8000-000000000001",100_000,r)
        assertFalse(DeviceReportPolicy.captureDue(e,159_999));assertTrue(DeviceReportPolicy.captureDue(e,160_000))
        assertNull(DeviceReportPolicy.contact(1000,null,null,null))
        assertEquals(Contract.timestamp(950),DeviceReportPolicy.contact(1000,900,950,null))
        assertFalse(DeviceReportPolicy.eligible(e.copy(nextAttemptAt=200_000),199_999,"TEST"))
        assertFalse(DeviceReportPolicy.eligible(e.copy(blocked="BLOCKED_VALIDATION"),200_000,"TEST"))
    }
    @Test fun persistent_latest_snapshot_uuid_retry_after_and_recovery()= runTest {
        val server=MockWebServer();server.start()
        try {
            val clock=MutableClock();val store=Store();val session=MemorySessionStore()
            session.save(Session(server.url("/").toString(),1,2,"TEST","test@example.invalid","TEST TOKEN"))
            val owner=session.state.value!!.ownerKey
            var sync=DeviceReportSync(store,session,LaravelApi(),clock)
            assertTrue(sync.capture(session.state.value!!.ownerKey,"TEST ADDRESS",report(clock.now)))
            val id=store.read(owner)!!.deviceId
            assertFalse(sync.capture(session.state.value!!.ownerKey,"TEST ADDRESS",report(clock.now)))
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After","300"))
            assertTrue(sync.flush()); val first=server.takeRequest();assertEquals("/api/v1/devices/$id/status",first.path)
            assertFalse(first.body.readUtf8().contains("latitude"))
            clock.now+=60_000
            assertTrue(sync.capture(session.state.value!!.ownerKey,"TEST ADDRESS",report(clock.now)))
            assertEquals(id,store.read(owner)!!.deviceId)
            assertTrue(store.read(owner)!!.nextAttemptAt>=clock.now+240_000)
            sync=DeviceReportSync(store,session,LaravelApi(),clock) // Reopened process uses the persistent entry.
            assertTrue(sync.flush());assertEquals(1,server.requestCount)
            clock.now+=300_000;val latest=store.read(owner)!!.report.observed_at
            server.enqueue(MockResponse().setBody("""{"data":{"client_device_id":"$id","observed_at":"$latest"}}"""))
            assertFalse(sync.flush());assertFalse(store.read(owner)!!.pending)
            sync.flush();assertEquals(2,server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun auth_failure_is_blocked_and_never_sent_for_another_account()=runTest {
        val server=MockWebServer();server.start()
        try {
            val clock=MutableClock();val store=Store();val session=MemorySessionStore()
            val a=Session(server.url("/").toString(),1,2,"TEST","a@example.invalid","TEST TOKEN")
            session.save(a);val sync=DeviceReportSync(store,session,LaravelApi(),clock)
            sync.capture(session.state.value!!.ownerKey,"TEST ADDRESS",report(clock.now));server.enqueue(MockResponse().setResponseCode(401))
            assertFalse(sync.flush());assertNull(session.state.value!!.token)
            assertEquals("BLOCKED_AUTH",store.read(a.ownerKey)!!.blocked)
            session.save(a.copy(userId=3,token="OTHER TEST TOKEN"))
            assertFalse(sync.capture(a.ownerKey,"TEST ADDRESS",report(clock.now)))
            sync.flush();assertEquals(1,server.requestCount)
            session.save(a);sync.flush();assertEquals(1,server.requestCount)
            assertTrue(DeviceReportPolicy.eligible(store.read(a.ownerKey)!!,clock.now,"NEW TEST TOKEN"))
        } finally {server.shutdown()}
    }
}
