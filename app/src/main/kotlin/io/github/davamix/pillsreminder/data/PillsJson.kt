package io.github.davamix.pillsreminder.data

import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.EarlierSchedule
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
import io.github.davamix.pillsreminder.core.reminders.ReminderPlanner
import io.github.davamix.pillsreminder.core.reminders.ReminderState
import io.github.davamix.pillsreminder.core.reminders.TakeReminder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.LocalDateTime

// The JSON format of the store (P3 SPEC §4.3.1, §4.3.2). Privacy: kotlinx.serialization's exception messages quote the
// JSON input, names and doses included, so they never leave this file. Only fixed texts and exception class names do.

/** The result of reading stored bytes. */
internal sealed interface Decoded {
    data class Readable(
        val data: PillsData,
    ) : Decoded

    /** [reason] says which step failed and why, and never holds treatment content (SPEC §5). */
    data class Unreadable(
        val reason: String,
    ) : Decoded
}

// Strict on purpose (P3-D2): unknown keys, unquoted strings and coerced values are refused, as by default. Optional
// (nullable) fields are left out when absent.
private val json = Json { explicitNulls = false }

/** The data as version 1 of the format, in UTF-8, compact. */
internal fun encodePillsData(data: PillsData): ByteArray =
    json.encodeToString(PillsDocumentV1.serializer(), data.toDocument()).encodeToByteArray()

/** Reads stored bytes (SPEC §4.3.2). Never throws: content it can't read gives [Decoded.Unreadable]. */
internal fun decodePillsData(bytes: ByteArray): Decoded {
    val root =
        try {
            json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true))
        } catch (e: Exception) {
            return unreadable("parse", e)
        }
    if (root !is JsonObject) return Decoded.Unreadable("parse: not a JSON object")

    val version = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
    if (version == null) return Decoded.Unreadable("version: missing, or not a whole number")
    // Migrations from older versions go here, once there are any (SPEC §4.3.6). A newer version is never read.
    if (version != SCHEMA_VERSION) return Decoded.Unreadable("version: $version isn't supported")

    val document =
        try {
            json.decodeFromJsonElement(PillsDocumentV1.serializer(), root)
        } catch (e: Exception) {
            return unreadable("decode", e)
        }
    return try {
        Decoded.Readable(document.toPillsData())
    } catch (e: Exception) {
        unreadable("map", e)
    }
}

/** A failed step. Only [InvalidDocumentException]'s own fixed messages are kept; other messages may quote content. */
private fun unreadable(
    step: String,
    e: Exception,
): Decoded.Unreadable =
    Decoded.Unreadable(
        if (e is InvalidDocumentException) "$step: ${e.message}" else "$step: ${e::class.simpleName}",
    )

/** A check of §4.3.2 step 4 failed. Its message is a fixed text, with no content. */
private class InvalidDocumentException(
    message: String,
) : Exception(message)

private fun ensure(
    condition: Boolean,
    message: String,
) {
    if (!condition) throw InvalidDocumentException(message)
}

// Writing.

private fun PillsData.toDocument() =
    PillsDocumentV1(
        schemaVersion = SCHEMA_VERSION,
        treatments = treatments.map { it.toDocument() },
        answers =
            answers.entries
                .sortedWith(compareBy({ it.key.medicineId.value }, { it.key.plannedAt }))
                .map { (key, answer) -> answer.toDocument(key) },
        reminders =
            reminders.takes.entries
                .sortedWith(compareBy({ it.key.medicineId.value }, { it.key.plannedAt }))
                .map { (key, record) -> record.toDocument(key) },
    )

private fun Treatment.toDocument() =
    TreatmentV1(
        id = id.value,
        name = name,
        notes = notes,
        endedAt = endedAt?.let(::writeTime),
        medicines = medicines.map { it.toDocument() },
    )

private fun Medicine.toDocument() =
    MedicineV1(
        id = id.value,
        name = name,
        dose = dose,
        instructions = instructions,
        schedule = schedule.toDocument(),
        earlierSchedules = earlierSchedules.map { EarlierScheduleV1(it.schedule.toDocument(), writeTime(it.until)) },
    )

private fun Schedule.toDocument() =
    ScheduleV1(
        firstTake = writeTime(firstTake),
        interval = IntervalV1(interval.amount, interval.unit.toDocument()),
        duration =
            when (val duration = duration) {
                is MedicineDuration.Days -> DurationV1(DurationUnitV1.DAYS, duration.count)
                is MedicineDuration.Weeks -> DurationV1(DurationUnitV1.WEEKS, duration.count)
                MedicineDuration.NoEnd -> DurationV1(DurationUnitV1.NO_END)
            },
    )

private fun IntervalUnit.toDocument() =
    when (this) {
        IntervalUnit.MINUTES -> IntervalUnitV1.MINUTES
        IntervalUnit.HOURS -> IntervalUnitV1.HOURS
        IntervalUnit.DAYS -> IntervalUnitV1.DAYS
        IntervalUnit.WEEKS -> IntervalUnitV1.WEEKS
    }

private fun Answer.toDocument(key: TakeKey) =
    when (this) {
        is Answer.Taken -> AnswerV1(key.medicineId.value, writeTime(key.plannedAt), AnswerKindV1.TAKEN, writeTime(at))
        Answer.Skipped -> AnswerV1(key.medicineId.value, writeTime(key.plannedAt), AnswerKindV1.SKIPPED)
    }

