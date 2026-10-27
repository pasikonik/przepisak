package pl.przepisak.data.repository

import kotlinx.coroutines.flow.Flow
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.model.RecipeFilter
import pl.przepisak.domain.model.RecipeListItem

/**
 * Jedyne wejście do przepisów. Wszystko czyta i pisze do lokalnej bazy — także
 * wtedy, gdy nie ma sieci (RNF-03). Synchronizacja jest procesem osobnym i
 * asynchronicznym; zapis nigdy na nią nie czeka.
 */
interface RecipeRepository {

    /** Lista przepisów zawężona filtrem (RF-03). */
    fun observeRecipes(filter: RecipeFilter): Flow<List<RecipeListItem>>

    fun observeRecipe(recipeId: Long): Flow<RecipeAggregate?>

    suspend fun getRecipe(recipeId: Long): RecipeAggregate?

    /** Nowy, pusty przepis z nadanym UUID — jeszcze niezapisany. */
    fun newDraft(): RecipeAggregate

    /**
     * Zapisuje cały przepis w jednej transakcji: nagłówek, grupy składników,
     * kroki, tagi i wiersz indeksu pełnotekstowego. Zwraca identyfikator przepisu.
     *
     * Tagi i kategoria z `id == 0` i niepustą nazwą są zakładane w locie.
     */
    suspend fun saveRecipe(aggregate: RecipeAggregate): Long

    /** Miękkie usunięcie — przepis znika z listy, ale „nagrobek” dojedzie do chmury. */
    suspend fun deleteRecipe(recipeId: Long)

    suspend fun restoreRecipe(recipeId: Long)

    suspend fun duplicateRecipe(recipeId: Long): Long?

    suspend fun setFavorite(recipeId: Long, favorite: Boolean)

    suspend fun setRating(recipeId: Long, rating: Int?)

    suspend fun setNotes(recipeId: Long, notes: String?)

    fun observeRecipeCount(): Flow<Int>

    /** Losowy przepis — dla przycisku „zaskocz mnie”. */
    suspend fun getRandomRecipeId(): Long?
}
