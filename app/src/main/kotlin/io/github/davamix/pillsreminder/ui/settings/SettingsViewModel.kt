package io.github.davamix.pillsreminder.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davamix.pillsreminder.check.HyperOsStep
import io.github.davamix.pillsreminder.check.reminderCheck
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import io.github.davamix.pillsreminder.ui.treatments.STOP_AFTER_MILLIS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Settings' ViewModel: the reminder check (RM-9, P7 SPEC §4.3.7–§4.3.10), from what the device says, read at the start
 * and each time the screen is resumed, and from the check's record. At the first launch ([firstLaunch]) it asks once
 * for the notification permission; [saved] keeps that it asked, so a rebuilt screen doesn't ask again.
 */
class SettingsViewModel(
    private val deps: ScreenDependencies,
    private val firstLaunch: Boolean,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val device = MutableStateFlow(deps.checkDevice.read())
    private val problem = MutableStateFlow<ChangeProblem?>(null)

    val state: StateFlow<SettingsState?> =
        combine(device, deps.checkStore.record, saved.getStateFlow(ASKED_KEY, false), problem) { device, record, asked, problem ->
            SettingsState(
                check = reminderCheck(device, record),
                askNotifications = firstLaunch && !asked && !device.notificationsAllowed,
                problem = problem,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    /** Reads the device again: the screen is resumed, maybe after a change in Android's settings (§4.3.9). */
    fun refresh() {
        device.value = deps.checkDevice.read()
    }

    /** Marks a HyperOS step done, or not (Q4). When the record can't be written, it says so and the box goes back. */
    fun setConfirmed(
        step: HyperOsStep,
        confirmed: Boolean,
    ) {
        viewModelScope.launch {
            try {
                deps.checkStore.update { record ->
                    record.copy(confirmed = if (confirmed) record.confirmed + step else record.confirmed - step)
                }
            } catch (e: IOException) {
                problem.value = ChangeProblem.NOT_SAVED
            }
        }
    }

    /** Android's dialog is about to ask for the notification permission: never again from this screen (§4.3.10). */
    fun notificationsRequested() {
        saved[ASKED_KEY] = true
    }

    /** The dialog's answer. Once granted, the reminders catch up at once (P7-D6): the dialog doesn't restart the app. */
    fun notificationsAsked(granted: Boolean) {
        saved[ASKED_KEY] = true
        refresh()
        if (granted) deps.reconcile()
    }

    fun problemShown() {
        problem.value = null
    }

    companion object {
        /** In the [SavedStateHandle]: the first launch's dialog was shown. */
        internal const val ASKED_KEY = "askedNotifications"

        fun factory(
            deps: ScreenDependencies,
            firstLaunch: Boolean,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { SettingsViewModel(deps, firstLaunch, createSavedStateHandle()) } }
    }
}
