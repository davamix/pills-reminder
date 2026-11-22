package io.github.davamix.pillsreminder.debug

import io.github.davamix.pillsreminder.core.model.DurationUnit
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineDraft
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentDraft
import io.github.davamix.pillsreminder.core.model.TreatmentId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** What a test treatment is made of, as the debug screen sets it (P5 SPEC §4.3.9). */
data class TestSettings(
    /** Every so many minutes. The default fits a round of 4 alerts, at 0, 10, 20 and 30 minutes, before the take is Missed. */
    val intervalMinutes: Int = 45,
    /** The first take, so many minutes after now; 0 is now, so it falls due at once. */
    val firstInMinutes: Int = 2,
    /** Two medicines, which fall due together (RM-4). */
    val twoMedicines: Boolean = false,
    /** No end, rather than 1 day. */
    val noEnd: Boolean = false,
    /** Each medicine with the instructions [INSTRUCTIONS], rather than none. */
    val instructions: Boolean = false,
)

/**
 * Test treatments for the debug tools (plan D-15): a treatment named "Test", with "Test A" and maybe "Test B", "1 tablet"
 * each, in minutes. Their IDs start with [PREFIX], so the debug screen ends or deletes only its own. Debug builds only.
 */
object TestSchedules {
    const val PREFIX = "debug-"

    /** The test medicines' instructions, when asked for: `TR-6`'s example. */
    const val INSTRUCTIONS = "with food"

    fun isTest(treatment: Treatment): Boolean = treatment.id.value.startsWith(PREFIX)

    /** A new test treatment, with new IDs, whose first take is [TestSettings.firstInMinutes] whole minutes after [now]. */
    fun draft(
        settings: TestSettings,
        now: ZonedDateTime,
    ): TreatmentDraft {
        require(settings.intervalMinutes >= 1) { "The interval is at least 1 minute, not ${settings.intervalMinutes}" }
        require(settings.firstInMinutes >= 0) { "The first take can't be in the past, not ${settings.firstInMinutes}" }
        val first = now.toLocalDateTime().truncatedTo(ChronoUnit.MINUTES).plusMinutes(settings.firstInMinutes.toLong())
        val names = if (settings.twoMedicines) listOf("Test A", "Test B") else listOf("Test A")
        val medicines =
            names.map { name ->
                MedicineDraft(
                    id = MedicineId(PREFIX + UUID.randomUUID()),
                    name = name,
                    dose = "1 tablet",
                    instructions = if (settings.instructions) INSTRUCTIONS else "",
                    firstTake = first,
                    intervalAmount = settings.intervalMinutes,
                    intervalUnit = IntervalUnit.MINUTES,
                    durationAmount = if (settings.noEnd) null else 1,
                    durationUnit = DurationUnit.DAYS,
                    noEnd = settings.noEnd,
                )
            }
        return TreatmentDraft(TreatmentId(PREFIX + UUID.randomUUID()), "Test", "", medicines)
    }
}
