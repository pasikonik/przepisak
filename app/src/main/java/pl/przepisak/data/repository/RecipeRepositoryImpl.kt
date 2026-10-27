package pl.przepisak.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.TextNormalizer
import pl.przepisak.core.util.TimeProvider
import pl.przepisak.data.local.PrzepisakDatabase
import pl.przepisak.data.local.dao.CategoryDao
import pl.przepisak.data.local.dao.RecipeDao
import pl.przepisak.data.local.dao.TagDao
import pl.przepisak.data.local.entity.IngredientGroupEntity
import pl.przepisak.data.local.entity.RecipeFtsEntity
import pl.przepisak.data.local.entity.RecipeTagCrossRef
import pl.przepisak.data.local.toDomain
import pl.przepisak.data.local.toEntity
import pl.przepisak.domain.model.Category
import pl.przepisak.domain.model.IngredientGroup
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.model.RecipeFilter
import pl.przepisak.domain.model.RecipeListItem
import pl.przepisak.domain.model.RecipeSort
import pl.przepisak.domain.model.Tag
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepositoryImpl @Inject constructor(
    private val db: PrzepisakDatabase,
    private val recipeDao: RecipeDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) : RecipeRepository {

    override fun observeRecipes(filter: RecipeFilter): Flow<List<RecipeListItem>> {
        val ftsQuery = TextNormalizer.toFtsMatchQuery(filter.query)
        return recipeDao.observeFiltered(
            ignoreQuery = if (ftsQuery == null) 1 else 0,
            ftsQuery = ftsQuery ?: "",
            ignoreCategories = if (filter.categoryIds.isEmpty()) 1 else 0,
            // SQLite nie przyjmuje `IN ()`, więc dla nieaktywnego filtru przekazujemy
            // jednoelementową atrapę — warunek i tak jest wtedy pominięty.
            categoryIds = filter.categoryIds.toList().ifEmpty { UNUSED_IDS },
            ignoreDifficulties = if (filter.difficulties.isEmpty()) 1 else 0,
            difficulties = filter.difficulties.map { it.name }.ifEmpty { UNUSED_NAMES },
            maxTotalTime = filter.maxTotalTimeMinutes,
            onlyFavorites = if (filter.onlyFavorites) 1 else 0,
            minRating = filter.minRating,
            requiredTagCount = filter.tagIds.size,
            tagIds = filter.tagIds.toList().ifEmpty { UNUSED_IDS },
            sortIndex = filter.sort.toSortIndex(),
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeRecipe(recipeId: Long): Flow<RecipeAggregate?> =
        recipeDao.observeDetails(recipeId).map { it?.toDomain() }

    override suspend fun getRecipe(recipeId: Long): RecipeAggregate? = withContext(io) {
        recipeDao.getDetails(recipeId)?.toDomain()
    }

    override fun newDraft(): RecipeAggregate =
        RecipeAggregate.empty(remoteId = UUID.randomUUID().toString(), nowMs = time.nowMs())

    override suspend fun saveRecipe(aggregate: RecipeAggregate): Long = withContext(io) {
        db.withTransaction {
            val now = time.nowMs()
            val isNew = aggregate.recipe.id == 0L

            val categoryId = resolveCategoryId(aggregate)
            val header = aggregate.recipe.copy(
                categoryId = categoryId,
                createdAtEpochMs = if (isNew || aggregate.recipe.createdAtEpochMs == 0L) {
                    now
                } else {
                    aggregate.recipe.createdAtEpochMs
                },
                updatedAtEpochMs = now,
            )

            val entity = header.toEntity(isDirty = true)
            val recipeId = if (isNew) {
                recipeDao.insertRecipe(entity)
            } else {
                recipeDao.updateRecipe(entity)
                entity.id
            }

            // Dzieci zapisujemy przez „usuń i wstaw ponownie”. Formularz edycji
            // dostarcza pełną, docelową listę, więc próba dopasowania po id
            // dodałaby złożoności bez zysku — a numeracja pozycji i tak się zmienia.
            recipeDao.deleteIngredientsFor(recipeId)
            recipeDao.deleteGroupsFor(recipeId)
            recipeDao.deleteStepsFor(recipeId)
            recipeDao.deleteTagLinksFor(recipeId)

            val groups = aggregate.groups
                .map { group -> group.copy(ingredients = group.ingredients.filter { it.name.isNotBlank() }) }
                .filterIndexed { index, group -> index == 0 || group.ingredients.isNotEmpty() || !group.isDefault }
                .ifEmpty { listOf(IngredientGroup()) }

            val groupIds = recipeDao.insertGroups(
                groups.mapIndexed { index, group ->
                    IngredientGroupEntity(
                        recipeId = recipeId,
                        name = group.name?.trim()?.takeIf { it.isNotBlank() },
                        position = index,
                    )
                },
            )

            val ingredientEntities = groups.flatMapIndexed { groupIndex, group ->
                group.ingredients.mapIndexed { itemIndex, ingredient ->
                    ingredient.copy(position = itemIndex).toEntity(recipeId, groupIds[groupIndex])
                }
            }
            recipeDao.insertIngredients(ingredientEntities)

            val stepEntities = aggregate.steps
                .filter { it.text.isNotBlank() }
                .mapIndexed { index, step -> step.copy(position = index).toEntity(recipeId) }
            recipeDao.insertSteps(stepEntities)

            val resolvedTags = resolveTags(aggregate.tags)
            recipeDao.insertRecipeTags(resolvedTags.map { RecipeTagCrossRef(recipeId, it.id) })
            tagDao.deleteOrphans()

            recipeDao.upsertFts(
                RecipeFtsEntity(
                    recipeId = recipeId,
                    title = TextNormalizer.normalize(header.title),
                    description = TextNormalizer.normalize(header.description.orEmpty()),
                    // Do indeksu trafiają nazwy składników razem z uwagami —
                    // „mleko bez laktozy” ma się znaleźć po słowie „laktozy”.
                    ingredients = TextNormalizer.normalize(
                        ingredientEntities.joinToString(" ") { "${it.name} ${it.note.orEmpty()}" },
                    ),
                    tags = TextNormalizer.normalize(resolvedTags.joinToString(" ") { it.name }),
                    category = TextNormalizer.normalize(
                        categoryId?.let { id -> categoryDao.getAll().firstOrNull { it.id == id }?.name }.orEmpty(),
                    ),
                    notes = TextNormalizer.normalize(header.notes.orEmpty()),
                ),
            )

            recipeId
        }
    }

    override suspend fun deleteRecipe(recipeId: Long) = withContext(io) {
        db.withTransaction {
            recipeDao.softDelete(recipeId, time.nowMs())
            // Wiersz FTS usuwamy od razu — przepis ma natychmiast zniknąć z wyników.
            recipeDao.deleteFts(recipeId)
        }
    }

    override suspend fun restoreRecipe(recipeId: Long) = withContext(io) {
        val details = recipeDao.getDetails(recipeId) ?: return@withContext
        db.withTransaction {
            recipeDao.restore(recipeId, time.nowMs())
            val aggregate = details.toDomain()
            recipeDao.upsertFts(
                RecipeFtsEntity(
                    recipeId = recipeId,
                    title = TextNormalizer.normalize(aggregate.recipe.title),
                    description = TextNormalizer.normalize(aggregate.recipe.description.orEmpty()),
                    ingredients = TextNormalizer.normalize(
                        aggregate.allIngredients.joinToString(" ") { "${it.name} ${it.note.orEmpty()}" },
                    ),
                    tags = TextNormalizer.normalize(aggregate.tags.joinToString(" ") { it.name }),
                    category = TextNormalizer.normalize(aggregate.category?.name.orEmpty()),
                    notes = TextNormalizer.normalize(aggregate.recipe.notes.orEmpty()),
                ),
            )
        }
    }

    override suspend fun duplicateRecipe(recipeId: Long): Long? = withContext(io) {
        val source = recipeDao.getDetails(recipeId)?.toDomain() ?: return@withContext null
        val now = time.nowMs()
        val copy = source.copy(
            recipe = source.recipe.copy(
                id = 0,
                remoteId = UUID.randomUUID().toString(),
                title = "${source.recipe.title} (kopia)",
                isFavorite = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
            groups = source.groups.map { group ->
                group.copy(id = 0, ingredients = group.ingredients.map { it.copy(id = 0) })
            },
            steps = source.steps.map { it.copy(id = 0) },
        )
        saveRecipe(copy)
    }

    override suspend fun setFavorite(recipeId: Long, favorite: Boolean) = withContext(io) {
        recipeDao.setFavorite(recipeId, favorite, time.nowMs())
    }

    override suspend fun setRating(recipeId: Long, rating: Int?) = withContext(io) {
        recipeDao.setRating(recipeId, rating?.coerceIn(1, 5), time.nowMs())
    }

    override suspend fun setNotes(recipeId: Long, notes: String?) = withContext(io) {
        recipeDao.setNotes(recipeId, notes?.takeIf { it.isNotBlank() }, time.nowMs())
    }

    override fun observeRecipeCount(): Flow<Int> = recipeDao.observeRecipeCount()

    override suspend fun getRandomRecipeId(): Long? = withContext(io) { recipeDao.getRandomRecipeId() }

    // ------------------------------------------------------------------
    // Pomocnicze
    // ------------------------------------------------------------------

    /**
     * Ustala identyfikator kategorii. Kategoria podana z `id == 0` i nazwą jest
     * zakładana (albo znaleziona po znormalizowanej nazwie) — dzięki temu import
     * i formularz mogą podać samą nazwę.
     */
    private suspend fun resolveCategoryId(aggregate: RecipeAggregate): Long? {
        val category: Category? = aggregate.category
        if (category != null && category.name.isNotBlank()) {
            if (category.id != 0L) return category.id
            val normalized = TextNormalizer.normalize(category.name)
            categoryDao.findByNormalizedName(normalized)?.let { return it.id }
            val newId = categoryDao.insert(category.copy(id = 0).toEntity())
            return if (newId > 0) newId else categoryDao.findByNormalizedName(normalized)?.id
        }
        return aggregate.recipe.categoryId
    }

    /** Zamienia tagi (także nowe, z `id == 0`) na istniejące wiersze bazy. */
    private suspend fun resolveTags(tags: List<Tag>): List<Tag> {
        val result = LinkedHashMap<Long, Tag>()
        for (tag in tags) {
            if (tag.name.isBlank()) continue
            if (tag.id != 0L) {
                result[tag.id] = tag
                continue
            }
            val normalized = TextNormalizer.normalize(tag.name)
            val existing = tagDao.findByNormalizedName(normalized)
            if (existing != null) {
                result[existing.id] = existing.toDomain()
                continue
            }
            val newId = tagDao.insert(tag.copy(id = 0).toEntity())
            val resolvedId = if (newId > 0) newId else tagDao.findByNormalizedName(normalized)?.id
            if (resolvedId != null) result[resolvedId] = tag.copy(id = resolvedId)
        }
        return result.values.toList()
    }

    private fun RecipeSort.toSortIndex(): Int = when (this) {
        RecipeSort.RECENT -> 0
        RecipeSort.TITLE_ASC -> 1
        RecipeSort.TIME_ASC -> 2
        RecipeSort.RATING_DESC -> 3
    }

    private companion object {
        /** Atrapy dla nieaktywnych filtrów — patrz komentarz w `observeRecipes`. */
        val UNUSED_IDS = listOf(-1L)
        val UNUSED_NAMES = listOf("")
    }
}
