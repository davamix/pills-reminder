package io.github.davamix.pillsreminder.data

import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.fail

/** Content the store can't read (P3 SPEC §4.3.2): each case is Unreadable, at the right step, with no content in the reason. */
class DamagedDataTest {
    private class Case(
        val name: String,
        val step: String,
        val bytes: ByteArray,
    )

    private val example = encodePillsData(StoreExample.example).decodeToString()
    private val full = encodePillsData(StoreExample.full).decodeToString()

    private fun case(
        name: String,
        step: String,
        text: String,
    ) = Case(name, step, text.encodeToByteArray())

    /** The example, or the full data, with one change to its JSON tree. */
    private fun edited(
        name: String,
        step: String,
        vararg path: Any,
        from: String = example,
        change: (JsonElement?) -> JsonElement?,
    ) = case(name, step, Json.parseToJsonElement(from).edit(path.toList(), change).toString())

    private val cases =
        listOf(
            case("an empty file", "parse", ""),
            case("text that isn't JSON", "parse", "Amoxicillin, 1 capsule"),
            case("truncated JSON", "parse", full.take(full.length / 2)),
            Case("bytes that aren't UTF-8", "parse", byteArrayOf(0xC3.toByte(), 0x28) + example.encodeToByteArray()),
            case("a JSON array", "parse", "[$example]"),
            edited("no schemaVersion", "version", "schemaVersion") { null },
            edited("schemaVersion 0", "version", "schemaVersion") { JsonPrimitive(0) },
            edited("schemaVersion 2", "version", "schemaVersion") { JsonPrimitive(2) },
            edited("schemaVersion as text", "version", "schemaVersion") { JsonPrimitive("1") },
            edited("schemaVersion 1.5", "version", "schemaVersion") { JsonPrimitive(1.5) },
            edited("an unknown field", "decode", "treatments", 0, "colour") { JsonPrimitive("red") },
            edited("an unknown top-level field", "decode", "reminders") { JsonArray(emptyList()) },
            edited("a missing dose", "decode", "treatments", 0, "medicines", 0, "dose") { null },
            edited("missing earlier schedules", "decode", "treatments", 0, "medicines", 0, "earlierSchedules") { null },
            edited("missing answers", "decode", "answers") { null },
            edited("a name that is a number", "decode", "treatments", 0, "name") { JsonPrimitive(8) },
            edited("medicines that aren't a list", "decode", "treatments", 0, "medicines") { JsonObject(emptyMap()) },
            edited("an unknown interval unit", "decode", "treatments", 0, "medicines", 0, "schedule", "interval", "unit") {
                JsonPrimitive("months")
            },
            edited("an unknown duration unit", "decode", "treatments", 0, "medicines", 0, "schedule", "duration", "unit") {
                JsonPrimitive("forever")
            },
            edited("an unknown answer", "decode", "answers", 0, "answer") { JsonPrimitive("maybe") },
            edited("an interval of 0", "map", "treatments", 0, "medicines", 0, "schedule", "interval", "amount") {
                JsonPrimitive(0)
            },
            edited("a duration of 0 days", "map", "treatments", 0, "medicines", 0, "schedule", "duration", "count") {
                JsonPrimitive(0)
            },
            edited("days without a count", "map", "treatments", 0, "medicines", 0, "schedule", "duration", "count") { null },
            edited("no end with a count", "map", "treatments", 0, "medicines", 0, "schedule", "duration", "unit") {
                JsonPrimitive("noEnd")
            },
            edited("an invalid date", "map", "treatments", 0, "medicines", 0, "schedule", "firstTake") {
                JsonPrimitive("2026-11-31T08:00")
            },
            edited("a time that isn't a date-time", "map", "answers", 0, "plannedAt") { JsonPrimitive("Monday 0800") },
            edited("a time with seconds", "map", "answers", 0, "takenAt") { JsonPrimitive("2026-11-23T08:05:30") },
            edited("a time with zero seconds written", "map", "answers", 0, "takenAt") { JsonPrimitive("2026-11-23T08:05:00") },
            edited("a time with a zone", "map", "treatments", 0, "medicines", 0, "schedule", "firstTake") {
                JsonPrimitive("2026-11-23T08:00Z")
            },
            edited("taken without a time", "map", "answers", 0, "takenAt") { null },
            edited("skipped with a time", "map", "answers", 1, "takenAt") { JsonPrimitive("2026-11-23T08:10") },
            edited("two answers for one take", "map", "answers") { JsonArray(it!!.jsonArray + it.jsonArray[0]) },
            edited("earlier schedules out of order", "map", "treatments", 1, "medicines", 0, "earlierSchedules", from = full) {
                JsonArray(it!!.jsonArray.reversed())
            },
            case(
                "a treatment ID twice",
                "map",
                encodePillsData(
                    StoreExample.full.copy(treatments = StoreExample.full.treatments.map { it.copy(id = TreatmentId("same")) }),
                ).decodeToString(),
            ),
            case(
                "a medicine ID twice in one treatment",
                "map",
                encodePillsData(
                    PillsData(
                        listOf(
                            StoreExample.treatment.copy(
                                medicines = listOf(StoreExample.amoxicillin, StoreExample.atrovent.copy(id = StoreExample.amoxicillinId)),
                            ),
                        ),
                    ),
                ).decodeToString(),
            ),
            case(
                "a medicine ID in two treatments",
                "map",
                encodePillsData(
                    PillsData(
                        listOf(
                            StoreExample.treatment,
                            StoreExample.ended.copy(medicines = listOf(StoreExample.weekly.copy(id = StoreExample.atroventId))),
                        ),
                    ),
                ).decodeToString(),
            ),
        )

