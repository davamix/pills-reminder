package io.github.davamix.pillsreminder.core

import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.core.time.dueAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Checks the invariants of SPEC §4.3.10 at [now]. Medicines with no end are checked up to 60 days after now.
 */
fun assertInvariants(
    data: PillsData,
    now: ZonedDateTime,
) {
    for (key in data.answers.keys) {
        val takes = data.takesOf(key.medicineId)
        assertTrue(takes != null && key.plannedAt in takes, "the answer to ${key.medicineId.value} at ${key.plannedAt} has no take")
    }
    val ids = data.treatments.flatMap { treatment -> treatment.medicines.map { it.id } }
    assertEquals(ids.size, ids.toSet().size, "medicine IDs are unique")
    for (id in ids) {
        val takes = checkNotNull(data.takesOf(id))
        val horizon = now.toLocalDateTime().plusDays(60)
        val all = takes.takes(LocalDateTime.MIN, horizon, now).toList()
        val planned = all.map { it.key.plannedAt }
        assertEquals(planned.sorted().distinct(), planned, "${id.value}: planned times are unique and in order")
        assertTrue(all.count { it.state == TakeState.DUE } <= 1, "${id.value}: at most one Due take at $now")
        val ahead = takes.answers.keys.count { it.dueAt(now.zone) > now.toInstant() }
        assertTrue(ahead <= 1, "${id.value}: at most one take answered ahead at $now")
        val last = takes.last
        if (takes.count != null && (last == null || last < horizon)) {
            assertEquals(takes.count, planned.size.toLong(), "${id.value}: the count")
        }
    }
}
