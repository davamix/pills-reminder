package io.github.davamix.pillsreminder.ui.editor

import io.github.davamix.pillsreminder.core.model.MedicineDraft
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TreatmentDraft
import io.github.davamix.pillsreminder.core.model.ValidationError
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import java.time.LocalDate
import java.time.LocalDateTime

/** The medicine editor's fields as typed: the amounts stay text, and the draft holds what they parse to (P2-D10). */
data class MedicineForm(
    val draft: MedicineDraft,
    val intervalText: String,
    val durationText: String,
)

/** The number of takes and the last one before saving (SC-2); both `null` for no end (SC-5). */
data class Preview(
    val count: Long?,
    val last: LocalDateTime?,
)

/** The medicine editor's working copy while it is open (P6 SPEC §4.3.7). */
data class MedicineEditing(
    val form: MedicineForm,
    val isNew: Boolean,
    /** Whether the errors show: after the first Done (P6 SPEC §4.3.8). */
    val showErrors: Boolean,
    val errors: List<ValidationError>,
    /** `null` while the interval or the duration isn't valid. */
    val preview: Preview?,
    /** A stored medicine whose first take has fallen due, or that has an answer (TR-10). */
    val started: Boolean,
    val firstTakeInPast: Boolean,
    /** Whether the working copy differs from the medicine it opened with (TR-13). */
    val hasChanges: Boolean,
    /** Whether the interval may be in minutes: debug builds only (plan D-15). */
    val allowMinutes: Boolean,
)

/** Both editors' state (P6-D1). `null` until the stored treatment is read. */
data class EditorState(
    val draft: TreatmentDraft,
    val isNew: Boolean,
    /** Whether the treatment's errors show: after the first Save. */
    val showErrors: Boolean,
    /** The treatment's own errors (TR-8): its name, and at least one medicine. */
    val errors: List<ValidationError>,
    /** Whether the draft differs from the stored treatment, or from an empty one (TR-13). */
    val hasChanges: Boolean,
    /** The draft's medicines that have started, whose removal asks first (P6 Q6). */
    val started: Set<MedicineId>,
    /** The medicine editor's working copy, while it is open. */
    val medicine: MedicineEditing?,
    val unreadable: Boolean,
    val saving: Boolean,
    val problem: ChangeProblem?,
    val today: LocalDate,
)
