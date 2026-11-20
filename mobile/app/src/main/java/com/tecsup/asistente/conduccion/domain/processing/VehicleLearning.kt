package com.tecsup.asistente.conduccion.domain.processing

import kotlin.math.*
import java.util.Properties

enum class NormalManeuver(val title: String) {
    ACCELERATION("Aceleración normal"), BRAKING("Frenada normal"), TURN("Giros normales")
}
data class LearnedVehicleProfile(
    val restAcceleration: Vector3, val gyroBias: Vector3, val forward: Vector3,
    val accelerationLimitG: Double, val brakingLimitG: Double, val turnLimitDps: Double,
    val normalAccelerationG: Double, val normalBrakingG: Double, val normalTurnDps: Double
) {
    fun properties() = Properties().apply {
        setProperty("version", "1")
        listOf(restAcceleration, gyroBias, forward).forEachIndexed { i,v -> setProperty("v$i", "${v.x},${v.y},${v.z}") }
        listOf(accelerationLimitG, brakingLimitG, turnLimitDps, normalAccelerationG, normalBrakingG, normalTurnDps)
            .forEachIndexed { i,v -> setProperty("n$i", v.toString()) }
    }
    companion object {
        fun read(p: Properties): LearnedVehicleProfile {
            require(p.getProperty("version") == "1")
            val v = (0..2).map { i -> p.getProperty("v$i").split(',').map(String::toDouble).let { Vector3(it[0],it[1],it[2]) } }
            val n = (0..5).map { p.getProperty("n$it").toDouble().also { value -> require(value.isFinite() && value > 0) } }
            require(norm(v[0]) in 0.7..1.3 && abs(norm(v[2])-1)<0.01 && abs(dot(v[0],v[2]))<0.02)
            require(n[0]>n[3] && n[1]>n[4] && n[2]>n[5])
            return LearnedVehicleProfile(v[0],v[1],v[2],n[0],n[1],n[2],n[3],n[4],n[5])
        }
    }
}
data class VehicleReading(val longitudinalG: Double, val yawDps: Double)
data class VehicleExcess(val kind: NormalManeuver, val value: Double, val limit: Double, val atMillis: Long)
private fun dot(a: Vector3,b: Vector3) = a.x*b.x+a.y*b.y+a.z*b.z
private fun norm(v: Vector3) = sqrt(dot(v,v))
private fun scale(v: Vector3,n: Double) = Vector3(v.x*n,v.y*n,v.z*n)
private fun unit(v: Vector3) = scale(v,1/norm(v))

/** Guided experimental envelope learning, NOT a validated dangerous-driving classifier.
 * Fixed mounting is required. Rest subtraction does not compensate dynamic pitch/roll.
 */
class VehicleLearning {
    var reference: ExperimentalCalibrationResult? = null
        private set
    var profile: LearnedVehicleProfile? = null
        private set
    var learning: NormalManeuver? = null
        private set
    var message = "Primero mide el reposo."
        private set
    private var forward: Vector3? = null
    private var normalAcceleration: Double? = null
    private var normalBraking: Double? = null
    private var normalTurn: Double? = null
    private val samples = mutableListOf<PhysicalImuSample>()
    private var previous: Long? = null
    private var candidate: NormalManeuver? = null
    private var candidateSince = 0L
    private var candidateCount = 0
    private var peak = 0.0
    private var lastAlert: Long? = null
    var invalidWindow = false
        private set
    val sampleCount get() = samples.size
    val learned get() = setOfNotNull(normalAcceleration?.let { NormalManeuver.ACCELERATION }, normalBraking?.let { NormalManeuver.BRAKING }, normalTurn?.let { NormalManeuver.TURN })

