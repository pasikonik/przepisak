package pl.przepisak.data.local

import pl.przepisak.core.util.TextNormalizer
import pl.przepisak.data.local.entity.CategoryEntity
import pl.przepisak.data.local.entity.IngredientDensityEntity
import pl.przepisak.data.local.entity.IngredientEntity
import pl.przepisak.data.local.entity.IngredientGroupEntity
import pl.przepisak.data.local.entity.PanColumns
import pl.przepisak.data.local.entity.PhotoColumns
import pl.przepisak.data.local.entity.RecipeEntity
import pl.przepisak.data.local.entity.StepEntity
import pl.przepisak.data.local.entity.TagEntity
import pl.przepisak.data.local.entity.TimerEntity
import pl.przepisak.data.local.relation.RecipeListItemView
import pl.przepisak.data.local.relation.RecipeWithDetails
import pl.przepisak.domain.model.BakingPan
import pl.przepisak.domain.model.Category
import pl.przepisak.domain.model.CookingTimer
import pl.przepisak.domain.model.Ingredient
import pl.przepisak.domain.model.IngredientDensity
import pl.przepisak.domain.model.IngredientGroup
import pl.przepisak.domain.model.PanShape
import pl.przepisak.domain.model.PhotoRef
import pl.przepisak.domain.model.Quantity
import pl.przepisak.domain.model.Recipe
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.model.RecipeListItem
import pl.przepisak.domain.model.Step
import pl.przepisak.domain.model.Tag

// ---------------------------------------------------------------------------
// Zdjęcia i formy
// ---------------------------------------------------------------------------

fun PhotoColumns.toDomain() = PhotoRef(
    localFileName = localFileName,
    remotePath = remotePath,
    sourceUrl = sourceUrl,
)

fun PhotoRef.toColumns() = PhotoColumns(
    localFileName = localFileName,
    remotePath = remotePath,
    sourceUrl = sourceUrl,
)

fun PanColumns.toDomain(): BakingPan? {
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

fun BakingPan?.toColumns() = PanColumns(
    shape = this?.shape?.name,
    diameterCm = this?.diameterCm,
    lengthCm = this?.lengthCm,
    widthCm = this?.widthCm,
    innerDiameterCm = this?.innerDiameterCm,
    heightCm = this?.heightCm,
    label = this?.label,
)

// ---------------------------------------------------------------------------
// Taksonomia
// ---------------------------------------------------------------------------

fun CategoryEntity.toDomain() = Category(id = id, name = name, iconKey = iconKey, position = position)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name.trim(),
    nameNormalized = TextNormalizer.normalize(name),
    iconKey = iconKey,
    position = position,
)

fun TagEntity.toDomain() = Tag(id = id, name = name, colorArgb = colorArgb)

fun Tag.toEntity() = TagEntity(
    id = id,
    name = name.trim(),
    nameNormalized = TextNormalizer.normalize(name),
    colorArgb = colorArgb,
)

// ---------------------------------------------------------------------------
// Składniki i kroki
// ---------------------------------------------------------------------------

fun IngredientEntity.toDomain() = Ingredient(
    id = id,
    position = position,
    name = name,
    quantity = amount?.let { Quantity(it, amountMax) },
    unit = unit,
    unitLabel = unitLabel,
    note = note,
    isOptional = isOptional,
    isScalable = isScalable,
    rawText = rawText,
)

fun Ingredient.toEntity(recipeId: Long, groupId: Long) = IngredientEntity(
    id = 0,
    recipeId = recipeId,
    groupId = groupId,
    position = position,
    name = name.trim(),
    nameNormalized = TextNormalizer.normalize(name),
    amount = quantity?.amount,
    amountMax = quantity?.amountMax,
    unit = unit,
    unitLabel = unitLabel,
    note = note,
    isOptional = isOptional,
    isScalable = isScalable,
    rawText = rawText,
)

fun StepEntity.toDomain() = Step(
    id = id,
    position = position,
    text = text,
    photo = photo.toDomain(),
    timerSeconds = timerSeconds,
)

fun Step.toEntity(recipeId: Long) = StepEntity(
    id = 0,
    recipeId = recipeId,
    position = position,
    text = text.trim(),
    photo = photo.toColumns(),
    timerSeconds = timerSeconds,
)

// ---------------------------------------------------------------------------
// Przepis
// ---------------------------------------------------------------------------

fun RecipeEntity.toDomain() = Recipe(
    id = id,
    remoteId = remoteId,
    title = title,
    description = description,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    totalTimeMinutesOverride = totalTimeMinutes,
    difficulty = difficulty,
    servings = servings,
    servingsLabel = servingsLabel,
    categoryId = categoryId,
    photo = photo.toDomain(),
    sourceUrl = sourceUrl,
    sourceName = sourceName,
    isFavorite = isFavorite,
    rating = rating,
    notes = notes,
    pan = pan.toDomain(),
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
)

