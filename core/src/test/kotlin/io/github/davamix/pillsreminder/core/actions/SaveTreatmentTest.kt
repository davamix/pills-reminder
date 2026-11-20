package io.github.davamix.pillsreminder.core.actions

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.assertInvariants
import io.github.davamix.pillsreminder.core.at
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.EarlierSchedule
import io.github.davamix.pillsreminder.core.model.Interval
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineDraft
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentDraft
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.medicine
import io.github.davamix.pillsreminder.core.model.toDraft
import io.github.davamix.pillsreminder.core.model.treatment
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import io.github.davamix.pillsreminder.core.schedule.MedicineTakes
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.TreatmentStatus
import io.github.davamix.pillsreminder.core.schedule.status
import io.github.davamix.pillsreminder.core.schedule.takesOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZonedDateTime

class SaveTreatmentTest {
    private val draft = Example.treatment.toDraft()
    private val amoxicillinId = Example.amoxicillinId

    private fun TreatmentDraft.amoxicillin(change: MedicineDraft.() -> MedicineDraft) =
        copy(medicines = medicines.map { if (it.id == amoxicillinId) it.change() else it })

    private fun amox(plannedAt: LocalDateTime) = TakeKey(amoxicillinId, plannedAt)

    private fun taken(vararg times: LocalDateTime) = times.associate { amox(it) to Answer.Taken(it) }

    private fun PillsData.amoxicillinTakes(): MedicineTakes = checkNotNull(takesOf(amoxicillinId))

    private fun MedicineTakes.planned(now: ZonedDateTime): List<LocalDateTime> =
        takes(LocalDateTime.MIN, at(12, 31, 0), now).map { it.key.plannedAt }.toList()

    /** SPEC §4.3.6: four takes answered, then on Tue 24 Nov at 10:00 the interval changes from 8 h to 12 h. */
    private val answeredFour = Example.data.copy(answers = taken(nov(23, 8), nov(23, 16), nov(24, 0), nov(24, 8)))
    private val tuesday1000 = nowAt(nov(24, 10))
    private val everyTwelveHours = draft.amoxicillin { copy(intervalAmount = 12) }

    @Test
    fun `a new treatment is added at the end, with its text trimmed and the dose as typed`() {
        val typed =
            draft
                .copy(name = "  Example ", notes = " notes ")
                .amoxicillin { copy(name = " Amoxicillin ", dose = " 1/3 tablet ", instructions = " with food ") }
        val first = PillsData().saveTreatment(typed, nowAt(nov(20, 12)))
        val second = first.saveTreatment(draft.copy(id = TreatmentId("second"), medicines = listOf(otherMedicine())), nowAt(nov(20, 12)))

        val treatment = checkNotNull(first.treatment(Example.treatmentId))
        assertEquals("Example" to "notes", treatment.name to treatment.notes)
        val medicine = treatment.medicines.first()
        assertEquals(listOf("Amoxicillin", "1/3 tablet", "with food"), listOf(medicine.name, medicine.dose, medicine.instructions))
        assertEquals(emptyList<EarlierSchedule>(), medicine.earlierSchedules)
        assertEquals(listOf(Example.treatmentId, TreatmentId("second")), second.treatments.map { it.id })
    }

    private fun otherMedicine() = draft.medicines.first().copy(id = MedicineId("other"))

    @Test
    fun `an edit before the first take replaces the schedule, with no earlier one`() {
        val saved = Example.data.saveTreatment(everyTwelveHours, nowAt(nov(22, 12)))
        val medicine = checkNotNull(saved.medicine(amoxicillinId)).second

        assertEquals(Interval(12, IntervalUnit.HOURS), medicine.schedule.interval)
        assertEquals(emptyList<EarlierSchedule>(), medicine.earlierSchedules)
        assertEquals(14L, saved.amoxicillinTakes().count)
    }

