package com.tecsup.asistente.conduccion.domain.processing

data class ManeuverAlertRule(
    val threshold: Int = 4,
    val windowMillis: Long = 120_000,
    val groupingMillis: Long = 2_000,
    val cooldownMillis: Long = 120_000,
) {
    init {
        require(threshold > 0 && windowMillis > 0 && groupingMillis >= 0 && cooldownMillis >= 0)
    }

    fun triggeringIds(
        events: List<IdentifiedManeuver>,
        latest: IdentifiedManeuver,
        previousAlertAt: Long?,
    ): List<String> {
        if (previousAlertAt != null && latest.occurredAt - previousAlertAt < cooldownMillis) return emptyList()

        val seen = mutableSetOf<String>()
        val accepted = mutableListOf<IdentifiedManeuver>()
        for (event in events.sortedBy { it.occurredAt }) {
            if (event.occurredAt > latest.occurredAt || !seen.add(event.eventId)) continue
            if (previousAlertAt != null && (event.occurredAt <= previousAlertAt ||
                    event.occurredAt - previousAlertAt < cooldownMillis)) continue
            val previous = accepted.lastOrNull()
            if (previous == null || event.occurredAt - previous.occurredAt >= groupingMillis) accepted.add(event)
        }

        val window = accepted.filter { latest.occurredAt - it.occurredAt <= windowMillis }
        return if (window.size == threshold && window.lastOrNull() == latest) window.map { it.eventId } else emptyList()
    }
}

data class IdentifiedManeuver(val eventId: String, val occurredAt: Long)
