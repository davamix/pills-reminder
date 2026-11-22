package io.github.davamix.pillsreminder.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes, one per screen of spec §6. The phase that replaces a screen's placeholder owns
// its route, and can change its arguments.

/** The home screen, with the next takes: the start destination (P7). */
@Serializable
data object HomeRoute

/** Every treatment (P6). */
@Serializable
data object TreatmentsRoute

/** One treatment (P6). */
@Serializable
data class TreatmentRoute(
    val treatmentId: String,
)

/**
 * The editors, a nested graph whose two screens share one draft (P6-D1); a `null` [treatmentId] means a new treatment
 * (P6). It opens on [TreatmentFormRoute].
 */
@Serializable
data class TreatmentEditorRoute(
    val treatmentId: String? = null,
)

/** The treatment editor's own screen, the first of the editors' graph (P6). */
@Serializable
data object TreatmentFormRoute

/** The medicine editor, in the editors' graph; a `null` [medicineId] means a new medicine (P6). */
@Serializable
data class MedicineEditorRoute(
    val medicineId: String? = null,
)

/** One take: a medicine and its planned local date-time, in ISO-8601 form such as `2026-11-23T16:00` (P6, P5). */
@Serializable
data class TakeRoute(
    val medicineId: String,
    val plannedAt: String,
)

/** Settings, with the reminder check (P7). */
@Serializable
data object SettingsRoute
