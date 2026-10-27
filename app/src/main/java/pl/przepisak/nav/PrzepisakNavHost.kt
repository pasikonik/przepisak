package pl.przepisak.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import pl.przepisak.feature.cook.KitchenModeScreen
import pl.przepisak.feature.importflow.ImportPreviewScreen
import pl.przepisak.feature.recipedetail.RecipeDetailScreen
import pl.przepisak.feature.recipeedit.RecipeEditScreen
import pl.przepisak.feature.recipelist.RecipeListScreen
import pl.przepisak.feature.settings.SettingsScreen
import pl.przepisak.feature.timers.TimersScreen
import pl.przepisak.feature.tools.PanCalculatorScreen
import pl.przepisak.feature.tools.ToolsScreen
import pl.przepisak.feature.tools.WeightConverterScreen

// Bez dolnego paska nawigacji — Ustawienia i Minutniki mają własne wejścia (ikony
// w pasku listy przepisów), więc osobna „zakładka” tylko zabierałaby miejsce na
// ekranie. Każdy ekran to teraz zwykłe wejście na stos nawigacji, z przyciskiem
// „wstecz”, a nie stały element interfejsu.
@Composable
fun PrzepisakApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier,
        // Bez tego zewnętrzny Scaffold (który nie ma własnego topBar) i tak rezerwuje
        // odstęp na pasek stanu — a każdy ekran ma JESZCZE JEDEN, własny Scaffold z
        // topBar, który robi to samo. Efekt: podwójny, zbędny odstęp nad każdym
        // topBar w całej aplikacji.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        PrzepisakNavHost(
            navController = navController,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun PrzepisakNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = RecipeListRoute,
        modifier = modifier,
    ) {
        composable<RecipeListRoute> {
            RecipeListScreen(
                onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) },
                onAddRecipe = { navController.navigate(RecipeEditRoute()) },
                onImportText = { text -> navController.navigate(ImportPreviewRoute(text)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTimers = { navController.navigate(TimersRoute) },
            )
        }

        composable<RecipeDetailRoute> { entry ->
            val route = entry.toRoute<RecipeDetailRoute>()
            RecipeDetailScreen(
                recipeId = route.recipeId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(RecipeEditRoute(it)) },
                onCook = { id, servings -> navController.navigate(CookRoute(id, servings)) },
                onOpenPanCalculator = { navController.navigate(PanCalculatorRoute) },
            )
        }

        composable<RecipeEditRoute> { entry ->
            val route = entry.toRoute<RecipeEditRoute>()
            RecipeEditScreen(
                recipeId = route.recipeId,
                onSaved = { savedId ->
                    // Po zapisie wracamy na listę i dopiero z niej otwieramy szczegóły,
                    // żeby przycisk „wstecz” nie wprowadzał użytkownika ponownie w edycję.
                    navController.popBackStack()
                    if (route.recipeId == 0L) navController.navigate(RecipeDetailRoute(savedId))
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable<CookRoute> { entry ->
            val route = entry.toRoute<CookRoute>()
            KitchenModeScreen(
                recipeId = route.recipeId,
                initialServings = route.servings,
                onExit = { navController.popBackStack() },
            )
        }

        composable<ToolsRoute> {
            ToolsScreen(
                onOpenPanCalculator = { navController.navigate(PanCalculatorRoute) },
                onOpenWeightConverter = { navController.navigate(WeightConverterRoute) },
                onOpenTimers = { navController.navigate(TimersRoute) },
            )
        }

        composable<PanCalculatorRoute> {
            PanCalculatorScreen(onBack = { navController.popBackStack() })
        }

        composable<WeightConverterRoute> {
            WeightConverterScreen(onBack = { navController.popBackStack() })
        }

        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable<TimersRoute> {
            TimersScreen(onBack = { navController.popBackStack() })
        }

        composable<ImportPreviewRoute> { entry ->
            val route = entry.toRoute<ImportPreviewRoute>()
            ImportPreviewScreen(
                url = route.url,
                onSaved = { savedId ->
                    navController.popBackStack()
                    navController.navigate(RecipeDetailRoute(savedId))
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
