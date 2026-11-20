package io.github.davamix.pillsreminder.core.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The instant this planned time falls due in [zone] (plan D-6). A time in a daylight saving gap moves forward by the
 * gap: in Europe/Madrid on 29 March 2026, 02:30 falls due at 03:30. In the repeated hour, the earlier instant counts:
 * on 25 October 2026, 02:30 falls due at the first 02:30.
 *
 * Compare planned times with now through this, never as local times: when the clock goes back, a take that fell due at
 * the first 02:30 would otherwise turn Pending again.
 */
fun LocalDateTime.dueAt(zone: ZoneId): Instant = atZone(zone).toInstant()

/**
 * The first take the editor suggests (TR-4): the first whole hour after now that exists on the phone's clock, and that
 * falls due after now. 10:30 gives 11:00, 10:00 gives 11:00, and 01:30 on 29 March 2026 in Europe/Madrid gives 03:00.
 */
fun suggestedFirstTake(now: ZonedDateTime): LocalDateTime {
    val zone = now.zone
    var candidate = now.toLocalDateTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
    while (zone.rules.getValidOffsets(candidate).isEmpty() || candidate.dueAt(zone) <= now.toInstant()) {
        candidate = candidate.plusHours(1)
    }
    return candidate
}