    @TestFactory
    fun `each damaged input is Unreadable at its step, with no content in the reason`(): List<DynamicTest> =
        cases.map { case ->
            dynamicTest(case.name) {
                when (val decoded = decodePillsData(case.bytes)) {
                    is Decoded.Readable -> fail("${case.name} was read")
                    is Decoded.Unreadable -> {
                        assertTrue(decoded.reason.startsWith("${case.step}: "), "${case.name}: ${decoded.reason}")
                        for (text in StoreExample.content) {
                            assertFalse(text in decoded.reason, "${case.name}: the reason quotes content")
                        }
                    }
                }
            }
        }

    @Test
    fun `the checks of step 4 give their own reasons`() {
        fun reason(case: String) = (decodePillsData(cases.single { it.name == case }.bytes) as Decoded.Unreadable).reason

        assertEquals("map: two treatments have the same ID", reason("a treatment ID twice"))
        assertEquals("map: two medicines have the same ID", reason("a medicine ID in two treatments"))
        assertEquals("map: two answers for the same take", reason("two answers for one take"))
        assertEquals("map: a time isn't written in whole minutes", reason("a time with seconds"))
        assertEquals("version: 2 isn't supported", reason("schemaVersion 2"))
    }

    @Test
    fun `an explicit null for an optional field is read as absent`() {
        val text = Json.parseToJsonElement(example).edit(listOf("treatments", 0, "endedAt")) { JsonNull }.toString()

        assertEquals(Decoded.Readable(StoreExample.example), decodePillsData(text.encodeToByteArray()))
    }

    @Test
    fun `answers that match no take are kept as they are`() {
        val withOrphans =
            StoreExample.example.copy(
                answers =
                    StoreExample.example.answers +
                        mapOf(
                            TakeKey(MedicineId("removed"), nov(23, 8)) to Answer.Skipped,
                            TakeKey(StoreExample.amoxicillinId, nov(23, 9)) to Answer.Taken(nov(23, 9)),
                        ),
            )

        assertEquals(Decoded.Readable(withOrphans), decodePillsData(encodePillsData(withOrphans)))
    }
}

/**
 * This element with the value at [path] changed: keys of objects and indexes of lists. [change] gets the old value, or
 * `null` if there is none, and returns the new one, or `null` to remove it.
 */
internal fun JsonElement.edit(
    path: List<Any>,
    change: (JsonElement?) -> JsonElement?,
): JsonElement {
    if (path.isEmpty()) return checkNotNull(change(this)) { "can't remove the root" }
    val key = path.first()
    val rest = path.drop(1)
    return when (this) {
        is JsonObject -> {
            key as String
            val old = this[key]
            val new = if (rest.isEmpty()) change(old) else checkNotNull(old) { "no $key" }.edit(rest, change)
            JsonObject(if (new == null) this - key else this + (key to new))
        }

        is JsonArray -> {
            key as Int
            val new = if (rest.isEmpty()) change(this[key]) else this[key].edit(rest, change)
            JsonArray(if (new == null) this.filterIndexed { i, _ -> i != key } else this.mapIndexed { i, e -> if (i == key) new else e })
        }

        else -> error("can't go into $this at $key")
    }
}
