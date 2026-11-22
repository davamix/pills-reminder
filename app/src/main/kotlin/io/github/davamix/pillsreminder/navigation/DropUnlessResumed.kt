package io.github.davamix.pillsreminder.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed

/**
 * [dropUnlessResumed] for a click that carries a value, such as the treatment a row opens: [block] runs only while the
 * screen is resumed, so a double tap can't open a screen twice (CLAUDE.md).
 */
@Composable
fun <T> dropUnlessResumedWith(block: (T) -> Unit): (T) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { value -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(value) }
}
