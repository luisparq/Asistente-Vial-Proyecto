package com.tecsup.asistente.conduccion.data.sync

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

object RetryPolicy {
    fun state(code: Int): String = when {
        code == 401 || code == 403 -> "BLOCKED_AUTH"
        code == 409 || code == 404 -> "BLOCKED_CONFLICT"
        code == 408 || code == 429 || code >= 500 || code == 0 -> "RETRY_WAIT"
        else -> "BLOCKED_VALIDATION"
    }
    fun next(now: Long, attempts: Int, retryAfter: String?): Long {
        val wait = (30_000L * (1L shl attempts.coerceIn(0, 10))).coerceAtMost(6 * 60 * 60 * 1000L)
        val backoff = now + wait + Random.nextLong(wait / 4 + 1)
        val server = retryAfter?.toLongOrNull()?.coerceIn(0, 31_536_000)?.let { now + it * 1000 }
            ?: runCatching { ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        return maxOf(backoff, server ?: 0)
    }
}