private fun TakeReminder.toDocument(key: TakeKey) =
    when (this) {
        is TakeReminder.Snoozed -> {
            ReminderV1(key.medicineId.value, writeTime(key.plannedAt), snoozedUntil = writeInstant(until))
        }

        is TakeReminder.Alerted -> {
            ReminderV1(key.medicineId.value, writeTime(key.plannedAt), alerts = count, lastAlertAt = writeInstant(lastAt))
        }
    }

/** `2026-11-23T08:00` for a whole minute. A time with seconds is written with them, and then refused when read. */
private fun writeTime(time: LocalDateTime): String = time.toString()

/** `2026-11-23T15:10:00Z`: UTC, in whole seconds, as P4's records hold them (P4-D5). */
private fun writeInstant(instant: Instant): String = instant.toString()

// Reading. P2's constructors check amounts, counts, whole-minute first takes and the order of earlier schedules.

private fun PillsDocumentV1.toPillsData(): PillsData {
    val treatments = treatments.map { it.toTreatment() }
    ensure(treatments.map { it.id }.isUnique(), "two treatments have the same ID")
    val medicineIds = treatments.flatMap { treatment -> treatment.medicines.map { it.id } }
    ensure(medicineIds.isUnique(), "two medicines have the same ID")
    val answers = answers.map { it.toKey() to it.toAnswer() }
    ensure(answers.map { it.first }.isUnique(), "two answers for the same take")
    val records = reminders.map { it.toKey() to it.toRecord() }
    ensure(records.map { it.first }.isUnique(), "two reminder records for the same take")
    return PillsData(treatments, answers.toMap(), ReminderState(records.toMap()))
}

private fun <T> List<T>.isUnique(): Boolean = size == toSet().size

private fun TreatmentV1.toTreatment() =
    Treatment(
        id = TreatmentId(id),
        name = name,
        notes = notes,
        medicines = medicines.map { it.toMedicine() },
        endedAt = endedAt?.let(::readTime),
    )

private fun MedicineV1.toMedicine() =
    Medicine(
        id = MedicineId(id),
        name = name,
        dose = dose,
        instructions = instructions,
        schedule = schedule.toSchedule(),
        earlierSchedules = earlierSchedules.map { EarlierSchedule(it.schedule.toSchedule(), readTime(it.until)) },
    )

private fun ScheduleV1.toSchedule() =
    Schedule(
        firstTake = readTime(firstTake),
        interval = Interval(interval.amount, interval.unit.toUnit()),
        duration = duration.toDuration(),
    )

private fun IntervalUnitV1.toUnit() =
    when (this) {
        IntervalUnitV1.MINUTES -> IntervalUnit.MINUTES
        IntervalUnitV1.HOURS -> IntervalUnit.HOURS
        IntervalUnitV1.DAYS -> IntervalUnit.DAYS
        IntervalUnitV1.WEEKS -> IntervalUnit.WEEKS
    }

private fun DurationV1.toDuration(): MedicineDuration =
    when (unit) {
        DurationUnitV1.DAYS -> MedicineDuration.Days(ensureNotNull(count, "a duration in days has no count"))
        DurationUnitV1.WEEKS -> MedicineDuration.Weeks(ensureNotNull(count, "a duration in weeks has no count"))
        DurationUnitV1.NO_END -> {
            ensure(count == null, "a duration with no end has a count")
            MedicineDuration.NoEnd
        }
    }

private fun AnswerV1.toKey() = TakeKey(MedicineId(medicineId), readTime(plannedAt))

private fun AnswerV1.toAnswer(): Answer =
    when (answer) {
        AnswerKindV1.TAKEN -> Answer.Taken(readTime(ensureNotNull(takenAt, "a taken answer has no time")))
        AnswerKindV1.SKIPPED -> {
            ensure(takenAt == null, "a skipped answer has a time")
            Answer.Skipped
        }
    }

private fun ReminderV1.toKey() = TakeKey(MedicineId(medicineId), readTime(plannedAt))

/** Snoozed, with only `snoozedUntil`; or alerted, with only `alerts` and `lastAlertAt` (P5 SPEC §4.3.7). */
private fun ReminderV1.toRecord(): TakeReminder {
    val snoozed = snoozedUntil != null
    val alerted = alerts != null || lastAlertAt != null
    ensure(snoozed != alerted, "a reminder record isn't either snoozed or alerted")
    if (snoozedUntil != null) return TakeReminder.Snoozed(readInstant(snoozedUntil))
    val count = ensureNotNull(alerts, "an alerted reminder record has no count")
    ensure(count in 1..ReminderPlanner.ALERTS_PER_ROUND, "an alerted reminder record's count is out of range")
    return TakeReminder.Alerted(count, readInstant(ensureNotNull(lastAlertAt, "an alerted reminder record has no time")))
}

private fun <T : Any> ensureNotNull(
    value: T?,
    message: String,
): T = value ?: throw InvalidDocumentException(message)

/** Only the form [writeTime] writes: a valid local date-time, in whole minutes, with no seconds and no zone. */
private fun readTime(text: String): LocalDateTime {
    val time = LocalDateTime.parse(text)
    ensure(time.second == 0 && time.nano == 0 && time.toString() == text, "a time isn't written in whole minutes")
    return time
}

/** Only the form [writeInstant] writes: UTC with `Z`, in whole seconds, the seconds written even when 0. */
private fun readInstant(text: String): Instant {
    val instant = Instant.parse(text)
    ensure(instant.nano == 0 && instant.toString() == text, "an instant isn't written in UTC in whole seconds")
    return instant
}