    @Test
    fun `an edit halfway keeps the takes up to it, and works out the rest from the new values`() {
        val saved = answeredFour.saveTreatment(everyTwelveHours, tuesday1000)
        val medicine = checkNotNull(saved.medicine(amoxicillinId)).second
        val takes = saved.amoxicillinTakes()
        val newTakes = listOf(nov(24, 20)) + (25..29).flatMap { listOf(nov(it, 8), nov(it, 20)) }

        assertEquals(listOf(EarlierSchedule(Example.amoxicillin.schedule, until = nov(24, 10))), medicine.earlierSchedules)
        assertEquals(listOf(nov(23, 8), nov(23, 16), nov(24, 0), nov(24, 8)) + newTakes, takes.planned(tuesday1000))
        assertEquals(15L, takes.count)
        assertEquals(nov(29, 20), takes.last)
        assertEquals(4, takes.answeredCount)
        assertInvariants(saved, tuesday1000)
    }

    @Test
    fun `a take answered early is kept, and the new takes start after it`() {
        val data = Example.data.copy(answers = taken(nov(23, 8)) + (amox(nov(23, 16)) to Answer.Taken(nov(23, 10, 30))))
        val now = nowAt(nov(23, 10, 45))
        val saved = data.saveTreatment(draft.amoxicillin { copy(intervalAmount = 6) }, now)

        assertEquals(listOf(nov(23, 8), nov(23, 16), nov(23, 20), nov(24, 2)), saved.amoxicillinTakes().planned(now).take(4))
        assertEquals(data.answers, saved.answers)
        assertInvariants(saved, now)
    }

    @Test
    fun `a take Due at the edit is kept, and becomes Missed when the new schedule's next take falls due`() {
        val data = Example.data.copy(answers = taken(nov(23, 8)))
        val now = nowAt(nov(23, 16, 5))
        val saved = data.saveTreatment(everyTwelveHours, now)
        val takes = saved.amoxicillinTakes()

        assertEquals(listOf(nov(23, 8), nov(23, 16), nov(23, 20)), takes.planned(now).take(3))
        assertEquals(TakeState.DUE, takes.take(nov(23, 16), now)?.state)
        assertEquals(nov(23, 20), takes.take(nov(23, 16), now)?.missedAt)
        assertEquals(TakeState.MISSED, takes.take(nov(23, 16), nowAt(nov(23, 20)))?.state)
        assertInvariants(saved, now)
    }

    @Test
    fun `two edits in a row keep both earlier schedules`() {
        val first = answeredFour.saveTreatment(everyTwelveHours, tuesday1000)
        val answered = first.copy(answers = first.answers + taken(nov(24, 20), nov(25, 8), nov(25, 20), nov(26, 8)))
        val thursday = nowAt(nov(26, 9))
        val daily = draft.amoxicillin { copy(intervalAmount = 1, intervalUnit = IntervalUnit.DAYS) }
        val saved = answered.saveTreatment(daily, thursday)
        val medicine = checkNotNull(saved.medicine(amoxicillinId)).second
        val takes = saved.amoxicillinTakes()

        assertEquals(listOf(nov(24, 10), nov(26, 9)), medicine.earlierSchedules.map { it.until })
        assertEquals(11L, takes.count)
        assertEquals(listOf(nov(27, 8), nov(28, 8), nov(29, 8)), takes.planned(thursday).takeLast(3))
        assertEquals(8, takes.answeredCount)
        assertInvariants(saved, thursday)
    }

    @Test
    fun `an unchanged schedule adds no earlier schedule, and the new name applies to every take`() {
        val saved = answeredFour.saveTreatment(draft.amoxicillin { copy(name = "Amoxicillin 500") }, tuesday1000)
        val medicine = checkNotNull(saved.medicine(amoxicillinId)).second

        assertEquals("Amoxicillin 500", medicine.name)
        assertEquals(emptyList<EarlierSchedule>(), medicine.earlierSchedules)
        assertEquals(answeredFour.answers, saved.answers)
    }

