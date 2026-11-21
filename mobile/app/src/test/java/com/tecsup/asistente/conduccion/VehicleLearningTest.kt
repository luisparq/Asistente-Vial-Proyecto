package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.domain.processing.*
import com.tecsup.asistente.conduccion.domain.diagnostic.RawImuSample
import org.junit.Assert.*
import org.junit.Test

/** Synthetic motion only. Does not validate physical accuracy or dangerous-driving criteria. */
class VehicleLearningTest {
    private fun reference(z: Int = 16384) = ExperimentalCalibrationAccumulator().apply {
        repeat(120) { add(RawImuSample(0,0,z,-131,262,0)) }
    }.result()!!
    private fun sample(ax: Double=0.0, ay: Double=0.0, az: Double=1.0, yaw: Double=0.0, time: Long=0) =
        PhysicalImuSample(Vector3(ax,ay,az),Vector3(-1.0,2.0,yaw),time)
    private fun trained(yAxis: Boolean=false, inverted: Boolean=false): VehicleLearning {
        val engine=VehicleLearning(); engine.setReference(reference(if(inverted) -16384 else 16384))
        val z=if(inverted) -1.0 else 1.0
        assertTrue(engine.begin(NormalManeuver.ACCELERATION))
        repeat(16) { engine.acceptLearning(sample(ax=if(yAxis) 0.0 else 0.1, ay=if(yAxis) 0.1 else 0.0,az=z,time=it*250L)) }
        assertTrue(engine.finish())
        assertTrue(engine.begin(NormalManeuver.BRAKING))
        repeat(16) { engine.acceptLearning(sample(ax=if(yAxis) 0.0 else -0.12,ay=if(yAxis) -0.12 else 0.0,az=z,time=it*250L)) }
        assertTrue(engine.finish())
        assertTrue(engine.begin(NormalManeuver.TURN))
        repeat(16) { engine.acceptLearning(sample(az=z,yaw=if(it<8) 10.0 else -10.0,time=it*250L)) }
        assertTrue(engine.finish())
        assertNotNull(engine.profile)
        return engine
    }
    @Test fun allNormalLessonsAreRequiredAndLimitsComeFromMeasuredEnvelope() {
        val e=VehicleLearning()
        assertFalse(e.begin(NormalManeuver.ACCELERATION))
        e.setReference(reference())
        assertFalse(e.begin(NormalManeuver.BRAKING))
        assertNull(e.profile)
        val p=trained().profile!!
        assertEquals(0.18,p.accelerationLimitG,0.0001)
        assertEquals(0.216,p.brakingLimitG,0.0001)
        assertEquals(18.0,p.turnLimitDps,0.0001)
        assertEquals(1.0,p.forward.x,0.0001)
    }
    @Test fun normalMotionAndVerticalBumpsDoNotTriggerButSustainedExcessDoes() {
        val e=trained()
        repeat(12) { assertNull(e.detect(sample(ax=0.1,az=1.3,yaw=10.0,time=it*250L))) }
        assertNull(e.detect(sample(ax=-0.4,time=4000)))
        assertNull(e.detect(sample(ax=-0.4,time=4250)))
        val hit=e.detect(sample(ax=-0.4,time=4500))!!
        assertEquals(NormalManeuver.BRAKING,hit.kind)
        assertEquals(0.4,hit.value,0.0001)
        repeat(8) { assertNull(e.detect(sample(ax=-0.4,time=4750+it*250L))) }
    }
    @Test fun learnsBoardOrientationInsteadOfAssumingLongitudinalXAndPositiveZ() {
        val e=trained(yAxis=true,inverted=true)
        assertEquals(1.0,e.profile!!.forward.y,0.0001)
        assertNull(e.detect(sample(ax=0.8,az=-1.0,time=0))) // lateral, not longitudinal
        assertNull(e.detect(sample(ay=0.4,az=-1.0,time=250)))
        assertNull(e.detect(sample(ay=0.4,az=-1.0,time=500)))
        assertEquals(NormalManeuver.ACCELERATION,e.detect(sample(ay=0.4,az=-1.0,time=750))!!.kind)
    }
    @Test fun shortSpikeAndReceptionGapNeverCompleteEvidence() {
        val e=trained()
        assertNull(e.detect(sample(yaw=30.0,time=0)))
        assertNull(e.detect(sample(time=250)))
        assertNull(e.detect(sample(yaw=30.0,time=500)))
        assertNull(e.detect(sample(yaw=30.0,time=750)))
        assertNull(e.detect(sample(yaw=30.0,time=2000)))
        assertNull(e.detect(sample(yaw=30.0,time=2250)))
        assertEquals(NormalManeuver.TURN,e.detect(sample(yaw=30.0,time=2500))!!.kind)
    }
    @Test fun uninformativeMixedDirectionOrOneSidedTurnsAreRejected() {
        val e=VehicleLearning();e.setReference(reference());e.begin(NormalManeuver.ACCELERATION)
        repeat(16) { e.acceptLearning(sample(time=it*250L)) }
        assertFalse(e.finish());assertNull(e.profile)
        e.begin(NormalManeuver.ACCELERATION)
        repeat(16) { e.acceptLearning(sample(ax=if(it%2==0) 0.1 else -0.1,time=it*250L)) }
        assertFalse(e.finish())
        val ready=trained();ready.begin(NormalManeuver.TURN)
        repeat(16) { ready.acceptLearning(sample(yaw=10.0,time=it*250L)) }
        assertFalse(ready.finish());assertNull(ready.profile)
    }
    @Test fun learningCanRecoverAfterAReceptionGap() {
        val engine = VehicleLearning()
        engine.setReference(reference())
        assertTrue(engine.begin(NormalManeuver.ACCELERATION))
        repeat(16) { engine.acceptLearning(sample(ax = -0.1, time = it * 250L)) }
        engine.acceptLearning(sample(ax = 0.1, time = 5000))
        assertEquals(1, engine.sampleCount)
        repeat(16) { engine.acceptLearning(sample(ax = 0.1, time = 5000 + it * 250L)) }
        assertTrue(engine.finish())
        assertTrue(NormalManeuver.ACCELERATION in engine.learned)
    }
    @Test fun accelerationLessonUsesTheCoherentPulseBeforeAnOppositeSignal() {
        val engine = VehicleLearning()
        engine.setReference(reference())
        assertTrue(engine.begin(NormalManeuver.ACCELERATION))
        repeat(20) {
            engine.acceptLearning(sample(ax = if (it < 10) 0.08 else -0.1, time = it * 250L))
        }
        assertTrue(engine.finish())
        assertTrue(NormalManeuver.ACCELERATION in engine.learned)
    }
    @Test fun profileRoundTripAndNewRestUpdateGyroBiasWithoutInventingNewLimits() {
        val p=trained().profile!!
        val saved=LearnedVehicleProfile.read(p.properties())
        assertEquals(p,saved)
        val e=VehicleLearning();e.setReference(reference(),saved)
        assertEquals(p.accelerationLimitG,e.profile!!.accelerationLimitG,0.0)
        assertEquals(-1.0,e.profile!!.gyroBias.x,0.0)
        e.reset();assertNull(e.profile)
    }

