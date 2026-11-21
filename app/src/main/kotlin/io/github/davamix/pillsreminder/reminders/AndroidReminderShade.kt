package io.github.davamix.pillsreminder.reminders

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.TakeKey

/**
 * The notification shade on Android (P5 SPEC §4.3.4). Each Due take has one notification, tagged
 * `<tagPrefix>/<take key>`, in one group with a summary tagged `<tagPrefix>-summary`. Instrumented tests use a prefix of
 * their own, so the app's reconcile never touches their notifications (P5-D11).
 */
class AndroidReminderShade(
    context: Context,
    private val tagPrefix: String = TAG_PREFIX,
) : ReminderShade {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    private val group = "io.github.davamix.pillsreminder.group.$tagPrefix"
    private val summaryTag = "$tagPrefix-summary"

    override fun prepare() = ReminderChannel.create(context)

    override fun shown(): Map<TakeKey, String> =
        manager.activeNotifications
            .mapNotNull { status ->
                val text = status.tag?.removePrefix("$tagPrefix/")?.takeIf { it != status.tag } ?: return@mapNotNull null
                val key = TakeKeyCodec.decode(text) ?: return@mapNotNull null
                key to status.notification.extras.getString(EXTRA_FINGERPRINT, "")
            }.toMap()

    override fun post(
        content: ReminderContent,
        alert: Boolean,
    ) {
        manager.notify(tag(content.key), ID, build(content, alert))
    }

    override fun remove(key: TakeKey) = manager.cancel(tag(key), ID)

    override fun summary(count: Int) {
        if (count <= 0) {
            manager.cancel(summaryTag, ID)
            return
        }
        val current = manager.activeNotifications.firstOrNull { it.tag == summaryTag }
        if (current?.notification?.extras?.getInt(EXTRA_COUNT) == count) return
        val summary =
            Notification
                .Builder(context, ReminderChannel.ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.resources.getQuantityString(R.plurals.reminders_summary, count, count))
                .setGroup(group)
                .setGroupSummary(true)
                // A summary with GROUP_ALERT_CHILDREN never alerts: only the reminders do.
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(ReminderIntents.openApp(context))
                .addExtras(Bundle().apply { putInt(EXTRA_COUNT, count) })
                .build()
        manager.notify(summaryTag, ID, summary)
    }

    /** The reminder's notification (RM-1, RM-3, RM-11). Internal for the instrumented tests, which check it unposted. */
    internal fun build(
        content: ReminderContent,
        alert: Boolean,
    ): Notification =
        Notification
            .Builder(context, ReminderChannel.ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(content))
            .setContentText(text(content))
            .setWhen(content.dueAt.toEpochMilli())
            .setShowWhen(true)
            // No category: HyperOS's Do Not Disturb lets the reminder category sound (Q6, RM-8).
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setContentIntent(ReminderIntents.openTake(context, content.key))
            .addAction(action(content.key, ReminderAction.TAKEN, R.string.reminder_action_taken))
            .addAction(action(content.key, ReminderAction.SNOOZE, R.string.reminder_action_snooze))
            .addAction(action(content.key, ReminderAction.SKIP, R.string.reminder_action_skip))
            .setGroup(group)
            // The platform has no setSilent(): a group child that only its summary may alert for is muted, with no sound,
            // no vibration and no pop-up, as NotificationCompat does it (Q8).
            .setGroupAlertBehavior(if (alert) Notification.GROUP_ALERT_CHILDREN else Notification.GROUP_ALERT_SUMMARY)
            .setOnlyAlertOnce(false)
            .addExtras(Bundle().apply { putString(EXTRA_FINGERPRINT, content.fingerprint) })
            .build()

    private fun title(content: ReminderContent): String =
        if (content.dose.isBlank()) content.medicine else context.getString(R.string.reminder_title, content.medicine, content.dose)

    private fun text(content: ReminderContent): String =
        if (content.instructions.isBlank()) {
            content.treatment
        } else {
            context.getString(R.string.reminder_text, content.treatment, content.instructions)
        }

    private fun action(
        key: TakeKey,
        action: ReminderAction,
        label: Int,
    ): Notification.Action =
        Notification.Action.Builder(null, context.getString(label), ReminderIntents.answer(context, action, key)).build()

    private fun tag(key: TakeKey) = "$tagPrefix/${TakeKeyCodec.encode(key)}"

    companion object {
        /** The app's own prefix; instrumented tests use another. */
        const val TAG_PREFIX = "take"

        /** Every notification of the shade has this ID; the tag tells them apart. */
        const val ID = 1
        private const val EXTRA_FINGERPRINT = "io.github.davamix.pillsreminder.extra.FINGERPRINT"
        private const val EXTRA_COUNT = "io.github.davamix.pillsreminder.extra.COUNT"
    }
}
