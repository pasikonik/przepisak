package pl.przepisak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.przepisak.data.local.entity.CategoryEntity
import pl.przepisak.data.local.entity.TagEntity

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY position ASC, name_normalized ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position ASC, name_normalized ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name_normalized = :normalized LIMIT 1")
    suspend fun findByNormalizedName(normalized: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    /** Liczba przepisów w każdej kategorii — do odznak na filtrach. */
    @Query(
        """
        SELECT c.id AS categoryId, COUNT(r.id) AS recipeCount
        FROM categories c
        LEFT JOIN recipes r ON r.category_id = c.id AND r.deleted_at IS NULL
        GROUP BY c.id
        """,
    )
    fun observeUsageCounts(): Flow<List<CategoryUsage>>
}

data class CategoryUsage(val categoryId: Long, val recipeCount: Int)

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name_normalized ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name_normalized ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name_normalized = :normalized LIMIT 1")
    suspend fun findByNormalizedName(normalized: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Tagi, do których nie odwołuje się żaden przepis — sprzątamy je po edycji,
     * żeby lista filtrów nie zapełniała się śmieciami.
     */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM recipe_tags)")
    suspend fun deleteOrphans()

    @Query(
        """
        SELECT t.id AS tagId, COUNT(rt.recipe_id) AS recipeCount
        FROM tags t
        LEFT JOIN recipe_tags rt ON rt.tag_id = t.id
        LEFT JOIN recipes r ON r.id = rt.recipe_id AND r.deleted_at IS NULL
        GROUP BY t.id
        """,
    )
    fun observeUsageCounts(): Flow<List<TagUsage>>
}

data class TagUsage(val tagId: Long, val recipeCount: Int)
