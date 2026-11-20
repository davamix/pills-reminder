package io.github.davamix.pillsreminder.data

import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.schedule.takesOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.LocalDateTime

/** The JSON format, version 1 (P3 SPEC §4.3.1, §4.3.6). Damaged input is in [DamagedDataTest]. */
class PillsJsonTest {
    private fun readable(bytes: ByteArray): PillsData =
        when (val decoded = decodePillsData(bytes)) {
            is Decoded.Readable -> decoded.data
            is Decoded.Unreadable -> fail("Unreadable: ${decoded.reason}")
        }

    private fun readBack(data: PillsData): PillsData = readable(encodePillsData(data))

    private fun tree(text: String) = Json.parseToJsonElement(text)

    private fun golden(): String =
        checkNotNull(javaClass.getResourceAsStream("/data/pills-v1.json")) { "pills-v1.json is missing" }
            .use { it.readBytes().decodeToString() }

    @Test
    fun `the full data reads back unchanged`() {
        assertEquals(StoreExample.full, readBack(StoreExample.full))
    }

    @Test
    fun `empty data reads back unchanged, and is written with every field`() {
        assertEquals(PillsData(), readBack(PillsData()))
        assertEquals("""{"schemaVersion":1,"treatments":[],"answers":[]}""", encodePillsData(PillsData()).decodeToString())
    }

    @Test
    fun `the example is written as SPEC 4-3-1 shows it`() {
        val expected =
            """
            {
              "schemaVersion": 1,
              "treatments": [
                {
                  "id": "example",
                  "name": "Example",
                  "notes": "",
                  "medicines": [
                    {
                      "id": "amoxicillin",
                      "name": "Amoxicillin",
                      "dose": "1 capsule",
                      "instructions": "",
                      "schedule": {
                        "firstTake": "2026-11-23T08:00",
                        "interval": { "amount": 8, "unit": "hours" },
                        "duration": { "unit": "days", "count": 7 }
                      },
                      "earlierSchedules": []
                    },
                    {
                      "id": "atrovent",
                      "name": "Atrovent",
                      "dose": "3 puffs",
                      "instructions": "",
                      "schedule": {
                        "firstTake": "2026-11-23T08:00",
                        "interval": { "amount": 8, "unit": "hours" },
                        "duration": { "unit": "days", "count": 5 }
                      },
                      "earlierSchedules": []
                    }
                  ]
                }
              ],
              "answers": [
                { "medicineId": "amoxicillin", "plannedAt": "2026-11-23T08:00", "answer": "taken", "takenAt": "2026-11-23T08:05" },
                { "medicineId": "atrovent", "plannedAt": "2026-11-23T08:00", "answer": "skipped" }
              ]
            }
            """.trimIndent()

        assertEquals(tree(expected), tree(encodePillsData(StoreExample.example).decodeToString()))
    }

    @Test
    fun `the golden file of version 1 decodes to the full data`() {
        assertEquals(StoreExample.full, readable(golden().encodeToByteArray()))
    }

    @Test
    fun `the full data is written as the golden file holds it`() {
        assertEquals(tree(golden()), tree(encodePillsData(StoreExample.full).decodeToString()))
    }

    @Test
    fun `the same data gives the same bytes, whatever the order of its answers`() {
        val reordered =
            StoreExample.full.copy(
                answers =
                    StoreExample.full.answers.entries
                        .reversed()
                        .associate { it.toPair() },
            )

        assertArrayEquals(encodePillsData(StoreExample.full), encodePillsData(reordered))
    }

    @Test
    fun `the schema version is written first, and the file is compact UTF-8`() {
        val bytes = encodePillsData(StoreExample.full)
        val text = bytes.decodeToString()

        assertTrue(text.startsWith("""{"schemaVersion":1,"""), text.take(40))
        assertTrue('\n' !in text && "  " !in text, "compact: no new lines and no indentation")
        assertTrue("½ tablet" in text && bytes.size > text.length, "½ is written as itself, in UTF-8")
    }

    @Test
    fun `times are written in whole minutes, without seconds or a zone`() {
        val text = encodePillsData(StoreExample.full).decodeToString()
        val times = Regex(""""\d{4}-\d\d-\d\dT[^"]*"""").findAll(text).map { it.value }.toList()

        // 9 in the treatments (first takes, untils, an end) and 10 in the answers (planned and taken times).
        assertEquals(19, times.size, "the times found")
        for (time in times) assertTrue(Regex(""""\d{4}-\d\d-\d\dT\d\d:\d\d"""").matches(time), time)
    }

    @Test
    fun `the takes and their states are the same after reading back`() {
        val original = StoreExample.full
        val decoded = readBack(original)
        val ids = original.treatments.flatMap { treatment -> treatment.medicines.map { it.id } }
        val moments = listOf(nov(16, 8), nov(19, 10), nov(23, 10), nov(24, 8), nov(25, 13), nov(30, 12)).map(::nowAt)

        for (now in moments) {
            for (id in ids) {
                val before = checkNotNull(original.takesOf(id))
                val after = checkNotNull(decoded.takesOf(id))
                val until = nov(30, 12).plusWeeks(4)
                assertEquals(before.takes(LocalDateTime.MIN, until, now).toList(), after.takes(LocalDateTime.MIN, until, now).toList())
                assertEquals(before.count, after.count)
                assertEquals(before.last, after.last)
                assertEquals(before.nextTake(now), after.nextTake(now))
                assertEquals(before.lastAnswered(now), after.lastAnswered(now))
            }
        }
    }
}
