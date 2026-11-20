package io.github.davamix.pillsreminder.data

import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.Interval
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.Schedule
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentId
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

// Spec §4.4 as test data on a device, with made-up IDs: the same as the unit tests' StoreExample.example, which
// instrumented tests can't see (P3-D5). The repo is public: no real treatments.
object DeviceExample {
    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")

    fun nov(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): LocalDateTime = LocalDateTime.of(2026, 11, day, hour, minute)

    fun clockAt(local: LocalDateTime): Clock = Clock.fixed(local.atZone(madrid).toInstant(), madrid)

    val treatmentId = TreatmentId("example")
    val amoxicillinId = MedicineId("amoxicillin")
    val atroventId = MedicineId("atrovent")

    val treatment =
        Treatment(
            id = treatmentId,
            name = "Example",
            notes = "",
            medicines =
                listOf(
                    Medicine(
                        amoxicillinId,
                        "Amoxicillin",
                        "1 capsule",
                        "",
                        Schedule(nov(23, 8), Interval(8, IntervalUnit.HOURS), MedicineDuration.Days(7)),
                    ),
                    Medicine(
                        atroventId,
                        "Atrovent",
                        "3 puffs",
                        "",
                        Schedule(nov(23, 8), Interval(8, IntervalUnit.HOURS), MedicineDuration.Days(5)),
                    ),
                ),
        )

    /** Amoxicillin's 08:00 take Taken at 08:05, and Atrovent's skipped (P3 SPEC §4.3.1). */
    val example =
        PillsData(
            treatments = listOf(treatment),
            answers =
                mapOf(
                    TakeKey(amoxicillinId, nov(23, 8)) to Answer.Taken(nov(23, 8, 5)),
                    TakeKey(atroventId, nov(23, 8)) to Answer.Skipped,
                ),
        )
}
