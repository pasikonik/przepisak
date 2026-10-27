package pl.przepisak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pl.przepisak.data.local.entity.IngredientDensityEntity

/** Dostęp do wbudowanej bazy wagowej produktów (RF-10). */
@Dao
interface DensityDao {

    @Query("SELECT * FROM ingredient_density ORDER BY category ASC, name_normalized ASC")
    fun observeAll(): Flow<List<IngredientDensityEntity>>

    @Query("SELECT * FROM ingredient_density WHERE id = :id")
    suspend fun getById(id: Long): IngredientDensityEntity?

    @Query("SELECT * FROM ingredient_density WHERE name_normalized = :normalized LIMIT 1")
    suspend fun findExact(normalized: String): IngredientDensityEntity?

    /**
     * Wyszukiwanie po nazwie i aliasach, bez czułości na diakrytyki (wołający
     * podaje tekst już znormalizowany).
     *
     * Kolejność wyników: najpierw trafienia od początku nazwy, potem zawierające
     * frazę w środku, na końcu trafienia po aliasie — to odpowiada temu, czego
     * użytkownik zwykle szuka.
     */
    @Query(
        """
        SELECT * FROM ingredient_density
        WHERE name_normalized LIKE '%' || :query || '%'
           OR aliases LIKE '%' || :query || '%'
        ORDER BY
            CASE
                WHEN name_normalized = :query THEN 0
                WHEN name_normalized LIKE :query || '%' THEN 1
                WHEN name_normalized LIKE '%' || :query || '%' THEN 2
                ELSE 3
            END,
            LENGTH(name_normalized) ASC,
            name_normalized ASC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 30): List<IngredientDensityEntity>

    @Query(
        """
        SELECT * FROM ingredient_density
        WHERE name_normalized LIKE '%' || :query || '%'
           OR aliases LIKE '%' || :query || '%'
        ORDER BY
            CASE
                WHEN name_normalized = :query THEN 0
                WHEN name_normalized LIKE :query || '%' THEN 1
                ELSE 2
            END,
            LENGTH(name_normalized) ASC
        LIMIT :limit
        """,
    )
    fun observeSearch(query: String, limit: Int = 30): Flow<List<IngredientDensityEntity>>

    @Query("SELECT DISTINCT category FROM ingredient_density ORDER BY category ASC")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM ingredient_density")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<IngredientDensityEntity>)

    @Upsert
    suspend fun upsert(entry: IngredientDensityEntity): Long

    @Update
    suspend fun update(entry: IngredientDensityEntity)

    /** Usuwamy tylko wpisy własne — wbudowane zostają. */
    @Query("DELETE FROM ingredient_density WHERE id = :id AND is_built_in = 0")
    suspend fun deleteCustom(id: Long)
}
