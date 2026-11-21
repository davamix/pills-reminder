package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.data.nov
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A take key as text, for tags and intents (P5 SPEC §4.3.4). */
class TakeKeyCodecTest {
    @Test
    fun `a key is written as its planned time, then its medicine ID`() {
        assertEquals("2026-11-23T16:00|amoxicillin", TakeKeyCodec.encode(TakeKey(MedicineId("amoxicillin"), nov(23, 16))))
    }

    @Test
    fun `a key reads back the same, also with an ID that holds the separator or a slash`() {
        for (id in listOf("amoxicillin", "0b6e2c4e-7a1f-4f27-9c55-3c0f8b2d1a9e", "a|b", "take/x", "ñ")) {
            val key = TakeKey(MedicineId(id), nov(23, 16, 30))
            assertEquals(key, TakeKeyCodec.decode(TakeKeyCodec.encode(key)), id)
        }
    }

    @Test
    fun `text the app didn't write isn't a key`() {
        val refused =
            listOf(
                "",
                "amoxicillin",
                "2026-11-23T16:00|",
                "|amoxicillin",
                "Monday 16h00|amoxicillin",
                "2026-11-23T16:00:00|amoxicillin",
                "2026-11-23T16:00:30|amoxicillin",
                "2026-11-23T16:00Z|amoxicillin",
                "2026-11-31T16:00|amoxicillin",
            )
        for (text in refused) assertNull(TakeKeyCodec.decode(text), text)
    }
}
