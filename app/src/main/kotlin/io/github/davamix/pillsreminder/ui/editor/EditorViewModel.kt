package io.github.davamix.pillsreminder.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davamix.pillsreminder.core.actions.previewMedicine
import io.github.davamix.pillsreminder.core.actions.saveTreatment
import io.github.davamix.pillsreminder.core.model.DurationUnit
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineDraft
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TreatmentDraft
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.ValidationError
import io.github.davamix.pillsreminder.core.model.toDraft
import io.github.davamix.pillsreminder.core.model.treatment
import io.github.davamix.pillsreminder.core.model.validate
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.core.time.dueAt
import io.github.davamix.pillsreminder.core.time.suggestedFirstTake
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import io.github.davamix.pillsreminder.ui.common.attempt
import io.github.davamix.pillsreminder.ui.treatments.STOP_AFTER_MILLIS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** At most this many digits in an amount: more can't fit in an Int anyway (P2-D10). */
private const val MAX_DIGITS = 10

/** The suggested interval and duration of a new medicine (P6 SPEC §4.3.7): every 8 hours, for 7 days. */
private const val DEFAULT_INTERVAL = 8
private const val DEFAULT_DURATION = 7

private const val SAVED_KEY = "editor"

/**
 * The two editors' ViewModel (P6-D1), scoped to their navigation graph. It holds the treatment's draft, and the
 * medicine editor's working copy while that is open; both survive Android ending the app's process, through
 * [saved] (P6-D6). Only [save] stores, with one `saveTreatment` that merges the draft with the latest data (P2-D4).
 *
 * [treatmentId] is `null` for a new treatment.
 */
