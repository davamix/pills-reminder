package io.github.davamix.pillsreminder.data

import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.reminders.ReminderState
import io.github.davamix.pillsreminder.core.reminders.TakeReminder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.Instant

/** The reminder records in format version 1 (P5 SPEC §4.3.7, P5-D1). Damaged records are in [DamagedDataTest]. */
class ReminderFormatTest {
    private fun readable(bytes: ByteArray): PillsData =
        when (val decoded = decodePillsData(bytes)) {
            is Decoded.Readable -> decoded.data
            is Decoded.Unreadable -> fail("Unreadable: ${decoded.reason}")
        }

    private fun tree(text: String) = Json.parseToJsonElement(text)

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/data/$name")) { "$name is missing" }.use { it.readBytes().decodeToString() }

    @Test
    fun `records of both kinds read back unchanged`() {
        assertEquals(StoreExample.reminded, readable(encodePillsData(StoreExample.reminded)))
    }

    @Test
    fun `the golden file with reminders decodes to its data`() {
        assertEquals(StoreExample.reminded, readable(resource("pills-v1-reminders.json").encodeToByteArray()))
    }

    @Test
    fun `the data with reminders is written as the golden file holds it`() {
        assertEquals(tree(resource("pills-v1-reminders.json")), tree(encodePillsData(StoreExample.reminded).decodeToString()))
    }

    @Test
    fun `the records are written as SPEC 4-3-7 shows them, after the answers`() {
        val expected =
            """
            [
              { "medicineId": "amoxicillin", "plannedAt": "2026-11-23T16:00", "alerts": 2, "lastAlertAt": "2026-11-23T15:10:00Z" },
              { "medicineId": "atrovent", "plannedAt": "2026-11-23T16:00", "snoozedUntil": "2026-11-23T15:15:00Z" }
            ]
            """.trimIndent()
        val written = tree(encodePillsData(StoreExample.reminded).decodeToString()).jsonObject

        assertEquals(tree(expected), written["reminders"])
        assertEquals(listOf("schemaVersion", "treatments", "answers", "reminders"), written.keys.toList())
    }

    @Test
    fun `data without records has no reminders field, and version-1 files without one read as no records`() {
        val written = tree(encodePillsData(StoreExample.example).decodeToString()).jsonObject

        assertFalse("reminders" in written)
        assertEquals(ReminderState(), readable(resource("pills-v1.json").encodeToByteArray()).reminders)
    }

    @Test
    fun `an explicit empty list reads as no records`() {
        val text =
            JsonObject(
                tree(encodePillsData(StoreExample.example).decodeToString()).jsonObject + ("reminders" to JsonArray(emptyList())),
            )

        assertEquals(StoreExample.example, readable(text.toString().encodeToByteArray()))
    }

    @Test
    fun `the records are sorted by medicine, then planned time, whatever their order`() {
        val earlier = TakeKey(StoreExample.atroventId, nov(23, 8)) to TakeReminder.Snoozed(Instant.parse("2026-11-23T07:15:00Z"))
        val records = StoreExample.reminded.reminders.takes + earlier
        val forwards = StoreExample.reminded.copy(reminders = ReminderState(records))
        val backwards = StoreExample.reminded.copy(reminders = ReminderState(records.entries.reversed().associate { it.toPair() }))

        assertArrayEquals(encodePillsData(forwards), encodePillsData(backwards))
        val order =
            tree(encodePillsData(forwards).decodeToString()).jsonObject.getValue("reminders").let { it as JsonArray }.map {
                it.jsonObject.getValue("medicineId").toString() + " " + it.jsonObject.getValue("plannedAt").toString()
            }
        assertEquals(
            listOf("\"amoxicillin\" \"2026-11-23T16:00\"", "\"atrovent\" \"2026-11-23T08:00\"", "\"atrovent\" \"2026-11-23T16:00\""),
            order,
        )
    }
}
