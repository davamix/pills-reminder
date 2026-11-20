package io.github.davamix.pillsreminder.core.model

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.nov
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ValidationTest {
    private val draft = Example.treatment.toDraft()
    private val amoxicillin = draft.medicines[0]
    private val id = amoxicillin.id

    private fun TreatmentDraft.withAmoxicillin(change: MedicineDraft.() -> MedicineDraft) =
        copy(medicines = listOf(amoxicillin.change(), medicines[1]))

    @Test
    fun `the example treatment is valid`() {
        assertEquals(emptyList<ValidationError>(), draft.validate(allowMinutes = false))
    }

    @Test
    fun `a treatment and a medicine need a name, and blank names count as missing`() {
        val errors = draft.copy(name = "   ").withAmoxicillin { copy(name = " \t") }.validate(allowMinutes = false)

        assertEquals(
            listOf(ValidationError.TreatmentNameMissing, ValidationError.MedicineNameMissing(id)),
            errors,
        )
    }

    @Test
    fun `a treatment needs at least one medicine`() {
        assertEquals(
            listOf(ValidationError.NoMedicine),
            draft.copy(medicines = emptyList()).validate(allowMinutes = false),
        )
    }

    @Test
    fun `every error is reported at once`() {
        val errors =
            draft
                .copy(name = "", medicines = listOf(amoxicillin.copy(name = "", intervalAmount = 0, durationAmount = null)))
                .validate(allowMinutes = false)

        assertEquals(
            listOf(
                ValidationError.TreatmentNameMissing,
                ValidationError.MedicineNameMissing(id),
                ValidationError.IntervalInvalid(id),
                ValidationError.DurationInvalid(id),
            ),
            errors,
        )
    }

    @Test
    fun `an interval must be present and at least 1`() {
        for (amount in listOf(null, 0, -3)) {
            assertEquals(
                listOf(ValidationError.IntervalInvalid(id)),
                amoxicillin.copy(intervalAmount = amount).validate(allowMinutes = false),
                "interval amount $amount",
            )
        }
    }

    @Test
    fun `a duration must be present and at least 1`() {
        for (amount in listOf(null, 0, -1)) {
            assertEquals(
                listOf(ValidationError.DurationInvalid(id)),
                amoxicillin.copy(durationAmount = amount).validate(allowMinutes = false),
                "duration amount $amount",
            )
        }
    }

    @Test
    fun `a duration with no end needs no amount`() {
        assertEquals(
            emptyList<ValidationError>(),
            amoxicillin.copy(noEnd = true, durationAmount = null).validate(allowMinutes = false),
        )
    }

    @Test
    fun `hours, days and weeks are always allowed, and minutes only when allowed`() {
        for (unit in listOf(IntervalUnit.HOURS, IntervalUnit.DAYS, IntervalUnit.WEEKS)) {
            assertEquals(emptyList<ValidationError>(), amoxicillin.copy(intervalUnit = unit).validate(false), "$unit")
        }
        val minutes = amoxicillin.copy(intervalUnit = IntervalUnit.MINUTES)

        assertEquals(listOf(ValidationError.MinutesNotAllowed(id)), minutes.validate(allowMinutes = false))
        assertEquals(emptyList<ValidationError>(), minutes.validate(allowMinutes = true))
    }

    @Test
    fun `weeks are a valid duration unit`() {
        assertEquals(
            emptyList<ValidationError>(),
            amoxicillin.copy(durationAmount = 12, durationUnit = DurationUnit.WEEKS).validate(allowMinutes = false),
        )
    }

    @Test
    fun `the dose and the instructions may be empty`() {
        assertEquals(
            emptyList<ValidationError>(),
            amoxicillin.copy(dose = "", instructions = "").validate(allowMinutes = false),
        )
    }

    @Test
    fun `there is no upper limit below the largest Int`() {
        val largest = amoxicillin.copy(intervalAmount = Int.MAX_VALUE, durationAmount = Int.MAX_VALUE)

        assertEquals(emptyList<ValidationError>(), largest.validate(allowMinutes = false))
        assertEquals(Int.MAX_VALUE, largest.toSchedule().interval.amount)
    }

    @Test
    fun `a first take in the past is valid`() {
        assertEquals(
            emptyList<ValidationError>(),
            amoxicillin.copy(firstTake = nov(1, 8)).validate(allowMinutes = false),
        )
    }

    @Test
    fun `a medicine turned into a draft and back keeps its schedule`() {
        for (medicine in Example.treatment.medicines) {
            assertEquals(medicine.schedule, medicine.toDraft().toSchedule())
        }
        val noEnd = Example.amoxicillin.copy(schedule = Example.amoxicillin.schedule.copy(duration = MedicineDuration.NoEnd))

        assertEquals(noEnd.schedule, noEnd.toDraft().toSchedule())
    }

    @Test
    fun `a draft's first take is truncated to whole minutes`() {
        val schedule = amoxicillin.copy(firstTake = nov(23, 8).withSecond(42).withNano(7)).toSchedule()

        assertEquals(nov(23, 8), schedule.firstTake)
    }

    @Test
    fun `a schedule refuses amounts below 1 and a first take with seconds`() {
        assertThrows<IllegalArgumentException> { Interval(0, IntervalUnit.HOURS) }
        assertThrows<IllegalArgumentException> { MedicineDuration.Days(0) }
        assertThrows<IllegalArgumentException> { MedicineDuration.Weeks(-1) }
        assertThrows<IllegalArgumentException> {
            Schedule(nov(23, 8).withSecond(1), Interval(8, IntervalUnit.HOURS), MedicineDuration.NoEnd)
        }
    }
}
