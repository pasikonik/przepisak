package pl.przepisak.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pl.przepisak.data.local.entity.IngredientEntity
import pl.przepisak.data.local.entity.IngredientGroupEntity
import pl.przepisak.data.local.entity.RecipeEntity
import pl.przepisak.data.local.entity.RecipeFtsEntity
import pl.przepisak.data.local.entity.RecipeTagCrossRef
import pl.przepisak.data.local.entity.StepEntity
import pl.przepisak.data.local.relation.RecipeListItemView
import pl.przepisak.data.local.relation.RecipeWithDetails

@Dao
interface RecipeDao {

    // ------------------------------------------------------------------
    // Odczyt
    // ------------------------------------------------------------------

    /**
     * Lista przepisów z filtrowaniem i sortowaniem (RF-03).
     *
     * Konwencja parametrów-przełączników: `:ignoreX = 1` wyłącza dany warunek.
     * Jest to konieczne, bo SQLite nie akceptuje `IN ()` — dla nieaktywnego filtru
     * repozytorium przekazuje jednoelementową listę-atrapę, a warunek i tak nie
     * jest sprawdzany.
     *
     * Sortowanie: dokładnie jedna gałąź `CASE` zwraca wartość, pozostałe dają
     * `NULL` dla każdego wiersza, więc nie wpływają na kolejność.
     */
    @Query(
        """
        SELECT
            r.id AS id,
            r.title AS title,
            r.description AS description,
            r.photo_local_file AS photo_local_file,
            r.photo_remote_path AS photo_remote_path,
            r.photo_source_url AS photo_source_url,
            r.difficulty AS difficulty,
            r.servings AS servings,
            r.is_favorite AS is_favorite,
            r.rating AS rating,
            r.updated_at AS updated_at,
            COALESCE(
                r.total_time_minutes,
                NULLIF(IFNULL(r.prep_time_minutes, 0) + IFNULL(r.cook_time_minutes, 0), 0)
            ) AS effective_time,
            c.name AS category_name,
            (
                SELECT GROUP_CONCAT(t.name, '|')
                FROM recipe_tags rt
                JOIN tags t ON t.id = rt.tag_id
                WHERE rt.recipe_id = r.id
            ) AS tag_names
        FROM recipes r
        LEFT JOIN categories c ON c.id = r.category_id
        WHERE r.deleted_at IS NULL
          AND (:ignoreQuery = 1 OR r.id IN (SELECT rowid FROM recipe_fts WHERE recipe_fts MATCH :ftsQuery))
          AND (:ignoreCategories = 1 OR r.category_id IN (:categoryIds))
          AND (:ignoreDifficulties = 1 OR r.difficulty IN (:difficulties))
          AND (:maxTotalTime IS NULL OR COALESCE(
                    r.total_time_minutes,
                    IFNULL(r.prep_time_minutes, 0) + IFNULL(r.cook_time_minutes, 0)
              ) <= :maxTotalTime)
          AND (:onlyFavorites = 0 OR r.is_favorite = 1)
          AND (:minRating IS NULL OR r.rating >= :minRating)
          AND (:requiredTagCount = 0 OR (
                SELECT COUNT(DISTINCT rt.tag_id)
                FROM recipe_tags rt
                WHERE rt.recipe_id = r.id AND rt.tag_id IN (:tagIds)
              ) = :requiredTagCount)
        ORDER BY
            CASE WHEN :sortIndex = 1 THEN r.title_normalized END ASC,
            CASE WHEN :sortIndex = 2 THEN COALESCE(
                    r.total_time_minutes,
                    NULLIF(IFNULL(r.prep_time_minutes, 0) + IFNULL(r.cook_time_minutes, 0), 0),
                    999999
                 ) END ASC,
            CASE WHEN :sortIndex = 3 THEN r.rating END DESC,
            r.updated_at DESC
        """,
    )
    fun observeFiltered(
        ignoreQuery: Int,
        ftsQuery: String,
        ignoreCategories: Int,
        categoryIds: List<Long>,
        ignoreDifficulties: Int,
        difficulties: List<String>,
        maxTotalTime: Int?,
        onlyFavorites: Int,
        minRating: Int?,
        requiredTagCount: Int,
        tagIds: List<Long>,
        sortIndex: Int,
    ): Flow<List<RecipeListItemView>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId AND deleted_at IS NULL")
    fun observeDetails(recipeId: Long): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun getDetails(recipeId: Long): RecipeWithDetails?

    @Transaction
    @Query("SELECT * FROM recipes WHERE remote_id = :remoteId")
    suspend fun getDetailsByRemoteId(remoteId: String): RecipeWithDetails?

    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun getRecipe(recipeId: Long): RecipeEntity?

    @Query("SELECT COUNT(*) FROM recipes WHERE deleted_at IS NULL")
    fun observeRecipeCount(): Flow<Int>

