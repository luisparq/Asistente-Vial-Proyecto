package com.tecsup.asistente.conduccion.domain.diagnostic
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
@Serializable
data class SensorRelaySample(val deviceCode:String,val sessionId:String,val sequence:Long,
    val axRaw:Int,val ayRaw:Int,val azRaw:Int,val gxRaw:Int,val gyRaw:Int,val gzRaw:Int,
    val profileVersion:Int,val event:Int,val range:Int,val flags:Int,
    val capturedAt:Long,val protocol:Int=2,
    @kotlinx.serialization.Transient val receivedAtNanos:Long=System.nanoTime())
object SensorRelayDecoder {
    fun decode(bytes:ByteArray,device:String,session:String,now:Long):SensorRelaySample {
        require(bytes.size==20 && device.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,62}")))
        require(session.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",RegexOption.IGNORE_CASE)))
        val raw=ImuRawDecoder.decode(bytes.copyOfRange(0,12))
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val seq=b.getInt(12).toLong() and 0xffffffffL
        val version=b.getShort(16).toInt() and 65535
        val code=bytes[18].toInt() and 255
        require((code and 15)<=3 && (code ushr 4)<=4)
        return SensorRelaySample(device,session,seq,raw.ax,raw.ay,raw.az,raw.gx,raw.gy,raw.gz,version,code and 15,code ushr 4,bytes[19].toInt() and 255,now)
    }
}
