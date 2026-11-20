package io.github.davamix.pillsreminder.core.actions

import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.schedule.MedicineTakes
import io.github.davamix.pillsreminder.core.schedule.Take
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.takesOf
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// The answers (SPEC §4.3.8). Each checks the take's state at now, and returns null, changing nothing, when the change
// isn't allowed. Only the last answered take of a medicine can be changed (TK-5). A Missed take can't be answered.

/**
 * Marks the take Taken now (TK-2): a Due take; the next take while it is Pending and can be marked early (TK-4); or the
 * last answered take when it is Skipped (TK-5).
 */
fun PillsData.markTaken(
    key: TakeKey,
    now: ZonedDateTime,
): PillsData? =
    answer(key, now, Answer.Taken(now.wholeMinutes())) { takes, take, isLast ->
        when (take.state) {
            TakeState.DUE -> true
            TakeState.PENDING -> takes.canMarkEarly(key.plannedAt, now)
            TakeState.SKIPPED -> isLast
            TakeState.TAKEN, TakeState.MISSED -> false
        }
    }

/** Skips the take: a Due take, or the last answered take when it is Taken (TK-5). */
fun PillsData.skip(
    key: TakeKey,
    now: ZonedDateTime,
): PillsData? =
    answer(key, now, Answer.Skipped) { _, take, isLast ->
        take.state == TakeState.DUE || (take.state == TakeState.TAKEN && isLast)
    }

/** Clears the answer of the last answered take (TK-5): it is Pending, Due or Missed again, by its times. */
fun PillsData.clearAnswer(
    key: TakeKey,
    now: ZonedDateTime,
): PillsData? {
    val (_, _, isLast) = find(key, now) ?: return null
    return if (isLast) copy(answers = answers - key) else null
}

/** Corrects the time the last answered take was taken (TK-2), to a time no later than now, in whole minutes. */
fun PillsData.setTakenTime(
    key: TakeKey,
    at: LocalDateTime,
    now: ZonedDateTime,
): PillsData? {
    val time = at.truncatedTo(ChronoUnit.MINUTES)
    return answer(key, now, Answer.Taken(time)) { _, take, isLast ->
        take.state == TakeState.TAKEN && isLast && time <= now.toLocalDateTime()
    }
}

private fun PillsData.answer(
    key: TakeKey,
    now: ZonedDateTime,
    answer: Answer,
    allowed: (MedicineTakes, Take, Boolean) -> Boolean,
): PillsData? {
    val (takes, take, isLast) = find(key, now) ?: return null
    return if (allowed(takes, take, isLast)) copy(answers = answers + (key to answer)) else null
}

/** The medicine's takes, the take, and whether it is the medicine's last answered take; `null` if there's no take. */
private fun PillsData.find(
    key: TakeKey,
    now: ZonedDateTime,
): Triple<MedicineTakes, Take, Boolean>? {
    val takes = takesOf(key.medicineId) ?: return null
    val take = takes.take(key.plannedAt, now) ?: return null
    return Triple(takes, take, takes.lastAnswered(now)?.key == key)
}
