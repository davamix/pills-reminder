package io.github.davamix.pillsreminder.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Version 1 of the stored document (P3 SPEC §4.3.1). These types *are* the file format, kept apart from the domain
// types on purpose (P2-D2, P3-D4). Never change them in a way that version-1 files can't be decoded as: the golden file
// app/src/test/resources/data/pills-v1.json must always decode (SPEC §4.3.6).
//
// Fields are written in the order declared here. Only the nullable ones have a default, so every other field is
// written, and is required when reading.

/** The version these types read and write. */
internal const val SCHEMA_VERSION = 1

@Serializable
internal data class PillsDocumentV1(
    val schemaVersion: Int,
    val treatments: List<TreatmentV1>,
    /** Sorted by medicine ID, then planned time. */
    val answers: List<AnswerV1>,
)

@Serializable
internal data class TreatmentV1(
    val id: String,
    val name: String,
    val notes: String,
    /** Only when it was ended early (TR-11). */
    val endedAt: String? = null,
    val medicines: List<MedicineV1>,
)

@Serializable
internal data class MedicineV1(
    val id: String,
    val name: String,
    val dose: String,
    val instructions: String,
    val schedule: ScheduleV1,
    /** Oldest first (TR-10). */
    val earlierSchedules: List<EarlierScheduleV1>,
)

@Serializable
internal data class ScheduleV1(
    val firstTake: String,
    val interval: IntervalV1,
    val duration: DurationV1,
)

@Serializable
internal data class EarlierScheduleV1(
    val schedule: ScheduleV1,
    val until: String,
)

@Serializable
internal data class IntervalV1(
    val amount: Int,
    val unit: IntervalUnitV1,
)

@Serializable
internal enum class IntervalUnitV1 {
    /** Debug builds only create it (plan D-15), but every build reads it. */
    @SerialName("minutes")
    MINUTES,

    @SerialName("hours")
    HOURS,

    @SerialName("days")
    DAYS,

    @SerialName("weeks")
    WEEKS,
}

@Serializable
internal data class DurationV1(
    val unit: DurationUnitV1,
    /** For days and weeks; absent for no end. */
    val count: Int? = null,
)

@Serializable
internal enum class DurationUnitV1 {
    @SerialName("days")
    DAYS,

    @SerialName("weeks")
    WEEKS,

    @SerialName("noEnd")
    NO_END,
}

@Serializable
internal data class AnswerV1(
    val medicineId: String,
    val plannedAt: String,
    val answer: AnswerKindV1,
    /** For taken; absent for skipped. */
    val takenAt: String? = null,
)

@Serializable
internal enum class AnswerKindV1 {
    @SerialName("taken")
    TAKEN,

    @SerialName("skipped")
    SKIPPED,
}