    fun reset() {
        reference=null; profile=null; forward=null; normalAcceleration=null; normalBraking=null; normalTurn=null
        learning=null; samples.clear(); resetDetection(); message="Primero mide el reposo."
    }
    fun setReference(result: ExperimentalCalibrationResult, reuse: LearnedVehicleProfile? = null) {
        reset()
        require(result.isStableForLab())
        require(norm(Vector3(result.nominal[0].mean,result.nominal[1].mean,result.nominal[2].mean)) in 0.7..1.3) { "Referencia de gravedad no compatible; revisa el sensor." }
        reference=result; message="Reposo listo. Enseña una aceleración normal en línea recta."
        if (reuse!=null && dot(unit(rest()),unit(reuse.restAcceleration))>cos(Math.toRadians(10.0))) {
            val up=unit(rest())
            forward=unit(reuse.forward-scale(up,dot(reuse.forward,up)))
            normalAcceleration=reuse.normalAccelerationG; normalBraking=reuse.normalBrakingG; normalTurn=reuse.normalTurnDps
            profile=reuse.copy(restAcceleration=rest(),gyroBias=bias(),forward=forward!!)
            message="Reposo actualizado. Perfil anterior listo; requiere conservar el mismo montaje."
        }
    }
    fun restore(saved: LearnedVehicleProfile) {
        reset(); profile=saved; forward=saved.forward
        normalAcceleration=saved.normalAccelerationG; normalBraking=saved.normalBrakingG; normalTurn=saved.normalTurnDps
        message="Perfil guardado disponible. Úsalo solo si el sensor conserva el mismo montaje."
    }
    private fun rest() = reference?.let { Vector3(it.nominal[0].mean,it.nominal[1].mean,it.nominal[2].mean) } ?: profile!!.restAcceleration
    private fun bias() = reference?.estimatedGyroOffsetsDps ?: profile!!.gyroBias
    private fun horizontal(s: PhysicalImuSample): Vector3 {
        val delta=s.accelerationG-rest(); val up=unit(rest())
        return delta-scale(up,dot(delta,up))
    }
    fun begin(kind: NormalManeuver): Boolean {
        if (reference==null || learning!=null || (kind!=NormalManeuver.ACCELERATION && forward==null)) return false
        if (kind==NormalManeuver.ACCELERATION) { forward=null; normalAcceleration=null; normalBraking=null; normalTurn=null }
        if (kind==NormalManeuver.BRAKING) normalBraking=null
        if (kind==NormalManeuver.TURN) normalTurn=null
        profile=null; learning=kind; samples.clear(); invalidWindow=false; resetDetection()
        message="Registrando ${kind.title.lowercase()}. Haz únicamente la maniobra indicada, con suavidad."
        return true
    }
    fun acceptLearning(s: PhysicalImuSample) {
        if (learning==null) return
        if (samples.lastOrNull()?.let { s.receivedAtMillis<=it.receivedAtMillis || s.receivedAtMillis-it.receivedAtMillis>750 } == true) invalidWindow=true
        if (samples.size<2400) samples.add(s) else invalidWindow=true
    }
    fun cancelLearning() { learning=null; samples.clear(); message="Aprendizaje cancelado. Repite esa maniobra." }
    private fun percentile(values: List<Double>): Double = values.sorted()[ceil((values.size-1)*0.95).toInt()]
    fun finish(): Boolean {
        val kind=learning ?: return false
        learning=null
        if (invalidWindow || samples.size<12 || samples.last().receivedAtMillis-samples.first().receivedAtMillis<3_000) {
            message="Faltan muestras continuas. Repite la maniobra normal."; return false
        }
        var values: List<Double>
        if (kind==NormalManeuver.ACCELERATION) {
            // Direction comes from coherent horizontal acceleration, not an assumed board axis.
            val h=samples.map(::horizontal).filter { norm(it)>0.03 }
            if (h.size<8) { message="No se observó aceleración suficiente. Repite en línea recta."; return false }
            val sum=Vector3(h.sumOf { it.x },h.sumOf { it.y },h.sumOf { it.z })
            if (norm(sum)/h.sumOf(::norm)<0.8) { message="La dirección cambió: repite solo aceleración recta, sin frenar ni girar."; return false }
            forward=unit(sum)
            values=h.map { dot(it,forward!!) }.filter { it>0.03 }
        } else if (kind==NormalManeuver.BRAKING) {
            values=samples.map { -dot(horizontal(it),forward!!) }.filter { it>0.03 }
        } else {
            val yaw=samples.map { dot(it.angularVelocityDps-bias(),unit(rest())) }
            if (yaw.count { it>2 }<4 || yaw.count { it< -2 }<4) { message="Faltan giros en ambos sentidos. Repite giros suaves a izquierda y derecha."; return false }
            values=yaw.map(::abs).filter { it>2 }
        }
        if (values.size<8) { message="No se observó suficiente ${kind.title.lowercase()}. Repite la medición."; return false }
        val typical=percentile(values)
        when(kind) {
            NormalManeuver.ACCELERATION -> normalAcceleration=typical
            NormalManeuver.BRAKING -> normalBraking=typical
            NormalManeuver.TURN -> normalTurn=typical
        }
        if (normalAcceleration!=null && normalBraking!=null && normalTurn!=null) {
            // Provisional margin above the measured normal envelope, explicitly exposed in the UI.
            val noise=reference!!.nominal.take(3).maxOf { it.sampleStandardDeviation ?: 0.0 }
            fun accelLimit(normal: Double)=maxOf(normal*1.8,normal+noise*3,0.12)
            profile=LearnedVehicleProfile(rest(),bias(),forward!!,accelLimit(normalAcceleration!!),accelLimit(normalBraking!!),
                maxOf(normalTurn!!*1.8,normalTurn!!+3,10.0),normalAcceleration!!,normalBraking!!,normalTurn!!)
            message="Perfil calculado. Ya puedes probar los límites aprendidos."
        } else message="${kind.title} aprendida. Continúa con las siguientes maniobras."
        return true
    }
    fun reading(s: PhysicalImuSample): VehicleReading? {
        val p=profile ?: return null
        return VehicleReading(dot(s.accelerationG-p.restAcceleration,p.forward),dot(s.angularVelocityDps-p.gyroBias,unit(p.restAcceleration)))
    }
    fun resetDetection() { previous=null; candidate=null; candidateCount=0; peak=0.0; lastAlert=null }
    fun detect(s: PhysicalImuSample): VehicleExcess? {
        val p=profile ?: return null
        val r=reading(s) ?: return null
        val gap=previous?.let { s.receivedAtMillis-it } ?: 0
        if (previous!=null && gap<=0) return null
        previous=s.receivedAtMillis
        if (gap>750) { candidate=null; candidateCount=0 }
        val kind=when {
            abs(r.yawDps)>p.turnLimitDps -> NormalManeuver.TURN
            r.longitudinalG>p.accelerationLimitG -> NormalManeuver.ACCELERATION
            -r.longitudinalG>p.brakingLimitG -> NormalManeuver.BRAKING
            else -> null
        }
        if (kind==null || lastAlert?.let { s.receivedAtMillis-it<3000 } == true) { candidate=null; candidateCount=0; return null }
        val value=when(kind) { NormalManeuver.TURN -> abs(r.yawDps); NormalManeuver.ACCELERATION -> r.longitudinalG; else -> -r.longitudinalG }
        if (candidate!=kind) { candidate=kind; candidateSince=s.receivedAtMillis; candidateCount=0; peak=value }
        candidateCount++; peak=max(peak,value)
        if (candidateCount<3 || s.receivedAtMillis-candidateSince<300) return null
        val limit=when(kind) { NormalManeuver.ACCELERATION -> p.accelerationLimitG; NormalManeuver.BRAKING -> p.brakingLimitG; NormalManeuver.TURN -> p.turnLimitDps }
        lastAlert=s.receivedAtMillis; candidate=null; candidateCount=0
        return VehicleExcess(kind,peak,limit,s.receivedAtMillis)
    }
}
