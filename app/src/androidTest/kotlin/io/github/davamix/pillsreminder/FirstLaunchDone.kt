package io.github.davamix.pillsreminder

import androidx.test.core.app.ApplicationProvider
import io.github.davamix.pillsreminder.check.CheckRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * For tests that go through `MainActivity`, which reads the app's real check record: marks the first launch done for
 * the test, so Home doesn't open the check, nor Android's permission dialog with it, and writes back the record it
 * found afterwards (P7 SPEC §6; P3: leave the data as it was found). It must run outside the activity's rule:
 * `@get:Rule(order = 0)`, with the activity's rule at a higher order.
 */
class FirstLaunchDone : ExternalResource() {
    private val store get() = ApplicationProvider.getApplicationContext<PillsReminderApp>().container.checkStore
    private var found: CheckRecord? = null

    override fun before() =
        runBlocking {
            val record = store.record.first()
            found = record
            store.update { it.copy(firstLaunchDone = true) }
        }

    override fun after() =
        runBlocking {
            val record = found ?: return@runBlocking
            store.update { record }
        }
}
