package io.github.davamix.pillsreminder.ui.editor

import io.github.davamix.pillsreminder.core.model.DurationUnit
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineDraft
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TreatmentDraft
import io.github.davamix.pillsreminder.core.model.TreatmentId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

// The editors' drafts as the SavedStateHandle keeps them (P6-D6), so they survive Android ending the app's process in
// the background. Kept apart from the store's format (P3): nothing here is ever written to the store. Times are text,
// in the form LocalDateTime.toString() writes.

/** Everything both editors hold, between two changes. */
internal data class EditorData(
    /** The draft as it started: the stored treatment, or an empty one (TR-13). */
    val original: TreatmentDraft,
    val draft: TreatmentDraft,
    val isNew: Boolean,
    val showErrors: Boolean,
    /** The medicine editor's working copy, while it is open. */
    val working: WorkingMedicine?,
)

/** The medicine editor's working copy, and the back-stack entry of the editor it belongs to. */
internal data class WorkingMedicine(
    val original: MedicineForm,
    val form: MedicineForm,
    val isNew: Boolean,
    val showErrors: Boolean,
    /** The medicine editor's back-stack entry: a new entry is a new working copy; the same one, restored, keeps it. */
    val entry: String,
)

@Serializable
private data class SavedMedicine(
    val id: String,
    val name: String,
    val dose: String,
    val instructions: String,
    val firstTake: String,
    val intervalAmount: Int?,
    val intervalUnit: IntervalUnit,
    val durationAmount: Int?,
    val durationUnit: DurationUnit,
    val noEnd: Boolean,
)

@Serializable
private data class SavedTreatment(
    val id: String,
    val name: String,
    val notes: String,
    val medicines: List<SavedMedicine>,
)

@Serializable
private data class SavedForm(
    val medicine: SavedMedicine,
    val intervalText: String,
    val durationText: String,
)

@Serializable
private data class SavedWorking(
    val original: SavedForm,
    val form: SavedForm,
    val isNew: Boolean,
    val showErrors: Boolean,
    val entry: String,
)

@Serializable
private data class SavedEditorData(
    val original: SavedTreatment,
    val draft: SavedTreatment,
    val isNew: Boolean,
    val showErrors: Boolean,
    val working: SavedWorking?,
)

private val json = Json

internal fun EditorData.encode(): String = json.encodeToString(SavedEditorData.serializer(), toSaved())

/** The saved data, or `null` if there is none or it can't be read: the editor then starts again from the store. */
internal fun decodeEditorData(text: String?): EditorData? =
    text?.let {
        try {
            json.decodeFromString(SavedEditorData.serializer(), it).toData()
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization's SerializationException is one.
            null
        } catch (e: DateTimeParseException) {
            null
        }
    }

private fun EditorData.toSaved() = SavedEditorData(original.toSaved(), draft.toSaved(), isNew, showErrors, working?.toSaved())

private fun SavedEditorData.toData() = EditorData(original.toDraft(), draft.toDraft(), isNew, showErrors, working?.toWorking())

private fun TreatmentDraft.toSaved() = SavedTreatment(id.value, name, notes, medicines.map { it.toSaved() })

private fun SavedTreatment.toDraft() = TreatmentDraft(TreatmentId(id), name, notes, medicines.map { it.toDraft() })

private fun MedicineDraft.toSaved() =
    SavedMedicine(
        id.value,
        name,
        dose,
        instructions,
        firstTake.toString(),
        intervalAmount,
        intervalUnit,
        durationAmount,
        durationUnit,
        noEnd,
    )

private fun SavedMedicine.toDraft() =
    MedicineDraft(
        MedicineId(id),
        name,
        dose,
        instructions,
        LocalDateTime.parse(firstTake),
        intervalAmount,
        intervalUnit,
        durationAmount,
        durationUnit,
        noEnd,
    )

private fun MedicineForm.toSaved() = SavedForm(draft.toSaved(), intervalText, durationText)

private fun SavedForm.toForm() = MedicineForm(medicine.toDraft(), intervalText, durationText)

private fun WorkingMedicine.toSaved() = SavedWorking(original.toSaved(), form.toSaved(), isNew, showErrors, entry)

private fun SavedWorking.toWorking() = WorkingMedicine(original.toForm(), form.toForm(), isNew, showErrors, entry)
