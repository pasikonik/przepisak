package pl.przepisak.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.TextNormalizer
import pl.przepisak.data.local.dao.CategoryDao
import pl.przepisak.data.local.dao.TagDao
import pl.przepisak.data.local.toDomain
import pl.przepisak.data.local.toEntity
import pl.przepisak.domain.model.Category
import pl.przepisak.domain.model.Tag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kategorie i tagi przepisów (RF-01) — proste słowniki wspierane tabelami
 * `categories` i `tags`.
 */
@Singleton
class TaxonomyRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TaxonomyRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeCategoryUsage(): Flow<Map<Long, Int>> =
        categoryDao.observeUsageCounts().map { rows -> rows.associate { it.categoryId to it.recipeCount } }

    override fun observeTagUsage(): Flow<Map<Long, Int>> =
        tagDao.observeUsageCounts().map { rows -> rows.associate { it.tagId to it.recipeCount } }

    override suspend fun ensureCategory(name: String): Category? = withContext(io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        val normalized = TextNormalizer.normalize(trimmed)
        categoryDao.findByNormalizedName(normalized)?.let { return@withContext it.toDomain() }

        val candidate = Category(name = trimmed)
        val insertedId = categoryDao.insert(candidate.toEntity())
        // OnConflictStrategy.IGNORE zwraca -1, gdy wiersz już istnieje (np. wyścig
        // dwóch zapisów) — wtedy po prostu dociągamy ten istniejący.
        val resolvedId = insertedId.takeIf { it > 0 } ?: categoryDao.findByNormalizedName(normalized)?.id
        resolvedId?.let { candidate.copy(id = it) }
    }

    override suspend fun ensureTag(name: String): Tag? = withContext(io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        val normalized = TextNormalizer.normalize(trimmed)
        tagDao.findByNormalizedName(normalized)?.let { return@withContext it.toDomain() }

        val candidate = Tag(name = trimmed)
        val insertedId = tagDao.insert(candidate.toEntity())
        val resolvedId = insertedId.takeIf { it > 0 } ?: tagDao.findByNormalizedName(normalized)?.id
        resolvedId?.let { candidate.copy(id = it) }
    }

    override suspend fun renameCategory(category: Category) = withContext(io) {
        categoryDao.update(category.toEntity())
    }

    override suspend fun renameTag(tag: Tag) = withContext(io) {
        tagDao.update(tag.toEntity())
    }

    override suspend fun deleteCategory(categoryId: Long) = withContext(io) {
        categoryDao.delete(categoryId)
    }

    override suspend fun deleteTag(tagId: Long) = withContext(io) {
        tagDao.delete(tagId)
    }
}
