package com.tecsup.asistente.conduccion.data.local

import android.content.Context
import android.util.AtomicFile
import com.tecsup.asistente.conduccion.domain.processing.LearnedVehicleProfile
import com.tecsup.asistente.conduccion.domain.processing.RoadTestLimits
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleProfileStore @Inject constructor(@ApplicationContext private val context: Context) {
    @Synchronized
    fun load(address: String): LearnedVehicleProfile? {
        val file = fileFor(address)
        val data = Properties()
        try {
            file.openRead().use { data.load(it) }
        } catch (_: FileNotFoundException) {
            try {
                context.assets.open("road-test-profiles/${file.baseFile.name}").use { data.load(it) }
            } catch (_: FileNotFoundException) {
                return null
            }
        }
        val profile = LearnedVehicleProfile.read(data)
        if (data.getProperty("roadTestDefaults") == "1") return profile
        return RoadTestLimits.applyTo(profile).also { save(address, it) }
    }

    @Synchronized
    fun save(address: String, profile: LearnedVehicleProfile) {
        val file = fileFor(address)
        val output = file.startWrite()
        try {
            profile.properties().apply { setProperty("roadTestDefaults", "1") }
                .store(output, "Perfil experimental del montaje")
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun fileFor(address: String): AtomicFile {
        val key = MessageDigest.getInstance("SHA-256").digest(address.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(context.filesDir, "vehicle-profile-$key.properties"))
    }
}
