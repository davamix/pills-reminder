package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.model.TakeKey

/**
 * The notification shade, as the reconcile sees it (P5 SPEC §4.3.4): only the app's own reminders, never other
 * notifications. The Android one is `AndroidReminderShade` (T5); tests use a fake.
 */
interface ReminderShade {
    /** Makes the shade ready: the notification channel. Called once, when the app starts. */
    fun prepare() {}

    /** The reminders in the shade now, each with the [ReminderContent.fingerprint] it was posted with. */
    fun shown(): Map<TakeKey, String>

    /**
     * Posts the reminder, or replaces the one with the same key: with sound and a pop-up when [alert], silently
     * otherwise. The reconcile decides when a silent post is needed (P5-D5).
     */
    fun post(
        content: ReminderContent,
        alert: Boolean,
    )

    fun remove(key: TakeKey)

    /** The group's summary: shown while [count] > 0, posted again only when the count changes, removed at 0. */
    fun summary(count: Int)
}
