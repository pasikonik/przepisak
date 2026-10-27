package pl.przepisak.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import pl.przepisak.data.local.entity.CategoryEntity
import pl.przepisak.data.local.entity.IngredientEntity
import pl.przepisak.data.local.entity.IngredientGroupEntity
import pl.przepisak.data.local.entity.PhotoColumns
import pl.przepisak.data.local.entity.RecipeEntity
import pl.przepisak.data.local.entity.RecipeTagCrossRef
import pl.przepisak.data.local.entity.StepEntity
import pl.przepisak.data.local.entity.TagEntity
import pl.przepisak.domain.model.Difficulty

/** Grupa składników razem z jej pozycjami. */
data class IngredientGroupWithItems(
    @Embedded val group: IngredientGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "group_id")
    val ingredients: List<IngredientEntity>,
)

/**
 * Pełny przepis ze wszystkim, co do niego należy.
 *
 * Kategoria jest listą, choć zawsze ma zero lub jeden element — `@Relation`
 * w Roomie zwraca kolekcję, a repozytorium bierze z niej `firstOrNull()`.
 */
data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,

    @Relation(entity = IngredientGroupEntity::class, parentColumn = "id", entityColumn = "recipe_id")
    val groups: List<IngredientGroupWithItems>,

    @Relation(parentColumn = "id", entityColumn = "recipe_id")
    val steps: List<StepEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeTagCrossRef::class,
            parentColumn = "recipe_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,

    @Relation(parentColumn = "category_id", entityColumn = "id")
    val categories: List<CategoryEntity>,
)

/**
 * Projekcja pod listę przepisów (RF-03).
 *
 * Tagi przychodzą jako pojedynczy napis sklejony przez `GROUP_CONCAT` — jedno
 * zapytanie zamiast dodatkowego zapytania na każdy wiersz.
 */
data class RecipeListItemView(
    val id: Long,
    val title: String,
    val description: String?,
    @Embedded(prefix = "photo_") val photo: PhotoColumns,
    val difficulty: Difficulty?,
    val servings: Int?,
    @androidx.room.ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    val rating: Int?,
    @androidx.room.ColumnInfo(name = "effective_time") val effectiveTimeMinutes: Int?,
    @androidx.room.ColumnInfo(name = "category_name") val categoryName: String?,
    /** Nazwy tagów rozdzielone znakiem `|`; `null`, gdy przepis nie ma tagów. */
    @androidx.room.ColumnInfo(name = "tag_names") val tagNames: String?,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long,
)
