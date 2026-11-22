package io.github.davamix.pillsreminder.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.UnreadableDataException
import java.io.IOException

/**
 * What went wrong with a change, for the screen to say (P6 SPEC §4.3.10). It never holds an exception's message, which
 * could hold treatment content (plan §3).
 */
enum class ChangeProblem(
    @param:StringRes val message: Int,
) {
    /** The stored data can't be read, so nothing can be changed (P3 Q1). */
    UNREADABLE(R.string.problem_unreadable),

    /** The write failed: the change wasn't saved. */
    NOT_SAVED(R.string.problem_not_saved),

    /** P2's rules refused the change: the take or the treatment changed in the meantime. */
    NOT_CHANGED(R.string.problem_not_changed),
}

/**
 * Runs [change], one of `core.actions`' repository helpers, and says what went wrong, or `null` if it was applied.
 * [change] returns whether it was applied. Any other exception is a bug, and isn't caught.
 */
suspend fun attempt(change: suspend () -> Boolean): ChangeProblem? =
    try {
        if (change()) null else ChangeProblem.NOT_CHANGED
    } catch (e: UnreadableDataException) {
        ChangeProblem.UNREADABLE
    } catch (e: IOException) {
        ChangeProblem.NOT_SAVED
    }

/** In place of a screen's content when the stored data can't be read (plan §2.4). The app offers no repair. */
@Composable
fun UnreadableMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = stringResource(R.string.problem_unreadable), style = MaterialTheme.typography.bodyLarge)
    }
}
