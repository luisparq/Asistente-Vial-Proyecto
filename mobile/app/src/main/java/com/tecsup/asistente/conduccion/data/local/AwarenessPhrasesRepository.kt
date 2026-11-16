package com.tecsup.asistente.conduccion.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AwarenessPhrasesRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "awareness_phrases.db"
        const val DATABASE_VERSION = 2
        const val TABLE_NAME = "awareness_phrases"

        const val COL_ID = "id"
        const val COL_EVENT_TYPE = "event_type"
        const val COL_PHRASE = "phrase"
        const val COL_LAST_USED_AT = "last_used_at"
        const val COL_USE_COUNT = "use_count"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EVENT_TYPE TEXT NOT NULL,
                $COL_PHRASE TEXT NOT NULL,
                $COL_LAST_USED_AT INTEGER NOT NULL DEFAULT 0,
                $COL_USE_COUNT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        seedPhrases(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1→v2: actualiza el texto del saludo inicial de AVIGEM.
        if (oldVersion < 2) {
            db.execSQL(
                "UPDATE $TABLE_NAME SET $COL_PHRASE = ? WHERE $COL_EVENT_TYPE = 'START_TRIP'",
                arrayOf("Hola, soy AVIGEM. Te acompañaré durante tu recorrido.")
            )
        }
    }

    private fun seedPhrases(db: SQLiteDatabase) {
        val initialPhrases = listOf(
            // Frenado Brusco (HARSH_BRAKING)
            "HARSH_BRAKING" to "Recuerda mantener tu distancia. Anticipar la frenada protege tu vida y la de los demás.",
            "HARSH_BRAKING" to "Frenar con suavidad cuida a tus pasajeros y evita choques por alcance. Conduce con calma.",
            "HARSH_BRAKING" to "Mira más allá del auto de adelante para reducir la marcha progresivamente sin sobresaltos.",

            // Aceleración Brusca (HARSH_ACCELERATION)
            "HARSH_ACCELERATION" to "Acelerar de forma gradual optimiza el combustible y te da el control total de tu vehículo.",
            "HARSH_ACCELERATION" to "Apurar la marcha en ciudad no ahorra tiempo, solo aumenta el riesgo. Mantén un ritmo seguro.",
            "HARSH_ACCELERATION" to "Presiona el pedal con suavidad. Llegar a salvo siempre es la prioridad.",

            // Giro Brusco (SHARP_TURN)
            "SHARP_TURN" to "Reduce la velocidad antes de entrar a la curva, no dentro de ella. Mantendrás la estabilidad.",
            "SHARP_TURN" to "Toma los virajes con espacio y señaliza a tiempo. La suavidad en el volante evita derrapes.",
            "SHARP_TURN" to "Evita maniobras repentinas al doblar. Tu estabilidad garantiza la seguridad de la vía.",

            // Inicio de Recorrido (START_TRIP)
            "START_TRIP" to "Hola, soy AVIGEM. Te acompañaré durante tu recorrido."
        )

        for ((type, phrase) in initialPhrases) {
            val values = ContentValues().apply {
                put(COL_EVENT_TYPE, type)
                put(COL_PHRASE, phrase)
                put(COL_LAST_USED_AT, 0L)
                put(COL_USE_COUNT, 0)
            }
            db.insert(TABLE_NAME, null, values)
        }
    }

    /**
     * Obtiene la siguiente frase concientizadora rotando según el menor uso o más antigua.
     */
    @Synchronized
    fun getNextPhrase(eventType: String): String {
        val db = writableDatabase
        var phraseId = -1L
        var selectedPhrase = ""

        val cursor = db.rawQuery(
            """
            SELECT $COL_ID, $COL_PHRASE FROM $TABLE_NAME
            WHERE $COL_EVENT_TYPE = ?
            ORDER BY $COL_LAST_USED_AT ASC, $COL_ID ASC
            LIMIT 1
            """.trimIndent(),
            arrayOf(eventType)
        )

        cursor.use {
            if (it.moveToFirst()) {
                phraseId = it.getLong(0)
                selectedPhrase = it.getString(1)
            }
        }

        if (phraseId != -1L) {
            val now = System.currentTimeMillis()
            db.execSQL(
                "UPDATE $TABLE_NAME SET $COL_LAST_USED_AT = ?, $COL_USE_COUNT = $COL_USE_COUNT + 1 WHERE $COL_ID = ?",
                arrayOf(now, phraseId)
            )
        }

        return if (selectedPhrase.isNotEmpty()) selectedPhrase else defaultFallback(eventType)
    }

    private fun defaultFallback(eventType: String): String = when (eventType) {
        "HARSH_BRAKING" -> "Mantengamos una conducción suave."
        "HARSH_ACCELERATION" -> "Acelera con suavidad."
        "SHARP_TURN" -> "Vamos con calma en las curvas."
        else -> "Hola, soy AVIGEM. Te acompañaré durante tu recorrido."
    }
}
