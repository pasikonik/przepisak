package pl.przepisak.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.przepisak.domain.model.MeasureUnit

/**
 * Grupa składników („Ciasto”, „Krem”). Każdy przepis ma co najmniej jedną grupę —
 * domyślną, z [name] równym `null`.
 */
@Entity(
    tableName = "ingredient_groups",
    indices = [Index(value = ["recipe_id"])],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class IngredientGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    val name: String? = null,
    val position: Int = 0,
)

/**
 * Składnik. Trzymamy równolegle dane rozłożone (ilość, jednostka, nazwa) i
 * [rawText] — dosłowną linię ze źródła. Dzięki temu skalowanie działa na
 * liczbach, a jednocześnie nic z oryginału nie ginie.
 */
@Entity(
    tableName = "ingredients",
    indices = [Index(value = ["recipe_id"]), Index(value = ["group_id"])],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IngredientGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    @ColumnInfo(name = "group_id") val groupId: Long,
    val position: Int = 0,
    val name: String,
    /** Nazwa bez diakrytyków — pomaga dopasować składnik do bazy wagowej (RF-10). */
    @ColumnInfo(name = "name_normalized") val nameNormalized: String,
    val amount: Double? = null,
    /** Górna granica zakresu („1-2 łyżki”). */
    @ColumnInfo(name = "amount_max") val amountMax: Double? = null,
    val unit: MeasureUnit? = null,
    /** Jednostka nierozpoznana — zapisana dosłownie. */
    @ColumnInfo(name = "unit_label") val unitLabel: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "is_optional") val isOptional: Boolean = false,
    @ColumnInfo(name = "is_scalable") val isScalable: Boolean = true,
    @ColumnInfo(name = "raw_text") val rawText: String = "",
)