    @Test fun manualLimitsCanBeLowerThanTheLearnedNormalAndSurviveRestVerification() {
        val engine = trained()
        engine.updateLimits(0.09, 0.20, 15.0)
        val saved = LearnedVehicleProfile.read(engine.profile!!.properties())
        engine.setReference(reference(), saved)
        val restored = engine.profile!!
        assertTrue(restored.manualLimits)
        assertEquals(0.09, restored.accelerationLimitG, 0.0)
        assertEquals(0.20, restored.brakingLimitG, 0.0)
        assertEquals(15.0, restored.turnLimitDps, 0.0)
        assertEquals(0.10, restored.normalAccelerationG, 0.0001)
    }

    @Test fun roadTestDefaultsKeepTheCalibrationAndDriveDetection() {
        val engine = trained()
        val learned = engine.profile!!
        val defaults = RoadTestLimits.applyTo(learned)
        val restored = LearnedVehicleProfile.read(defaults.properties())
        assertEquals(learned.restAcceleration, restored.restAcceleration)
        assertEquals(learned.forward, restored.forward)
        assertEquals(learned.gyroBias, restored.gyroBias)
        assertEquals(0.265, restored.accelerationLimitG, 0.0)
        assertEquals(0.510, restored.brakingLimitG, 0.0)
        assertEquals(18.5, restored.turnLimitDps, 0.0)

        engine.restore(restored)
        repeat(8) { assertNull(engine.detect(sample(ax = 0.26, time = it * 50L))) }
        repeat(6) { assertNull(engine.detect(sample(ax = 0.27, time = 400 + it * 50L))) }
        assertEquals(NormalManeuver.ACCELERATION, engine.detect(sample(ax = 0.27, time = 700))!!.kind)
        repeat(6) { assertNull(engine.detect(sample(ax = -0.52, time = 4000 + it * 50L))) }
        assertEquals(NormalManeuver.BRAKING, engine.detect(sample(ax = -0.52, time = 4300))!!.kind)
        repeat(6) { assertNull(engine.detect(sample(yaw = 19.0, time = 8000 + it * 50L))) }
        assertEquals(NormalManeuver.TURN, engine.detect(sample(yaw = 19.0, time = 8300))!!.kind)
    }
}