/**
 * Mapuje przepis na encję. Pola synchronizacji ([RecipeEntity.isDirty],
 * `lastSyncedAt`) ustawia wołający — mapper ich nie dotyka, bo zależą od tego,
 * czy zmiana pochodzi od użytkownika, czy z chmury.
 */
fun Recipe.toEntity(isDirty: Boolean = true, deletedAt: Long? = null, lastSyncedAt: Long? = null) = RecipeEntity(
    id = id,
    remoteId = remoteId,
    title = title.trim(),
    titleNormalized = TextNormalizer.normalize(title),
    description = description?.takeIf { it.isNotBlank() },
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    totalTimeMinutes = totalTimeMinutesOverride,
    difficulty = difficulty,
    servings = servings,
    servingsLabel = servingsLabel?.takeIf { it.isNotBlank() },
    categoryId = categoryId,
    photo = photo.toColumns(),
    sourceUrl = sourceUrl?.takeIf { it.isNotBlank() },
    sourceName = sourceName?.takeIf { it.isNotBlank() },
    isFavorite = isFavorite,
    rating = rating,
    notes = notes?.takeIf { it.isNotBlank() },
    pan = pan.toColumns(),
    createdAt = createdAtEpochMs,
    updatedAt = updatedAtEpochMs,
    isDirty = isDirty,
    deletedAt = deletedAt,
    lastSyncedAt = lastSyncedAt,
)

fun RecipeWithDetails.toDomain(): RecipeAggregate {
    val sortedGroups = groups
        .sortedBy { it.group.position }
        .map { withItems ->
            IngredientGroup(
                id = withItems.group.id,
                name = withItems.group.name,
                position = withItems.group.position,
                ingredients = withItems.ingredients.sortedBy { it.position }.map { it.toDomain() },
            )
        }
    return RecipeAggregate(
        recipe = recipe.toDomain(),
        // Przepis zawsze pokazujemy z co najmniej jedną (domyślną) grupą.
        groups = sortedGroups.ifEmpty { listOf(IngredientGroup()) },
        steps = steps.sortedBy { it.position }.map { it.toDomain() },
        tags = tags.sortedBy { it.nameNormalized }.map { it.toDomain() },
        category = categories.firstOrNull()?.toDomain(),
    )
}

fun RecipeListItemView.toDomain() = RecipeListItem(
    id = id,
    title = title,
    description = description,
    photo = photo.toDomain(),
    totalTimeMinutes = effectiveTimeMinutes,
    difficulty = difficulty,
    servings = servings,
    isFavorite = isFavorite,
    rating = rating,
    categoryName = categoryName,
    tagNames = tagNames?.split('|')?.filter { it.isNotBlank() }?.sorted().orEmpty(),
    updatedAtEpochMs = updatedAt,
)

// ---------------------------------------------------------------------------
// Baza wagowa i minutniki
// ---------------------------------------------------------------------------

fun IngredientDensityEntity.toDomain() = IngredientDensity(
    id = id,
    name = name,
    normalizedName = nameNormalized,
    aliases = aliases.split('|').filter { it.isNotBlank() },
    category = category,
    gramsPerMl = gramsPerMl,
    gramsPerCup = gramsPerCup,
    gramsPerTablespoon = gramsPerTablespoon,
    gramsPerTeaspoon = gramsPerTeaspoon,
    gramsPerPiece = gramsPerPiece,
    isBuiltIn = isBuiltIn,
)

fun IngredientDensity.toEntity() = IngredientDensityEntity(
    id = id,
    name = name.trim(),
    nameNormalized = TextNormalizer.normalize(name),
    aliases = aliases.joinToString("|") { TextNormalizer.normalize(it) },
    category = category,
    gramsPerMl = gramsPerMl,
    gramsPerCup = gramsPerCup,
    gramsPerTablespoon = gramsPerTablespoon,
    gramsPerTeaspoon = gramsPerTeaspoon,
    gramsPerPiece = gramsPerPiece,
    isBuiltIn = isBuiltIn,
)

fun TimerEntity.toDomain() = CookingTimer(
    id = id,
    label = label,
    totalSeconds = totalSeconds,
    remainingSecondsWhenPaused = remainingSeconds,
    endAtEpochMs = endAtEpochMs,
    state = state,
    recipeId = recipeId,
    stepPosition = stepPosition,
    createdAtEpochMs = createdAt,
)

fun CookingTimer.toEntity() = TimerEntity(
    id = id,
    label = label,
    totalSeconds = totalSeconds,
    remainingSeconds = remainingSecondsWhenPaused,
    endAtEpochMs = endAtEpochMs,
    state = state,
    recipeId = recipeId,
    stepPosition = stepPosition,
    createdAt = createdAtEpochMs,
)
