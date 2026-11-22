package io.github.davamix.pillsreminder.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import io.github.davamix.pillsreminder.ui.editor.EditorViewModel
import io.github.davamix.pillsreminder.ui.editor.MedicineEditorScreen
import io.github.davamix.pillsreminder.ui.editor.TreatmentEditorScreen

/**
 * The editors (P6-D1): a nested graph on [TreatmentEditorRoute], whose two destinations share one [EditorViewModel],
 * scoped to the graph's back-stack entry, so the draft lives as long as the editors are open.
 */
fun NavGraphBuilder.editorGraph(
    navController: NavHostController,
    dependencies: ScreenDependencies,
) {
    navigation<TreatmentEditorRoute>(startDestination = TreatmentFormRoute) {
        composable<TreatmentFormRoute> { entry ->
            val graph = remember(entry) { navController.getBackStackEntry<TreatmentEditorRoute>() }
            val isNew = graph.toRoute<TreatmentEditorRoute>().treatmentId == null
            TreatmentEditorScreen(
                viewModel = editorViewModel(graph, dependencies),
                onLeave = dropUnlessResumed { navController.popBackStack<TreatmentEditorRoute>(inclusive = true) },
                onEditMedicine =
                    dropUnlessResumedWith { id: MedicineId? -> navController.navigate(MedicineEditorRoute(id?.value)) },
                onSaved = { id ->
                    // A new treatment opens its own screen in place of the editors; an edit returns to where it came from.
                    if (isNew) {
                        navController.navigate(TreatmentRoute(id.value)) { popUpTo<TreatmentEditorRoute> { inclusive = true } }
                    } else {
                        navController.popBackStack<TreatmentEditorRoute>(inclusive = true)
                    }
                },
            )
        }
        composable<MedicineEditorRoute> { entry ->
            val graph = remember(entry) { navController.getBackStackEntry<TreatmentEditorRoute>() }
            MedicineEditorScreen(
                viewModel = editorViewModel(graph, dependencies),
                medicineId = entry.toRoute<MedicineEditorRoute>().medicineId?.let(::MedicineId),
                entry = entry.id,
                onClose = dropUnlessResumed { navController.popBackStack() },
            )
        }
    }
}

/** The editors' shared ViewModel, from the graph's back-stack entry, which also holds its saved state (P6-D6). */
@Composable
private fun editorViewModel(
    graph: NavBackStackEntry,
    dependencies: ScreenDependencies,
): EditorViewModel {
    val treatmentId = graph.toRoute<TreatmentEditorRoute>().treatmentId?.let(::TreatmentId)
    return viewModel(viewModelStoreOwner = graph, factory = EditorViewModel.factory(dependencies, treatmentId))
}
