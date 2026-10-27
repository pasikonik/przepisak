package pl.przepisak.nav

import kotlinx.serialization.Serializable

/**
 * Trasy nawigacji. Korzystamy z typowanej nawigacji (navigation-compose 2.9):
 * argumenty są zwykłymi polami klasy, więc kompilator pilnuje ich typów zamiast
 * nas — nie ma ręcznego składania i parsowania adresów.
 */

@Serializable
data object RecipeListRoute

@Serializable
data class RecipeDetailRoute(val recipeId: Long)

/** `recipeId == 0` oznacza nowy przepis. */
@Serializable
data class RecipeEditRoute(val recipeId: Long = 0L)

/** `servings == 0` oznacza „użyj liczby porcji z przepisu”. */
@Serializable
data class CookRoute(val recipeId: Long, val servings: Int = 0)

@Serializable
data object ToolsRoute

@Serializable
data object PanCalculatorRoute

@Serializable
data object WeightConverterRoute

@Serializable
data object SettingsRoute

@Serializable
data object TimersRoute

@Serializable
data class ImportPreviewRoute(val url: String)
