package io.github.davamix.pillsreminder.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import io.github.davamix.pillsreminder.ui.common.rememberAppDependencies
import io.github.davamix.pillsreminder.ui.home.HomeScreen
import io.github.davamix.pillsreminder.ui.settings.SettingsScreen
import io.github.davamix.pillsreminder.ui.take.TakeScreen
import io.github.davamix.pillsreminder.ui.treatment.TreatmentScreen
import io.github.davamix.pillsreminder.ui.treatments.TreatmentsScreen
import kotlinx.coroutines.flow.first

/**
 * The app's navigation graph. Each phase that builds a screen fills in its own destination. A click that navigates goes
 * through [dropUnlessResumed], so a double tap can't open a screen twice, or go back twice.
 *
 * [openTake] is a take to open from a tapped reminder (P5); [onTakeOpened] is called once it is open, to clear it.
 * [dependencies] are what the screens' ViewModels use: the app's, or fakes in UI tests (P6-D2).
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    openTake: TakeRoute? = null,
    onTakeOpened: () -> Unit = {},
    dependencies: ScreenDependencies = rememberAppDependencies(),
) {
    OpenTake(navController, openTake, onTakeOpened)
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                deps = dependencies,
                onOpenTreatment = dropUnlessResumedWith { id: TreatmentId -> navController.navigate(TreatmentRoute(id.value)) },
                onAllTreatments = dropUnlessResumed { navController.navigate(TreatmentsRoute) },
                onNewTreatment = dropUnlessResumed { navController.navigate(TreatmentEditorRoute()) },
                onSettings = dropUnlessResumed { navController.navigate(SettingsRoute()) },
                // Driven by Home's state, once resumed: a plain lambda (CLAUDE.md, "Navigation driven by a ViewModel state").
                onOpenCheck = { navController.navigate(SettingsRoute(firstLaunch = true)) },
            )
        }
        composable<TreatmentsRoute> {
            TreatmentsScreen(
                deps = dependencies,
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpen = dropUnlessResumedWith { id: TreatmentId -> navController.navigate(TreatmentRoute(id.value)) },
                onNew = dropUnlessResumed { navController.navigate(TreatmentEditorRoute()) },
            )
        }
        composable<TreatmentRoute> { entry ->
            val route = entry.toRoute<TreatmentRoute>()
            TreatmentScreen(
                deps = dependencies,
                treatmentId = TreatmentId(route.treatmentId),
                onBack = dropUnlessResumed { navController.popBackStack() },
                onEdit = dropUnlessResumed { navController.navigate(TreatmentEditorRoute(route.treatmentId)) },
                onOpenTake =
                    dropUnlessResumedWith { key: TakeKey ->
                        navController.navigate(TakeRoute(key.medicineId.value, key.plannedAt.toString()))
                    },
                onDeleted = { navController.popBackStack() },
            )
        }
        editorGraph(navController, dependencies)
        composable<TakeRoute> { entry ->
            val route = entry.toRoute<TakeRoute>()
            TakeScreen(
                deps = dependencies,
                medicineId = route.medicineId,
                plannedAt = route.plannedAt,
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
    }
}

/**
 * Opens [take] once, then calls [onOpened] (RM-6, P5 SPEC §4.3.8). Usually right above Home, so Back returns to Home and
 * whatever was open above Home closes. While the editors are open, above the screen on top instead, so Back returns to
 * them with their changes; a take already on top is replaced, not stacked (P6 Q3). It waits for the activity to be
 * resumed: an activity Android rebuilds is composed before that (CLAUDE.md, "Navigation driven by a ViewModel state").
 */
@Composable
private fun OpenTake(
    navController: NavHostController,
    take: TakeRoute?,
    onOpened: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnOpened by rememberUpdatedState(onOpened)
    LaunchedEffect(take) {
        if (take == null) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        val editorOpen = navController.hasEntry<TreatmentEditorRoute>()
        val takeOnTop = navController.currentBackStackEntry?.destination?.hasRoute<TakeRoute>() == true
        navController.navigate(take) {
            when {
                !editorOpen -> popUpTo<HomeRoute>()
                takeOnTop -> popUpTo<TakeRoute> { inclusive = true }
            }
            launchSingleTop = true
        }
        currentOnOpened()
    }
}

/** Whether the back stack holds an entry of route [T]. */
private inline fun <reified T : Any> NavHostController.hasEntry(): Boolean =
    try {
        getBackStackEntry<T>()
        true
    } catch (e: IllegalArgumentException) {
        false
    }
