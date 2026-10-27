package pl.przepisak.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.przepisak.domain.model.BakingPan
import pl.przepisak.domain.model.Category
import pl.przepisak.domain.model.Difficulty
import pl.przepisak.domain.model.Ingredient
import pl.przepisak.domain.model.IngredientGroup
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.PanShape
import pl.przepisak.domain.model.PhotoRef
import pl.przepisak.domain.model.Quantity
import pl.przepisak.domain.model.Recipe
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.model.Step
import pl.przepisak.domain.model.Tag

/**
 * Reprezentacja przepisu w chmurze (RF-15).
 *
 * Cały przepis jedzie jako jeden dokument w kolumnie `jsonb`, a nie jako sześć
 * powiązanych tabel. Dla kopii zapasowej jednego użytkownika to lepszy wybór:
 * rozstrzyganie konfliktu na poziomie całego przepisu jest przewidywalne i nie
 * potrafi zgubić fragmentu (np. samej listy kroków), a schemat w Supabase sprowadza
 * się do jednej tabeli.
 *
 * Kategoria i tagi podróżują **po nazwie**, nie po identyfikatorze: numery wierszy
 * w lokalnej bazie różnią się między urządzeniami, więc id nie ma tam żadnego sensu.
 */
@Serializable
data class PhotoPayload(
    @SerialName("local_file") val localFile: String? = null,
    @SerialName("remote_path") val remotePath: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
data class PanPayload(
    @SerialName("shape") val shape: String? = null,
    @SerialName("diameter_cm") val diameterCm: Double? = null,
    @SerialName("length_cm") val lengthCm: Double? = null,
    @SerialName("width_cm") val widthCm: Double? = null,
    @SerialName("inner_diameter_cm") val innerDiameterCm: Double? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("label") val label: String? = null,
)

@Serializable
data class IngredientPayload(
    @SerialName("name") val name: String,
    @SerialName("amount") val amount: Double? = null,
    @SerialName("amount_max") val amountMax: Double? = null,
    @SerialName("unit") val unit: String? = null,
    @SerialName("unit_label") val unitLabel: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("is_optional") val isOptional: Boolean = false,
    @SerialName("is_scalable") val isScalable: Boolean = true,
    @SerialName("raw_text") val rawText: String = "",
)

@Serializable
data class IngredientGroupPayload(
    @SerialName("name") val name: String? = null,
    @SerialName("items") val items: List<IngredientPayload> = emptyList(),
)

@Serializable
data class StepPayload(
    @SerialName("text") val text: String,
    @SerialName("photo") val photo: PhotoPayload? = null,
    @SerialName("timer_seconds") val timerSeconds: Int? = null,
)

@Serializable
data class RecipePayload(
    @SerialName("remote_id") val remoteId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("prep_time_minutes") val prepTimeMinutes: Int? = null,
    @SerialName("cook_time_minutes") val cookTimeMinutes: Int? = null,
    @SerialName("total_time_minutes") val totalTimeMinutes: Int? = null,
    @SerialName("difficulty") val difficulty: String? = null,
    @SerialName("servings") val servings: Int? = null,
    @SerialName("servings_label") val servingsLabel: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("photo") val photo: PhotoPayload? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("rating") val rating: Int? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("pan") val pan: PanPayload? = null,
    @SerialName("ingredient_groups") val ingredientGroups: List<IngredientGroupPayload> = emptyList(),
    @SerialName("steps") val steps: List<StepPayload> = emptyList(),
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

/**
 * Wiersz tabeli `recipes`.
 *
 * `updated_at` trzymamy jako liczbę milisekund (kolumna `bigint`), a nie jako
 * `timestamptz`. Porównania „co jest nowsze” robimy wtedy na liczbach i nie mamy
 * do czynienia ze strefami czasowymi ani precyzją formatów daty.
 */
@Serializable
data class RemoteRecipeRow(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("payload") val payload: RecipePayload,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted") val deleted: Boolean = false,
)

// ---------------------------------------------------------------------------
// Mapowanie
// ---------------------------------------------------------------------------

fun RecipeAggregate.toPayload(): RecipePayload = RecipePayload(
    remoteId = recipe.remoteId,
    title = recipe.title,
    description = recipe.description,
    prepTimeMinutes = recipe.prepTimeMinutes,
    cookTimeMinutes = recipe.cookTimeMinutes,
    totalTimeMinutes = recipe.totalTimeMinutesOverride,
    difficulty = recipe.difficulty?.name,
    servings = recipe.servings,
    servingsLabel = recipe.servingsLabel,
    category = category?.name,
    tags = tags.map { it.name },
    photo = recipe.photo.toPayload(),
    sourceUrl = recipe.sourceUrl,
    sourceName = recipe.sourceName,
    isFavorite = recipe.isFavorite,
    rating = recipe.rating,
    notes = recipe.notes,
    pan = recipe.pan?.let { pan ->
        PanPayload(
            shape = pan.shape.name,
            diameterCm = pan.diameterCm,
            lengthCm = pan.lengthCm,
            widthCm = pan.widthCm,
            innerDiameterCm = pan.innerDiameterCm,
            heightCm = pan.heightCm,
            label = pan.label,
        )
    },
    ingredientGroups = groups.map { group ->
        IngredientGroupPayload(
            name = group.name,
            items = group.ingredients.map { it.toPayload() },
        )
    },
    steps = steps.map { step ->
        StepPayload(
            text = step.text,
            photo = step.photo.toPayload(),
            timerSeconds = step.timerSeconds,
        )
    },
    createdAt = recipe.createdAtEpochMs,
    updatedAt = recipe.updatedAtEpochMs,
)

/**
 * Odtwarza przepis z dokumentu.
 *
 * [existingLocalId] pozwala nadpisać istniejący wiersz w lokalnej bazie; `0`
 * oznacza nowy przepis.
 */
fun RecipePayload.toAggregate(existingLocalId: Long = 0L): RecipeAggregate = RecipeAggregate(
    recipe = Recipe(
        id = existingLocalId,
        remoteId = remoteId,
        title = title,
        description = description,
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = cookTimeMinutes,
        totalTimeMinutesOverride = totalTimeMinutes,
        difficulty = difficulty?.toDifficulty(),
        servings = servings,
        servingsLabel = servingsLabel,
        photo = photo.toDomain(),
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        isFavorite = isFavorite,
        rating = rating,
        notes = notes,
        pan = pan?.toDomain(),
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
    ),
    groups = ingredientGroups
        .mapIndexed { index, group ->
            IngredientGroup(
                name = group.name,
                position = index,
                ingredients = group.items.mapIndexed { itemIndex, item -> item.toDomain(itemIndex) },
            )
        }
        .ifEmpty { listOf(IngredientGroup()) },
    steps = steps.mapIndexed { index, step ->
        Step(
            position = index,
            text = step.text,
            photo = step.photo.toDomain(),
            timerSeconds = step.timerSeconds,
        )
    },
    tags = tags.filter { it.isNotBlank() }.map { Tag(name = it) },
    category = category?.takeIf { it.isNotBlank() }?.let { Category(name = it) },
)

private fun PhotoRef.toPayload(): PhotoPayload? =
    if (isEmpty) null else PhotoPayload(localFileName, remotePath, sourceUrl)

private fun PhotoPayload?.toDomain(): PhotoRef = this
    ?.let { PhotoRef(localFileName = it.localFile, remotePath = it.remotePath, sourceUrl = it.sourceUrl) }
    ?: PhotoRef.EMPTY

private fun Ingredient.toPayload(): IngredientPayload = IngredientPayload(
    name = name,
    amount = quantity?.amount,
    amountMax = quantity?.amountMax,
    unit = unit?.name,
    unitLabel = unitLabel,
    note = note,
    isOptional = isOptional,
    isScalable = isScalable,
    rawText = rawText,
)

private fun IngredientPayload.toDomain(position: Int): Ingredient = Ingredient(
    position = position,
    name = name,
    quantity = amount?.let { Quantity(it, amountMax) },
    unit = unit?.let { name -> MeasureUnit.entries.firstOrNull { it.name == name } },
    unitLabel = unitLabel,
    note = note,
    isOptional = isOptional,
    isScalable = isScalable,
    rawText = rawText,
)

private fun String.toDifficulty(): Difficulty? = Difficulty.entries.firstOrNull { it.name == this }

private fun PanPayload.toDomain(): BakingPan? {
    val parsedShape = shape?.let { name -> PanShape.entries.firstOrNull { it.name == name } } ?: return null
    return BakingPan(
        shape = parsedShape,
        diameterCm = diameterCm,
        lengthCm = lengthCm,
        widthCm = widthCm,
        innerDiameterCm = innerDiameterCm,
        heightCm = heightCm,
        label = label,
    )
}
