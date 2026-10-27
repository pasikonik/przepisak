package pl.przepisak.data.local

import android.content.ContentValues
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import pl.przepisak.data.local.dao.CategoryDao
import pl.przepisak.data.local.dao.DensityDao
import pl.przepisak.data.local.dao.RecipeDao
import pl.przepisak.data.local.dao.TagDao
import pl.przepisak.data.local.dao.TimerDao
import pl.przepisak.data.local.entity.CategoryEntity
import pl.przepisak.data.local.entity.IngredientDensityEntity
import pl.przepisak.data.local.entity.IngredientEntity
import pl.przepisak.data.local.entity.IngredientGroupEntity
import pl.przepisak.data.local.entity.RecipeEntity
import pl.przepisak.data.local.entity.RecipeFtsEntity
import pl.przepisak.data.local.entity.RecipeTagCrossRef
import pl.przepisak.data.local.entity.StepEntity
import pl.przepisak.data.local.entity.TagEntity
import pl.przepisak.data.local.entity.TimerEntity
import pl.przepisak.data.local.seed.DensitySeed
import pl.przepisak.data.local.seed.TaxonomySeed

/**
 * Lokalna baza danych — podstawowy magazyn aplikacji (architektura offline-first).
 *
 * Chmura jest wyłącznie kopią zapasową; tutaj jest źródło prawdy.
 */
@Database(
    entities = [
        RecipeEntity::class,
        IngredientGroupEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        RecipeTagCrossRef::class,
        RecipeFtsEntity::class,
        IngredientDensityEntity::class,
        TimerEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PrzepisakDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun densityDao(): DensityDao
    abstract fun timerDao(): TimerDao

    companion object {
        const val NAME = "przepisak.db"

        /**
         * Wypełnia bazę danymi startowymi przy pierwszym uruchomieniu.
         *
         * Piszemy przez [SupportSQLiteDatabase], a nie przez DAO, bo `onCreate`
         * wykonuje się w trakcie budowania bazy — instancja [PrzepisakDatabase]
         * jeszcze nie istnieje. Dzięki temu dane startowe są na miejscu, zanim
         * cokolwiek zdąży odczytać bazę.
         */
        val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedCategories(db)
                seedTags(db)
                seedDensities(db)
            }
        }

        private fun seedCategories(db: SupportSQLiteDatabase) {
            TaxonomySeed.categories().forEach { category ->
                db.insert(
                    "categories",
                    android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                    ContentValues().apply {
                        put("name", category.name)
                        put("name_normalized", category.nameNormalized)
                        put("icon_key", category.iconKey)
                        put("position", category.position)
                    },
                )
            }
        }

        private fun seedTags(db: SupportSQLiteDatabase) {
            TaxonomySeed.tags().forEach { tag ->
                db.insert(
                    "tags",
                    android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                    ContentValues().apply {
                        put("name", tag.name)
                        put("name_normalized", tag.nameNormalized)
                        put("color_argb", tag.colorArgb)
                    },
                )
            }
        }

        private fun seedDensities(db: SupportSQLiteDatabase) {
            DensitySeed.entries().forEach { entry ->
                db.insert(
                    "ingredient_density",
                    android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                    ContentValues().apply {
                        put("name", entry.name)
                        put("name_normalized", entry.nameNormalized)
                        put("aliases", entry.aliases)
                        put("category", entry.category)
                        put("grams_per_ml", entry.gramsPerMl)
                        put("grams_per_cup", entry.gramsPerCup)
                        put("grams_per_tablespoon", entry.gramsPerTablespoon)
                        put("grams_per_teaspoon", entry.gramsPerTeaspoon)
                        put("grams_per_piece", entry.gramsPerPiece)
                        put("is_built_in", if (entry.isBuiltIn) 1 else 0)
                    },
                )
            }
        }
    }
}
