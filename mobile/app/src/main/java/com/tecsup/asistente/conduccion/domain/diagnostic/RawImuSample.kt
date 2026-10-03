package com.tecsup.asistente.conduccion.domain.diagnostic

/** Cuentas crudas int16; sin conversión de unidades, filtros ni clasificación. */
data class RawImuSample(val ax: Int, val ay: Int, val az: Int, val gx: Int, val gy: Int, val gz: Int)

object ImuRawDecoder {
    const val FRAME_SIZE = 12
    fun decode(bytes: ByteArray): RawImuSample {
        require(bytes.size == FRAME_SIZE) { "IMU_RAW requiere exactamente 12 bytes." }
        fun signed(offset: Int): Int = ((bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)).toShort().toInt()
        return RawImuSample(signed(0), signed(2), signed(4), signed(6), signed(8), signed(10))
    }
}
