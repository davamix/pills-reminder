package io.github.davamix.pillsreminder.ui.editor

import androidx.lifecycle.SavedStateHandle
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.DurationUnit
import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.model.ValidationError
import io.github.davamix.pillsreminder.core.model.treatment
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.data.StoreExample
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import io.github.davamix.pillsreminder.reminders.FakeRepository
import io.github.davamix.pillsreminder.reminders.MutableClock
import io.github.davamix.pillsreminder.ui.TestTicks
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.deps
import io.github.davamix.pillsreminder.ui.next
import io.github.davamix.pillsreminder.ui.vmTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/** The two editors (TR-2–TR-8, TR-10, TR-13, SC-2, SC-5, D-15, P6 SPEC §4.3.6–§4.3.10, P6-D1, P6-D6). */
class EditorViewModelTest {
    private val clock = MutableClock(nowAt(nov(22, 10, 30)))
    private val ticks = TestTicks()

    private fun empty() = FakeRepository(StoredData.Readable(PillsData()))

    private fun example(vararg answers: Pair<TakeKey, Answer>) =
        FakeRepository(StoredData.Readable(PillsData(listOf(StoreExample.treatment), answers.toMap())))

    private fun viewModel(
        repository: TreatmentRepository,
        treatmentId: TreatmentId? = null,
        allowMinutes: Boolean = true,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = EditorViewModel(deps(repository, clock, ticks, allowMinutes), treatmentId, saved)

    private val MedicineEditing.name get() = form.draft.name

    private suspend fun EditorViewModel.ready(predicate: (EditorState) -> Boolean = { true }) = state.next(predicate)

    private suspend fun EditorViewModel.medicine(predicate: (MedicineEditing) -> Boolean = { true }) =
        checkNotNull(ready { it.medicine != null && predicate(it.medicine!!) }.medicine)

    /** Fills the open medicine editor with a medicine of the §4.4 example. */
    private fun EditorViewModel.fill(
        name: String,
        dose: String,
        days: Int,
    ) {
        setMedicineName(name)
        setDose(dose)
        setInterval("8")
        setIntervalUnit(IntervalUnit.HOURS)
        setDuration(days.toString())
        setDurationUnit(DurationUnit.DAYS)
        setFirstTakeDate(nov(23, 0).toLocalDate())
        setFirstTakeTime(nov(23, 8).toLocalTime())
    }

    @Test
    fun `a new treatment starts empty, with nothing to confirm and no errors shown`() =
        vmTest {
            val state = viewModel(empty()).ready()

            assertTrue(state.isNew)
            assertEquals("", state.draft.name)
            assertTrue(state.draft.medicines.isEmpty())
            assertFalse(state.hasChanges)
            assertFalse(state.showErrors)
            assertFalse(state.unreadable)
            assertNull(state.medicine)
        }

    @Test
    fun `a new medicine starts at the next full hour, every 8 hours for 7 days, with its takes`() =
        vmTest {
            val viewModel = viewModel(empty())
            viewModel.ready()

            viewModel.openMedicine(null, "entry-1")

            val medicine = viewModel.medicine()
            assertTrue(medicine.isNew)
            assertEquals(nov(22, 11), medicine.form.draft.firstTake)
            assertEquals(listOf("8", "7"), listOf(medicine.form.intervalText, medicine.form.durationText))
            assertEquals(Preview(21, nov(29, 3)), medicine.preview)
            assertFalse(medicine.hasChanges)
            assertFalse(medicine.firstTakeInPast)
        }

    @Test
    fun `the example's medicines show 21 and 15 takes, and their last ones, before saving`() =
        vmTest {
            val viewModel = viewModel(empty())
            viewModel.ready()

            viewModel.openMedicine(null, "entry-1")
            viewModel.fill("Amoxicillin", "1 capsule", days = 7)
            assertEquals(Preview(21, nov(30, 0)), viewModel.medicine { it.form.draft.name == "Amoxicillin" }.preview)

            viewModel.setDuration("5")
            assertEquals(Preview(15, nov(28, 0)), viewModel.medicine { it.form.durationText == "5" }.preview)
        }

    @Test
    fun `a medicine with no end shows no end instead of a count`() =
        vmTest {
            val viewModel = viewModel(empty())
            viewModel.ready()
            viewModel.openMedicine(null, "entry-1")

            viewModel.setNoEnd(true)

            assertEquals(Preview(null, null), viewModel.medicine { it.form.draft.noEnd }.preview)
        }

    @Test
    fun `no error shows before Done, then each, and each goes once fixed`() =
        vmTest {
            val viewModel = viewModel(empty())
            viewModel.ready()
            viewModel.openMedicine(null, "entry-1")
            viewModel.setInterval("")
            viewModel.setDuration("")

            val before = viewModel.medicine { it.form.intervalText.isEmpty() && it.form.durationText.isEmpty() }
            assertFalse(before.showErrors)
            assertNull(before.preview)

            assertFalse(viewModel.medicineDone())
            val shown = viewModel.medicine { it.showErrors }
            val id = shown.form.draft.id
            assertEquals(
                listOf(
                    ValidationError.MedicineNameMissing(id),
                    ValidationError.IntervalInvalid(id),
                    ValidationError.DurationInvalid(id),
                ),
                shown.errors,
            )

            viewModel.setMedicineName("Amoxicillin")
            viewModel.setInterval("8")
            viewModel.setNoEnd(true)
            assertTrue(viewModel.medicine { it.form.draft.noEnd }.errors.isEmpty())
            assertTrue(viewModel.medicineDone())
            assertEquals(
                listOf("Amoxicillin"),
                viewModel
                    .ready { it.medicine == null }
                    .draft.medicines
                    .map { it.name },
            )
        }

    @Test
    fun `an amount keeps only its digits, and one too big for an Int is invalid`() =
        vmTest {
            val viewModel = viewModel(empty())
            viewModel.ready()
            viewModel.openMedicine(null, "entry-1")

            viewModel.setInterval("1a2 3")
            val digits = viewModel.medicine { it.form.intervalText == "123" }
            assertEquals(123, digits.form.draft.intervalAmount)

            viewModel.setInterval("2147483648")
            val tooBig = viewModel.medicine { it.form.intervalText == "2147483648" }
            assertNull(tooBig.form.draft.intervalAmount)
            assertTrue(tooBig.errors.any { it is ValidationError.IntervalInvalid })

            viewModel.setDuration("123456789012")
            assertEquals("1234567890", viewModel.medicine { it.form.durationText.length == 10 }.form.durationText)
        }

    @Test
    fun `a first take in the past says so, unless the medicine has started`() =
        vmTest {
            clock.at(nov(23, 10, 30))
            val viewModel = viewModel(empty())
            viewModel.ready()
            viewModel.openMedicine(null, "entry-1")

            viewModel.setFirstTakeTime(nov(23, 8).toLocalTime())
            viewModel.setFirstTakeDate(nov(23, 0).toLocalDate())
            assertTrue(viewModel.medicine { it.form.draft.firstTake == nov(23, 8) }.firstTakeInPast)

            viewModel.setFirstTakeDate(nov(24, 0).toLocalDate())
            assertFalse(viewModel.medicine { it.form.draft.firstTake == nov(24, 8) }.firstTakeInPast)
        }

    @Test
    fun `Save shows the treatment's errors, stores nothing, and they go once fixed`() =
        vmTest {
            val repository = empty()
            val viewModel = viewModel(repository)
            viewModel.ready()

            viewModel.save()

            val shown = viewModel.ready { it.showErrors }
            assertEquals(listOf(ValidationError.TreatmentNameMissing, ValidationError.NoMedicine), shown.errors)
            assertEquals(0, repository.writes)
            viewModel.setName("Example")
            assertEquals(listOf(ValidationError.NoMedicine), viewModel.ready { it.draft.name == "Example" }.errors)
        }

    @Test
    fun `a treatment with two medicines starting at different times is saved as typed, trimmed`() =
        vmTest {
            val repository = empty()
            val viewModel = viewModel(repository)
            val id = viewModel.ready().draft.id
            viewModel.setName(" Example ")
            viewModel.setNotes("From the GP")
            viewModel.openMedicine(null, "entry-1")
            viewModel.fill("Amoxicillin", "1/3 tablet", days = 7)
            viewModel.setInstructions("with food")
            assertTrue(viewModel.medicineDone())
            viewModel.openMedicine(null, "entry-2")
            viewModel.fill("Atrovent", "3 puffs", days = 5)
            viewModel.setFirstTakeTime(nov(23, 9).toLocalTime())
            assertTrue(viewModel.medicineDone())
            assertTrue(viewModel.ready { it.draft.medicines.size == 2 }.hasChanges)

            viewModel.save()
            advanceUntilIdle()

            assertEquals(id, viewModel.savedAs.value)
            val stored = checkNotNull(repository.current.treatment(id))
            assertEquals(listOf("Example", "From the GP"), listOf(stored.name, stored.notes))
            assertEquals(listOf("1/3 tablet", "3 puffs"), stored.medicines.map { it.dose })
            assertEquals("with food", stored.medicines.first().instructions)
            assertEquals(listOf(nov(23, 8), nov(23, 9)), stored.medicines.map { it.schedule.firstTake })
        }

    @Test
    fun `Done replaces the medicine it edited, Close keeps the draft as it was, and Remove takes it out`() =
        vmTest {
            val viewModel = viewModel(example(), StoreExample.treatmentId)
            viewModel.ready()

            viewModel.openMedicine(StoreExample.amoxicillinId, "entry-1")
            viewModel.setDose("2 capsules")
            assertTrue(viewModel.medicine { it.form.draft.dose == "2 capsules" }.hasChanges)
            viewModel.closeMedicine()
            assertEquals(
                "1 capsule",
                viewModel
                    .ready { it.medicine == null }
                    .draft.medicines
                    .first()
                    .dose,
            )
            assertFalse(viewModel.ready().hasChanges)

            viewModel.openMedicine(StoreExample.amoxicillinId, "entry-2")
            viewModel.setDose("2 capsules")
            assertTrue(viewModel.medicineDone())
            val done = viewModel.ready { it.medicine == null && it.hasChanges }
            assertEquals(listOf("2 capsules", "3 puffs"), done.draft.medicines.map { it.dose })

            viewModel.removeMedicine(StoreExample.atroventId)
            assertEquals(
                listOf("Amoxicillin"),
                viewModel
                    .ready { it.draft.medicines.size == 1 }
                    .draft.medicines
                    .map { it.name },
            )
        }

    @Test
    fun `an edit starts from the stored treatment, and says which medicines have started`() =
        vmTest {
            clock.at(nov(23, 10, 30))
            val state = viewModel(example(), StoreExample.treatmentId).ready()

            assertFalse(state.isNew)
            assertFalse(state.hasChanges)
            assertEquals("Example", state.draft.name)
            assertEquals(setOf(StoreExample.amoxicillinId, StoreExample.atroventId), state.started)
        }

    @Test
    fun `a medicine whose first take was marked Taken early has started, before it is due`() =
        vmTest {
            val early = TakeKey(StoreExample.amoxicillinId, nov(23, 8)) to Answer.Taken(nov(22, 10, 20))

            val state = viewModel(example(early), StoreExample.treatmentId).ready()

            assertEquals(setOf(StoreExample.amoxicillinId), state.started)
        }

    @Test
    fun `a stored medicine that hasn't started, or a new one, can be removed without asking`() =
        vmTest {
            val viewModel = viewModel(example(), StoreExample.treatmentId)
            viewModel.ready()
            viewModel.openMedicine(null, "entry-1")
            viewModel.fill("Ibuprofen", "1 tablet", days = 3)
            assertTrue(viewModel.medicineDone())

            assertTrue(viewModel.ready { it.draft.medicines.size == 3 }.started.isEmpty())
        }

    @Test
    fun `editing a started medicine says so, and its takes count those kept`() =
        vmTest {
            clock.at(nov(24, 10))
            val answered =
                listOf(nov(23, 8), nov(23, 16), nov(24, 0), nov(24, 8)).map {
                    TakeKey(StoreExample.amoxicillinId, it) to
                        Answer.Taken(it)
                }
            val repository = example(*answered.toTypedArray())
            val viewModel = viewModel(repository, StoreExample.treatmentId)
            viewModel.ready()
            viewModel.openMedicine(StoreExample.amoxicillinId, "entry-1")

            viewModel.setInterval("12")

            val medicine = viewModel.medicine { it.form.intervalText == "12" }
            assertTrue(medicine.started)
            assertFalse(medicine.firstTakeInPast)
            assertEquals(Preview(15, nov(29, 20)), medicine.preview)
            assertTrue(viewModel.medicineDone())
            viewModel.save()
            advanceUntilIdle()
            val takes = checkNotNull(repository.current.takesOf(StoreExample.amoxicillinId))
            assertEquals(listOf(15L, 4L), listOf(takes.count, takes.answeredCount.toLong()))
        }

    @Test
    fun `an answer given while the editor is open is kept by the save`() =
        vmTest {
            clock.at(nov(23, 16, 5))
            val repository = example()
            val viewModel = viewModel(repository, StoreExample.treatmentId)
            viewModel.ready()
            val atrovent1600 = TakeKey(StoreExample.atroventId, nov(23, 16))
            // Answered from the reminder while the editor is open.
            repository.markTaken(atrovent1600, clock)

            viewModel.setNotes("From the GP")
            viewModel.save()
            advanceUntilIdle()

            assertNotNull(viewModel.savedAs.value)
            assertEquals(Answer.Taken(nov(23, 16, 5)), repository.current.answers[atrovent1600])
            assertEquals(
                "From the GP",
                repository.current.treatments
                    .single()
                    .notes,
            )
        }

    @Test
    fun `data that can't be read is shown, and Save refuses with the message`() =
        vmTest {
            val viewModel = viewModel(InMemoryTreatmentRepository(StoredData.Unreadable))
            assertTrue(viewModel.ready().unreadable)
            viewModel.setName("Example")
            viewModel.openMedicine(null, "entry-1")
            viewModel.fill("Amoxicillin", "1 capsule", days = 7)
            assertTrue(viewModel.medicineDone())

            viewModel.save()
            advanceUntilIdle()

            assertEquals(ChangeProblem.UNREADABLE, viewModel.ready { it.problem != null }.problem)
            assertNull(viewModel.savedAs.value)
        }

    @Test
    fun `a failed write says not saved, and keeps the draft to try again`() =
        vmTest {
            val repository = example()
            val viewModel = viewModel(repository, StoreExample.treatmentId)
            viewModel.ready()
            viewModel.setName("Example 2")
            repository.failure = IOException("disk full")

            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.ready { it.problem != null }
            assertEquals(ChangeProblem.NOT_SAVED, state.problem)
            assertEquals("Example 2", state.draft.name)
            assertFalse(state.saving)
            assertNull(viewModel.savedAs.value)
            assertEquals(
                "Example",
                repository.current.treatments
                    .single()
                    .name,
            )

            viewModel.problemShown()
            repository.failure = null
            viewModel.save()
            advanceUntilIdle()
            assertEquals(StoreExample.treatmentId, viewModel.savedAs.value)
        }

    @Test
    fun `minutes are refused unless allowed, in debug builds`() =
        vmTest {
            val release = viewModel(empty(), allowMinutes = false)
            release.ready()
            release.openMedicine(null, "entry-1")
            release.setMedicineName("Test")
            release.setIntervalUnit(IntervalUnit.MINUTES)
            val refused = release.medicine { it.form.draft.intervalUnit == IntervalUnit.MINUTES }
            assertFalse(refused.allowMinutes)
            assertTrue(refused.errors.any { it is ValidationError.MinutesNotAllowed })
            assertNull(refused.preview)
            assertFalse(release.medicineDone())

            val debug = viewModel(empty(), allowMinutes = true)
            debug.ready()
            debug.openMedicine(null, "entry-1")
            debug.setMedicineName("Test")
            debug.setIntervalUnit(IntervalUnit.MINUTES)
            assertTrue(debug.medicine { it.form.draft.intervalUnit == IntervalUnit.MINUTES }.allowMinutes)
            assertTrue(debug.medicineDone())
        }

    @Test
    fun `the drafts survive the process ending, and the medicine editor keeps its working copy`() =
        vmTest {
            val handle = SavedStateHandle()
            val before = viewModel(empty(), saved = handle)
            val id = before.ready().draft.id
            before.setName("Example")
            before.openMedicine(null, "entry-1")
            before.setMedicineName("Amoxicillin")
            before.medicine { it.name == "Amoxicillin" }

            // A new ViewModel from what the old one saved, as after Android ended the process.
            val after = viewModel(empty(), saved = SavedStateHandle(mapOf("editor" to handle.get<String>("editor"))))
            val restored = after.ready()
            assertEquals(listOf(id.value, "Example"), listOf(restored.draft.id.value, restored.draft.name))
            assertTrue(restored.hasChanges)

            // After each open, the state catches up before it is read: a fresh working copy must not go unseen.
            after.openMedicine(null, "entry-1")
            advanceUntilIdle()
            assertEquals("Amoxicillin", after.medicine().name)
            after.openMedicine(null, "entry-2")
            advanceUntilIdle()
            assertEquals("", after.medicine().name)
        }

    @Test
    fun `saved data that can't be read starts again from the store`() =
        vmTest {
            val viewModel = viewModel(example(), StoreExample.treatmentId, saved = SavedStateHandle(mapOf("editor" to "{not json")))

            assertEquals("Example", viewModel.ready().draft.name)
        }

    @Test
    fun `an edited medicine's name and units show in the draft, and its unit follows the choice`() =
        vmTest {
            val viewModel = viewModel(empty())
            viewModel.ready()
            viewModel.openMedicine(null, "entry-1")
            viewModel.fill("Vitamin D", "", days = 4)

            viewModel.setIntervalUnit(IntervalUnit.WEEKS)
            viewModel.setInterval("1")
            viewModel.setDurationUnit(DurationUnit.WEEKS)

            val draft = viewModel.medicine { it.form.draft.durationUnit == DurationUnit.WEEKS }.form.draft
            assertEquals(listOf(1, 4), listOf(draft.intervalAmount, draft.durationAmount))
            assertEquals(IntervalUnit.WEEKS, draft.intervalUnit)
            assertEquals("Vitamin D", draft.name)
        }
}