    @Test
    fun `a removed medicine is deleted with its answers`() {
        val atrovent = TakeKey(Example.atroventId, nov(23, 8))
        val data = answeredFour.copy(answers = answeredFour.answers + (atrovent to Answer.Skipped))
        val saved = data.saveTreatment(draft.copy(medicines = draft.medicines.filter { it.id == amoxicillinId }), tuesday1000)

        assertNull(saved.medicine(Example.atroventId))
        assertFalse(atrovent in saved.answers)
        assertEquals(answeredFour.answers, saved.answers)
        assertInvariants(saved, tuesday1000)
    }

    @Test
    fun `an answer given while the editor is open survives the save`() {
        val data = Example.data.copy(answers = taken(nov(23, 8)))
        val opened = checkNotNull(data.treatment(Example.treatmentId)).toDraft()
        val answered = checkNotNull(data.markTaken(amox(nov(23, 16)), nowAt(nov(23, 16, 1))))
        val saved = answered.saveTreatment(opened.copy(notes = "after food"), nowAt(nov(23, 16, 2)))

        assertEquals(Answer.Taken(nov(23, 16, 1)), saved.answers[amox(nov(23, 16))])
        assertEquals("after food", saved.treatment(Example.treatmentId)?.notes)
    }

    @Test
    fun `the preview equals the saved result`() {
        val medicineDraft = everyTwelveHours.medicines.first { it.id == amoxicillinId }
        val preview = answeredFour.previewMedicine(Example.treatmentId, medicineDraft, tuesday1000)
        val saved = answeredFour.saveTreatment(everyTwelveHours, tuesday1000).amoxicillinTakes()

        assertEquals(saved.count to saved.last, preview.count to preview.last)
        assertEquals(saved.planned(tuesday1000), preview.planned(tuesday1000))
        assertEquals(21L, PillsData().previewMedicine(TreatmentId("new"), draft.medicines.first(), tuesday1000).count)
    }

    @Test
    fun `an ended treatment can still be saved, and its takes after the end stay removed`() {
        val ended = checkNotNull(Example.data.endTreatment(Example.treatmentId, nowAt(nov(25, 12))))
        val now = nowAt(nov(26, 9))
        val saved = ended.saveTreatment(draft.copy(name = "Example 2").amoxicillin { copy(intervalAmount = 4) }, now)

        assertEquals("Example 2", saved.treatment(Example.treatmentId)?.name)
        assertEquals(nov(25, 12), saved.treatment(Example.treatmentId)?.endedAt)
        assertEquals(TreatmentStatus.FINISHED, saved.status(Example.treatmentId, now))
        assertEquals(7L, saved.amoxicillinTakes().count)
        assertEquals(nov(25, 8), saved.amoxicillinTakes().last)
        assertInvariants(saved, now)
    }

    @Test
    fun `an invalid draft is refused, and the message holds no treatment content`() {
        val invalid = draft.copy(notes = "private note").amoxicillin { copy(intervalAmount = 0) }
        val error = assertThrows<IllegalArgumentException> { Example.data.saveTreatment(invalid, tuesday1000) }
        val message = checkNotNull(error.message)

        assertTrue("IntervalInvalid" in message && Example.amoxicillinId.value in message, message)
        for (content in listOf("Amoxicillin", "Atrovent", "Example", "1 capsule", "3 puffs", "private note")) {
            assertFalse(content in message, "the message mentions $content")
        }
    }

    @Test
    fun `a medicine can't be in a draft twice, nor belong to another treatment`() {
        val twice = draft.copy(medicines = draft.medicines + draft.medicines.first())
        val other = draft.copy(id = TreatmentId("other"), medicines = listOf(draft.medicines.first()))

        assertThrows<IllegalArgumentException> { Example.data.saveTreatment(twice, tuesday1000) }
        assertThrows<IllegalArgumentException> { Example.data.saveTreatment(other, tuesday1000) }
    }
}