    @Query("SELECT id FROM recipes WHERE deleted_at IS NULL ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomRecipeId(): Long?

    /** Wszystkie ścieżki zdjęć w bazie — używane przy porządkowaniu plików. */
    @Query(
        """
        SELECT photo_local_file FROM recipes WHERE photo_local_file IS NOT NULL
        UNION
        SELECT photo_local_file FROM steps WHERE photo_local_file IS NOT NULL
        """,
    )
    suspend fun getAllLocalPhotoNames(): List<String>

    // ------------------------------------------------------------------
    // Zapis — pojedyncze tabele
    // ------------------------------------------------------------------

    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Upsert
    suspend fun upsertRecipe(recipe: RecipeEntity): Long

    @Insert
    suspend fun insertGroups(groups: List<IngredientGroupEntity>): List<Long>

    @Insert
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Insert
    suspend fun insertSteps(steps: List<StepEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeTags(refs: List<RecipeTagCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFts(row: RecipeFtsEntity)

    @Query("DELETE FROM recipe_fts WHERE rowid = :recipeId")
    suspend fun deleteFts(recipeId: Long)

    @Query("DELETE FROM ingredient_groups WHERE recipe_id = :recipeId")
    suspend fun deleteGroupsFor(recipeId: Long)

    @Query("DELETE FROM ingredients WHERE recipe_id = :recipeId")
    suspend fun deleteIngredientsFor(recipeId: Long)

    @Query("DELETE FROM steps WHERE recipe_id = :recipeId")
    suspend fun deleteStepsFor(recipeId: Long)

    @Query("DELETE FROM recipe_tags WHERE recipe_id = :recipeId")
    suspend fun deleteTagLinksFor(recipeId: Long)

    @Delete
    suspend fun deleteRecipeRow(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipeHard(recipeId: Long)

    // ------------------------------------------------------------------
    // Szybkie akcje z listy / szczegółów
    // ------------------------------------------------------------------

    @Query("UPDATE recipes SET is_favorite = :favorite, is_dirty = 1, updated_at = :nowMs WHERE id = :recipeId")
    suspend fun setFavorite(recipeId: Long, favorite: Boolean, nowMs: Long)

    @Query("UPDATE recipes SET rating = :rating, is_dirty = 1, updated_at = :nowMs WHERE id = :recipeId")
    suspend fun setRating(recipeId: Long, rating: Int?, nowMs: Long)

    @Query("UPDATE recipes SET notes = :notes, is_dirty = 1, updated_at = :nowMs WHERE id = :recipeId")
    suspend fun setNotes(recipeId: Long, notes: String?, nowMs: Long)

    /** Miękkie usunięcie — „nagrobek”, by usunięcie dotarło na inne urządzenia (RF-15). */
    @Query("UPDATE recipes SET deleted_at = :nowMs, is_dirty = 1, updated_at = :nowMs WHERE id = :recipeId")
    suspend fun softDelete(recipeId: Long, nowMs: Long)

    @Query("UPDATE recipes SET deleted_at = NULL, is_dirty = 1, updated_at = :nowMs WHERE id = :recipeId")
    suspend fun restore(recipeId: Long, nowMs: Long)

    // ------------------------------------------------------------------
    // Synchronizacja (RF-15)
    // ------------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM recipes WHERE is_dirty = 1")
    suspend fun getDirtyRecipes(): List<RecipeWithDetails>

    @Query("SELECT COUNT(*) FROM recipes WHERE is_dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("UPDATE recipes SET is_dirty = 0, last_synced_at = :nowMs WHERE id = :recipeId AND updated_at <= :syncedUpdatedAt")
    suspend fun markSynced(recipeId: Long, syncedUpdatedAt: Long, nowMs: Long)

    @Query("UPDATE recipes SET photo_remote_path = :remotePath WHERE id = :recipeId")
    suspend fun setRecipePhotoRemotePath(recipeId: Long, remotePath: String?)

    @Query("UPDATE steps SET photo_remote_path = :remotePath WHERE id = :stepId")
    suspend fun setStepPhotoRemotePath(stepId: Long, remotePath: String?)

    /**
     * Ustawia nazwę pliku lokalnego po pobraniu zdjęcia z chmury. Nazwa nadawana
     * przy zapisie jest nowa, więc trzeba ją wpisać do właściwego wiersza.
     */
    @Query("UPDATE recipes SET photo_local_file = :fileName WHERE id = :recipeId")
    suspend fun setRecipePhotoLocalFile(recipeId: Long, fileName: String?)

    @Query("UPDATE steps SET photo_local_file = :fileName WHERE id = :stepId")
    suspend fun setStepPhotoLocalFile(stepId: Long, fileName: String?)

    @Query("DELETE FROM recipes WHERE deleted_at IS NOT NULL AND is_dirty = 0")
    suspend fun purgeSyncedTombstones()
}
