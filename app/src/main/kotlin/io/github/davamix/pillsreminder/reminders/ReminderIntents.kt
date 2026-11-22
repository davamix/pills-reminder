package io.github.davamix.pillsreminder.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.davamix.pillsreminder.MainActivity
import io.github.davamix.pillsreminder.core.model.TakeKey

/**
 * The reminders' intents (P5 SPEC §4.3.3–§4.3.5, §4.3.8): each explicit, to one of the app's own components, and
 * immutable. A take's intents carry its key as text ([TakeKeyCodec]), in an extra and in the intent's identifier, so
 * each take's `PendingIntent`s are distinct.
 */
object ReminderIntents {
    /** The alarm's action. Instrumented tests add a suffix of their own (P5-D11). */
    const val ACTION_WAKE_UP = "io.github.davamix.pillsreminder.action.WAKE_UP"
    private const val ACTION_ANSWER = "io.github.davamix.pillsreminder.action.ANSWER_"
    private const val EXTRA_TAKE_KEY = "io.github.davamix.pillsreminder.extra.TAKE_KEY"
    private const val EXTRA_RETRY = "io.github.davamix.pillsreminder.extra.RETRY"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** The alarm's `PendingIntent`, to [AlarmReceiver]; `null` with [PendingIntent.FLAG_NO_CREATE] when it isn't set. */
    fun wakeUp(
        context: Context,
        action: String,
        retry: Boolean = false,
        flags: Int = FLAGS,
    ): PendingIntent? {
        val intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .setIdentifier(action)
                .putExtra(EXTRA_RETRY, retry)
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /** Whether [intent] is the alarm's, the app's or a test's (any action starting with [ACTION_WAKE_UP]). */
    fun isWakeUp(intent: Intent): Boolean = intent.action?.startsWith(ACTION_WAKE_UP) == true

    /** Whether the alarm was set as a retry after a failed write (Q1). */
    fun isRetry(intent: Intent): Boolean = intent.getBooleanExtra(EXTRA_RETRY, false)

    /** Taken, Snooze or Skip on the take's reminder, to [ReminderActionReceiver] (RM-3). */
    fun answer(
        context: Context,
        action: ReminderAction,
        key: TakeKey,
    ): PendingIntent {
        val text = TakeKeyCodec.encode(key)
        val intent =
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(ACTION_ANSWER + action.name)
                .setIdentifier("${action.name}/$text")
                .putExtra(EXTRA_TAKE_KEY, text)
        return PendingIntent.getBroadcast(context, 0, intent, FLAGS)
    }

    /** The action of an answer's intent, or `null` if it isn't one. */
    fun actionOf(intent: Intent): ReminderAction? = ReminderAction.entries.firstOrNull { intent.action == ACTION_ANSWER + it.name }

    /** Opens the app on the take (RM-6, §4.3.8): an existing activity gets it in `onNewIntent`. */
    fun openTake(
        context: Context,
        key: TakeKey,
    ): PendingIntent = PendingIntent.getActivity(context, 0, openTakeIntent(context, key), FLAGS)

    /** [openTake]'s intent, which `MainActivity` reads with [keyOf]. */
    internal fun openTakeIntent(
        context: Context,
        key: TakeKey,
    ): Intent {
        val text = TakeKeyCodec.encode(key)
        return Intent(context, MainActivity::class.java)
            .setIdentifier("open/$text")
            .putExtra(EXTRA_TAKE_KEY, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /**
     * Opens the app: the summary's tap, and the alarm clock's in quick settings. On Home when the app starts; an
     * existing activity stays on the screen it was on.
     */
    fun openApp(context: Context): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .setIdentifier("open")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, FLAGS)
    }

    /** The take a reminder's intent is for, or `null` when it carries no key the app wrote. */
    fun keyOf(intent: Intent): TakeKey? = intent.getStringExtra(EXTRA_TAKE_KEY)?.let(TakeKeyCodec::decode)
}
