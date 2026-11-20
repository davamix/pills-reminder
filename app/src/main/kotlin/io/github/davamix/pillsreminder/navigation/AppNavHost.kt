package io.github.davamix.pillsreminder.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.davamix.pillsreminder.ui.editor.MedicineEditorScreen
import io.github.davamix.pillsreminder.ui.editor.TreatmentEditorScreen
import io.github.davamix.pillsreminder.ui.home.HomeScreen
import io.github.davamix.pillsreminder.ui.settings.SettingsScreen
import io.github.davamix.pillsreminder.ui.take.TakeScreen
import io.github.davamix.pillsreminder.ui.treatment.TreatmentScreen
import io.github.davamix.pillsreminder.ui.treatments.TreatmentsScreen

// Placeholder targets, until the real screens exist (P6, P7).
private const val SAMPLE_ID = "sample"
private val sampleTake = TakeRoute(medicineId = SAMPLE_ID, plannedAt = "2026-11-23T16:00")

/**
 * The app's navigation graph. Each phase that builds a screen fills in its own destination. A click that navigates goes
 * through [dropUnlessResumed], so a double tap can't open a screen twice, or go back twice.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onAllTreatments = dropUnlessResumed { navController.navigate(TreatmentsRoute) },
                onNewTreatment = dropUnlessResumed { navController.navigate(TreatmentEditorRoute()) },
                onSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
                onSampleTake = dropUnlessResumed { navController.navigate(sampleTake) },
            )
        }
        composable<TreatmentsRoute> {
            TreatmentsScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenSample = dropUnlessResumed { navController.navigate(TreatmentRoute(SAMPLE_ID)) },
                onNewTreatment = dropUnlessResumed { navController.navigate(TreatmentEditorRoute()) },
            )
        }
        composable<TreatmentRoute> { entry ->
            val route = entry.toRoute<TreatmentRoute>()
            TreatmentScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onEdit = dropUnlessResumed { navController.navigate(TreatmentEditorRoute(route.treatmentId)) },
                onOpenTake = dropUnlessResumed { navController.navigate(sampleTake) },
            )
        }
        composable<TreatmentEditorRoute> {
            TreatmentEditorScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onAddMedicine = dropUnlessResumed { navController.navigate(MedicineEditorRoute()) },
            )
        }
        composable<MedicineEditorRoute> {
            MedicineEditorScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<TakeRoute> {
            TakeScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
    }
}
