package pl.przepisak.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Wbudowana baza wagowa produktów (RF-10).
 *
 * Wiersze wbudowane ([isBuiltIn] = `true`) wstawia `DensitySeed` przy tworzeniu
 * bazy. Użytkownik może dodawać własne — te mają `isBuiltIn = false` i nie są
 * nadpisywane przy aktualizacjach.
 */
@Entity(
    tableName = "ingredient_density",
    indices = [
        Index(value = ["name_normalized"], unique = true),
        Index(value = ["category"]),
    ],
)
data class IngredientDensityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_normalized") val nameNormalized: String,
    /** Aliasy rozdzielone znakiem `|`. */
    val aliases: String = "",
    val category: String,
    @ColumnInfo(name = "grams_per_ml") val gramsPerMl: Double,
    @ColumnInfo(name = "grams_per_cup") val gramsPerCup: Double? = null,
    @ColumnInfo(name = "grams_per_tablespoon") val gramsPerTablespoon: Double? = null,
    @ColumnInfo(name = "grams_per_teaspoon") val gramsPerTeaspoon: Double? = null,
    @ColumnInfo(name = "grams_per_piece") val gramsPerPiece: Double? = null,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = true,
)
