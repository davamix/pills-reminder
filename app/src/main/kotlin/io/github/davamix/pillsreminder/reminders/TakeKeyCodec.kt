package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TakeKey
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * A take key as text, for notification tags, intents and the debug log: the planned time, then the medicine ID, such
 * as `2026-11-23T16:00|amoxicillin`. The time comes first and never holds `|`, so the ID may hold anything.
 */
object TakeKeyCodec {
    private const val SEPARATOR = '|'

    fun encode(key: TakeKey): String = "${key.plannedAt}$SEPARATOR${key.medicineId.value}"

    /** The key, or `null` for text that isn't a key the app wrote: no separator, no ID, a time not in whole minutes. */
    fun decode(text: String): TakeKey? {
        val separator = text.indexOf(SEPARATOR)
        if (separator < 0) return null
        val timeText = text.substring(0, separator)
        val id = text.substring(separator + 1)
        if (id.isEmpty()) return null
        val time =
            try {
                LocalDateTime.parse(timeText)
            } catch (_: DateTimeParseException) {
                return null
            }
        if (time.second != 0 || time.nano != 0 || time.toString() != timeText) return null
        return TakeKey(MedicineId(id), time)
    }
}
