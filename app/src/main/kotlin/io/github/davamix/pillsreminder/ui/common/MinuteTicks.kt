package io.github.davamix.pillsreminder.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock

private const val MINUTE_MILLIS = 60_000L

/**
 * Emits at once, then at each whole minute of [clock], for as long as it is collected (P6-D3). Planned times are whole
 * minutes, so a screen that works out its state again at each tick shows a take turn Due at its minute.
 *
 * Each wait is worked out again from the clock, so a wait that ends a little early is followed by a short one, and a
 * change of the phone's time is caught up at the next tick. Whole minutes are the same in every zone the phone uses.
 */
fun minuteTicks(clock: Clock): Flow<Unit> =
    flow {
        while (true) {
            emit(Unit)
            delay(MINUTE_MILLIS - Math.floorMod(clock.millis(), MINUTE_MILLIS))
        }
    }
