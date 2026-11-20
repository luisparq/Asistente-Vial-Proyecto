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
        const val DATABASE_VERSION = 3
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
        if (oldVersion < 3) {
            for ((type, phrases) in maneuverPhrases) {
                db.delete(TABLE_NAME, "$COL_EVENT_TYPE = ?", arrayOf(type))
                insertPhrases(db, phrases.map { type to it })
            }
        }
    }

    private fun seedPhrases(db: SQLiteDatabase) {
        insertPhrases(db, maneuverPhrases.flatMap { (type, phrases) -> phrases.map { type to it } } +
            listOf("START_TRIP" to "Hola, soy AVIGEM. Te acompañaré durante tu recorrido."))
    }

    private fun insertPhrases(db: SQLiteDatabase, initialPhrases: List<Pair<String, String>>) {
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
}

internal fun defaultFallback(eventType: String): String =
    maneuverPhrases[eventType]?.first() ?: "Hola, soy AVIGEM. Te acompañaré durante tu recorrido."

private val maneuverPhrases = mapOf(
    "SHARP_TURN" to listOf(
        "Gira con suavidad.", "Reduce antes de la curva.", "Mueve el volante con calma.",
    ).map { "Hey, detecté un giro brusco. $it" },
    "HARSH_ACCELERATION" to listOf(
        "Acelera poco a poco.", "Pisa el pedal con suavidad.", "Mantén un ritmo constante.",
    ).map { "Hey, detecté una aceleración brusca. $it" },
    "HARSH_BRAKING" to listOf(
        "Mantén tu distancia.", "Anticipa la frenada.", "Frena de forma gradual.",
    ).map { "Hey, detecté un frenado brusco. $it" },
)
