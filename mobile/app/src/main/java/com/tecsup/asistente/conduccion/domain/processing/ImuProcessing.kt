package com.tecsup.asistente.conduccion.domain.processing

import com.tecsup.asistente.conduccion.domain.diagnostic.RawImuSample

data class Vector3(val x: Double, val y: Double, val z: Double) {
    init { require(listOf(x, y, z).all { it.isFinite() }) }
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    fun blend(previous: Vector3, alpha: Double) = Vector3(
        alpha * x + (1 - alpha) * previous.x,
        alpha * y + (1 - alpha) * previous.y,
        alpha * z + (1 - alpha) * previous.z)
}

/** Nominal units for MPU6050 register semantics, NOT confirmation of the module's identity/accuracy. */
data class PhysicalImuSample(val accelerationG: Vector3, val angularVelocityDps: Vector3, val receivedAtMillis: Long)
data class ProcessedImuSample(val accelerationG: Vector3, val angularVelocityDps: Vector3,
    val receivedAtMillis: Long, val hasCalibrationOffsets: Boolean)

/** Offsets use g and degrees/second, and are subtracted after conversion. Null means uncalibrated. */
data class ImuCalibration(val accelerationOffsetG: Vector3, val gyroOffsetDps: Vector3)

class ImuConverter {
    // AsistenteVialImu.ino: ACCEL_CONFIG (0x1C) = 0x00; GYRO_CONFIG (0x1B) = 0x00.
    // AFS_SEL=0 -> ±2g / 16384 LSB/g; FS_SEL=0 -> ±250°/s / 131 LSB/(°/s).
    fun convert(raw: RawImuSample) = PhysicalImuSample(
        Vector3(raw.ax / 16384.0, raw.ay / 16384.0, raw.az / 16384.0),
        Vector3(raw.gx / 131.0, raw.gy / 131.0, raw.gz / 131.0), raw.receivedAtMillis)
}

/** Optional exponential smoothing, disabled by default; no validated filter tuning. */
class ImuProcessor(val calibration: ImuCalibration? = null, val smoothingAlpha: Double? = null) {
    init { require(smoothingAlpha == null || smoothingAlpha.isFinite() && smoothingAlpha > 0 && smoothingAlpha <= 1) }
    private var previous: ProcessedImuSample? = null
    fun reset() { previous = null }
    fun process(sample: PhysicalImuSample): ProcessedImuSample {
        // Replayed timestamps must not apply smoothing twice or alter its state.
        previous?.takeIf { sample.receivedAtMillis == it.receivedAtMillis }?.let { return it }
        val old = previous?.takeIf { sample.receivedAtMillis > it.receivedAtMillis }
        val accel = calibration?.let { sample.accelerationG - it.accelerationOffsetG } ?: sample.accelerationG
        val gyro = calibration?.let { sample.angularVelocityDps - it.gyroOffsetDps } ?: sample.angularVelocityDps
        return ProcessedImuSample(
            if (old != null && smoothingAlpha != null) accel.blend(old.accelerationG, smoothingAlpha) else accel,
            if (old != null && smoothingAlpha != null) gyro.blend(old.angularVelocityDps, smoothingAlpha) else gyro,
            sample.receivedAtMillis, calibration != null).also { previous = it }
    }
}

data class ImuProcessingResult(val raw: RawImuSample, val converted: PhysicalImuSample,
    val processed: ProcessedImuSample, val candidates: List<CandidateEvent>)

/** In-memory 3C pipeline only. No Room, API, trip association or SENSOR event writer. */
class ImuPipeline(
    private val converter: ImuConverter = ImuConverter(),
    private val processor: ImuProcessor = ImuProcessor(),
    private val detector: DrivingEventDetector = DrivingEventDetector(),
) {
    fun reset() { processor.reset(); detector.reset() }
    fun accept(raw: RawImuSample): ImuProcessingResult {
        val converted = converter.convert(raw)
        val processed = processor.process(converted)
        return ImuProcessingResult(raw, converted, processed, detector.detect(processed))
    }
}
