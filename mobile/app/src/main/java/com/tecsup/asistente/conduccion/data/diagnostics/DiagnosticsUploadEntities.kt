package com.tecsup.asistente.conduccion.data.diagnostics

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "diagnostics_upload_session", primaryKeys = ["clientSessionId"],
    indices = [Index("ownerKey"), Index("state")])
data class DiagnosticsUploadSessionEntity(
    val clientSessionId: String,
    val ownerKey: String,
    val deviceCode: String,
    val startedAtMillis: Long,
    val state: String = "ACTIVE",
)

@Entity(tableName = "diagnostics_upload_sample", primaryKeys = ["clientSessionId", "sequenceNumber"],
    foreignKeys = [ForeignKey(entity = DiagnosticsUploadSessionEntity::class,
        parentColumns = ["clientSessionId"], childColumns = ["clientSessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("clientSessionId")])
data class DiagnosticsUploadSampleEntity(
    val clientSessionId: String,
    val sequenceNumber: Long,
    val sampledAtMillis: Long,
    val axRaw: Int, val ayRaw: Int, val azRaw: Int,
    val gxRaw: Int, val gyRaw: Int, val gzRaw: Int,
)
