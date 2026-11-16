package com.tecsup.asistente.conduccion.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "maneuver_alerts",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("tripId")],
)
data class ManeuverAlertEntity(
    @PrimaryKey val id: String,
    val tripId: Long,
    val eventIds: String,
    val occurredAt: Long,
    val testOnly: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val locationCapturedAt: Long?,
    val locationStatus: String,
)

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE maneuver_alerts (
                id TEXT NOT NULL PRIMARY KEY, tripId INTEGER NOT NULL, eventIds TEXT NOT NULL,
                occurredAt INTEGER NOT NULL, testOnly INTEGER NOT NULL, latitude REAL,
                longitude REAL, locationCapturedAt INTEGER, locationStatus TEXT NOT NULL,
                FOREIGN KEY(tripId) REFERENCES trips(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_maneuver_alerts_tripId ON maneuver_alerts(tripId)")
    }
}
