package com.tecsup.asistente.conduccion
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
class SensorRelayDecoderTest {
 private val session="566b07e4-78ca-4f0b-a15c-7ed5705ca2a0"
 @Test fun rawAndIdentitySurviveBothTransportRepresentations(){
  val b=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
  listOf(100,-200,16384,-160,150,-30).forEach{b.putShort(it.toShort())}
  b.putInt(-1);b.putShort(7);b.put(0x22);b.put(3)
  val s=SensorRelayDecoder.decode(b.array(),"AVIGEM-ONE-001",session,123456)
  assertEquals(4294967295L,s.sequence);assertEquals(-200,s.ayRaw);assertEquals(2,s.event);assertEquals(2,s.range);assertEquals(7,s.profileVersion)
  assertEquals(session,s.sessionId);assertEquals(123456L,s.capturedAt)
 }
 @Test fun malformedOrUnidentifiedFramesAreRejected(){
  assertThrows(IllegalArgumentException::class.java){SensorRelayDecoder.decode(ByteArray(12),"ONE",session,0)}
  assertThrows(IllegalArgumentException::class.java){SensorRelayDecoder.decode(ByteArray(20),"ONE","invalid",0)}
  assertThrows(IllegalArgumentException::class.java){SensorRelayDecoder.decode(ByteArray(20).apply{this[18]=0x55},"ONE",session,0)}
 }
}