class EditorViewModel(
    private val deps: ScreenDependencies,
    private val treatmentId: TreatmentId?,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val editor = MutableStateFlow(decodeEditorData(saved.get<String>(SAVED_KEY)))
    private val problem = MutableStateFlow<ChangeProblem?>(null)
    private val saving = MutableStateFlow(false)
    private val savedId = MutableStateFlow<TreatmentId?>(null)

    /** The saved treatment's ID once saved: the editor leaves (P6 SPEC §4.3.1). */
    val savedAs: StateFlow<TreatmentId?> = savedId.asStateFlow()

    val state: StateFlow<EditorState?> =
        combine(editor, deps.repository.data, deps.minuteTicks, problem, saving) { editor, stored, _, problem, saving ->
            editor?.let { build(it, stored, ZonedDateTime.now(deps.clock), problem, saving, deps.allowMinutes) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    init {
        if (editor.value == null) viewModelScope.launch { start() }
    }

    /** A new treatment's empty draft, or the stored treatment's, read once (P6 SPEC §4.3.6). */
    private suspend fun start() {
        val draft =
            if (treatmentId == null) {
                TreatmentDraft(TreatmentId.random(), "", "", emptyList())
            } else {
                val data = (deps.repository.data.first() as? StoredData.Readable)?.data
                data?.treatment(treatmentId)?.toDraft() ?: TreatmentDraft(treatmentId, "", "", emptyList())
            }
        set(EditorData(original = draft, draft = draft, isNew = treatmentId == null, showErrors = false, working = null))
    }

    fun setName(text: String) = change { it.copy(draft = it.draft.copy(name = text)) }

    fun setNotes(text: String) = change { it.copy(draft = it.draft.copy(notes = text)) }

    fun removeMedicine(id: MedicineId) =
        change { it.copy(draft = it.draft.copy(medicines = it.draft.medicines.filter { m -> m.id != id })) }

    /**
     * Opens the medicine editor on the draft's medicine [id], or on a new one when `null`. [entry] is the medicine
     * editor's back-stack entry: the same entry keeps its working copy, as after Android ended the process; another
     * starts a fresh one.
     */
    fun openMedicine(
        id: MedicineId?,
        entry: String,
    ) = change { data ->
        if (data.working?.entry == entry) return@change data
        val medicine = id?.let { data.draft.medicines.firstOrNull { m -> m.id == it } } ?: newMedicine()
        val form = MedicineForm(medicine, medicine.intervalAmount?.toString() ?: "", medicine.durationAmount?.toString() ?: "")
        data.copy(working = WorkingMedicine(form, form, isNew = id == null, showErrors = false, entry = entry))
    }

    fun setMedicineName(text: String) = medicine { it.copy(draft = it.draft.copy(name = text)) }

    fun setDose(text: String) = medicine { it.copy(draft = it.draft.copy(dose = text)) }

    fun setInstructions(text: String) = medicine { it.copy(draft = it.draft.copy(instructions = text)) }

    fun setInterval(text: String) =
        medicine {
            val digits = text.digits()
            it.copy(draft = it.draft.copy(intervalAmount = digits.toIntOrNull()), intervalText = digits)
        }

    fun setIntervalUnit(unit: IntervalUnit) = medicine { it.copy(draft = it.draft.copy(intervalUnit = unit)) }

    fun setDuration(text: String) =
        medicine {
            val digits = text.digits()
            it.copy(draft = it.draft.copy(durationAmount = digits.toIntOrNull()), durationText = digits)
        }

    fun setDurationUnit(unit: DurationUnit) = medicine { it.copy(draft = it.draft.copy(durationUnit = unit)) }

    fun setNoEnd(noEnd: Boolean) = medicine { it.copy(draft = it.draft.copy(noEnd = noEnd)) }

    fun setFirstTakeDate(date: LocalDate) = medicine { it.withFirstTake(date = date) }

    fun setFirstTakeTime(time: LocalTime) = medicine { it.withFirstTake(time = time) }

    /**
     * Done (P6 Q1): puts the working copy into the treatment's draft, replacing the medicine with its ID or adding it at
     * the end, and closes the medicine editor. `false`, showing the errors, while it isn't valid (TR-8).
     */
    fun medicineDone(): Boolean {
        val data = editor.value ?: return false
        val working = data.working ?: return false
        val medicine = working.form.draft
        if (medicine.validate(deps.allowMinutes).isNotEmpty()) {
            set(data.copy(working = working.copy(showErrors = true)))
            return false
        }
        val medicines = data.draft.medicines
        val updated = if (medicines.any { it.id == medicine.id }) medicines.replacing(medicine) else medicines + medicine
        set(data.copy(draft = data.draft.copy(medicines = updated), working = null))
        return true
    }

    /** Closes the medicine editor without keeping its changes (TR-13). */
    fun closeMedicine() = change { it.copy(working = null) }

    /**
     * Saves the treatment (P6 SPEC §4.3.6): with errors, shows them (TR-8); otherwise stores the draft through
     * `saveTreatment`, which merges it with the latest data inside `update` (TR-10, P2-D4). Once saved, [savedAs] is set.
     */
    fun save() {
        val data = editor.value ?: return
        if (saving.value) return
        if (data.draft.validate(deps.allowMinutes).isNotEmpty()) {
            set(data.copy(showErrors = true))
            return
        }
        saving.value = true
        viewModelScope.launch {
            val result =
                attempt {
                    deps.repository.saveTreatment(data.draft, deps.clock)
                    true
                }
            saving.value = false
            if (result == null) savedId.value = data.draft.id else problem.value = result
        }
    }

    fun problemShown() {
        problem.value = null
    }

    private fun newMedicine() =
        MedicineDraft(
            id = MedicineId.random(),
            name = "",
            dose = "",
            instructions = "",
            firstTake = suggestedFirstTake(ZonedDateTime.now(deps.clock)),
            intervalAmount = DEFAULT_INTERVAL,
            intervalUnit = IntervalUnit.HOURS,
            durationAmount = DEFAULT_DURATION,
            durationUnit = DurationUnit.DAYS,
            noEnd = false,
        )

    private fun change(transform: (EditorData) -> EditorData) {
        editor.value?.let { set(transform(it)) }
    }

    private fun medicine(transform: (MedicineForm) -> MedicineForm) =
        change { data -> data.working?.let { data.copy(working = it.copy(form = transform(it.form))) } ?: data }

    private fun set(data: EditorData) {
        editor.value = data
        saved[SAVED_KEY] = data.encode()
    }

    companion object {
        fun factory(
            deps: ScreenDependencies,
            treatmentId: TreatmentId?,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { EditorViewModel(deps, treatmentId, createSavedStateHandle()) } }

        private fun String.digits() = filter(Char::isDigit).take(MAX_DIGITS)

        /** The form with its first take on [date] at [time], each the current one unless given. */
        private fun MedicineForm.withFirstTake(
            date: LocalDate = draft.firstTake.toLocalDate(),
            time: LocalTime = draft.firstTake.toLocalTime(),
        ) = copy(draft = draft.copy(firstTake = date.atTime(time)))

        /** The list with the medicine of the same ID replaced by [medicine]. */
        private fun List<MedicineDraft>.replacing(medicine: MedicineDraft) = map { if (it.id == medicine.id) medicine else it }

        internal fun build(
            data: EditorData,
            stored: StoredData,
            now: ZonedDateTime,
            problem: ChangeProblem?,
            saving: Boolean,
            allowMinutes: Boolean,
        ): EditorState {
            val pills = (stored as? StoredData.Readable)?.data
            val known = pills ?: PillsData()
            val treatmentErrors =
                data.draft.validate(allowMinutes).filter {
                    it == ValidationError.TreatmentNameMissing || it == ValidationError.NoMedicine
                }
            return EditorState(
                draft = data.draft,
                isNew = data.isNew,
                showErrors = data.showErrors,
                errors = treatmentErrors,
                hasChanges = data.draft != data.original,
                started =
                    data.draft.medicines
                        .map { it.id }
                        .filter { known.started(data.draft.id, it, now) }
                        .toSet(),
                medicine = data.working?.let { medicineEditing(it, data.draft.id, known, now, allowMinutes) },
                unreadable = pills == null,
                saving = saving,
                problem = problem,
                today = now.toLocalDate(),
            )
        }

        private fun medicineEditing(
            working: WorkingMedicine,
            treatmentId: TreatmentId,
            data: PillsData,
            now: ZonedDateTime,
            allowMinutes: Boolean,
        ): MedicineEditing {
            val draft = working.form.draft
            val errors = draft.validate(allowMinutes)
            val scheduleValid = errors.none { it !is ValidationError.MedicineNameMissing }
            val started = data.started(treatmentId, draft.id, now)
            return MedicineEditing(
                form = working.form,
                isNew = working.isNew,
                showErrors = working.showErrors,
                errors = errors,
                preview =
                    if (scheduleValid) {
                        data.previewMedicine(treatmentId, draft, now).let { Preview(it.count, it.last) }
                    } else {
                        null
                    },
                started = started,
                firstTakeInPast = !started && draft.firstTake.dueAt(now.zone) < now.toInstant(),
                hasChanges = working.form != working.original,
                allowMinutes = allowMinutes,
            )
        }

        /**
         * Whether the treatment's stored medicine [id] has started: its first take has fallen due, or it has an answer
         * (P6 SPEC §4.3.6). A medicine not yet stored in that treatment hasn't.
         */
        private fun PillsData.started(
            treatmentId: TreatmentId,
            id: MedicineId,
            now: ZonedDateTime,
        ): Boolean {
            if (treatment(treatmentId)?.medicines?.any { it.id == id } != true) return false
            val takes = takesOf(id) ?: return false
            val first = takes.first
            return takes.answers.isNotEmpty() || (first != null && first.dueAt(now.zone) <= now.toInstant())
        }
    }
}
