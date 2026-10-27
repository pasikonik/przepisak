package pl.przepisak.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.TextNormalizer
import pl.przepisak.data.local.dao.DensityDao
import pl.przepisak.data.local.entity.IngredientDensityEntity
import pl.przepisak.data.local.toDomain
import pl.przepisak.data.local.toEntity
import pl.przepisak.domain.model.IngredientDensity
import javax.inject.Inject
import javax.inject.Singleton

/** Wbudowana baza wagowa produktów (RF-10). */
@Singleton
class DensityRepositoryImpl @Inject constructor(
    private val densityDao: DensityDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DensityRepository {

    override fun observeAll(): Flow<List<IngredientDensity>> =
        densityDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeCategories(): Flow<List<String>> = densityDao.observeCategories()

    override suspend fun search(query: String, limit: Int): List<IngredientDensity> = withContext(io) {
        val normalized = TextNormalizer.normalize(query)
        if (normalized.isBlank()) return@withContext emptyList()
        densityDao.search(normalized, limit).map { it.toDomain() }
    }

    /**
     * Dopasowuje nazwę składnika z przepisu do wpisu bazy wagowej.
     *
     * Nazwy w przepisach rzadko odpowiadają wpisom bazy dosłownie — bywają
     * odmienione przez przypadek („mąki pszennej” zamiast „mąka pszenna”) albo
     * mają doklejone dopiski w nawiasach („masło (miękkie)”). Dlatego próbujemy
     * kolejno coraz luźniejszych dopasowań i przerywamy na pierwszym, które
     * uznajemy za wystarczająco pewne:
     *
     * 1. trafienie dokładne znormalizowanej nazwy (lub aliasu),
     * 2. zawieranie się jednej znormalizowanej frazy w drugiej — w dowolną
     *    stronę, bo raz to nazwa składnika jest dłuższa („cebula czerwona
     *    drobno posiekana”), a raz to wpis bazy („czosnek (ząbek, posiekany)”),
     * 3. dopasowanie słowo po słowie — od najdłuższego słowa nazwy składnika,
     *    pomijając słowa krótsze niż [MIN_WORD_LENGTH] znaki (przyimki, spójniki)
     *    — wśród wszystkich wpisów bazy wybieramy ten o najdłuższym wspólnym
     *    prefiksie ze słowem; polska odmiana zmienia głównie końcówki, więc
     *    prefiks stemowy jest tu bardziej wiarygodny niż dopasowanie 1:1.
     *
     * Wpisów bazy jest niewiele (kilkaset), więc czytamy je jednorazowo w
     * całości zamiast wielu zapytań LIKE — prościej i równie szybko.
     */
    override suspend fun matchForIngredient(ingredientName: String): IngredientDensity? = withContext(io) {
        val normalized = TextNormalizer.normalize(ingredientName)
        // Krótszy tekst (np. sam przyimek po odcięciu reszty) jest zbyt niepewny,
        // by cokolwiek z nim wiązać — patrz próg [MIN_WORD_LENGTH] niżej.
        if (normalized.length < MIN_WORD_LENGTH) return@withContext null

        val all = densityDao.observeAll().first()
        if (all.isEmpty()) return@withContext null

        all.firstOrNull { entity -> entity.nameNormalized == normalized || entity.aliasList.contains(normalized) }
            ?.let { return@withContext it.toDomain() }

        all.filter { entity -> phraseMatches(entity, normalized) }
            .minByOrNull { entity -> kotlin.math.abs(entity.nameNormalized.length - normalized.length) }
            ?.let { return@withContext it.toDomain() }

        val words = normalized.split(' ')
            .filter { it.length >= MIN_WORD_LENGTH }
            .sortedByDescending { it.length }

        for (word in words) {
            val best = all
                .map { entity -> entity to commonPrefixLength(entity.nameNormalized, word) }
                .filter { (_, prefixLength) -> prefixLength >= MIN_WORD_LENGTH }
                // Przy takim samym prefiksie wygrywa krótsza (bardziej dosłowna) nazwa.
                .maxWithOrNull(compareBy({ it.second }, { -it.first.nameNormalized.length }))
            if (best != null) return@withContext best.first.toDomain()
        }

        null
    }

    override suspend fun getById(id: Long): IngredientDensity? = withContext(io) {
        densityDao.getById(id)?.toDomain()
    }

    override suspend fun save(entry: IngredientDensity): Long = withContext(io) {
        // Wpisy wbudowane wstawia wyłącznie DensitySeed — wszystko, co zapisuje
        // repozytorium w trakcie działania aplikacji, jest wpisem użytkownika.
        // `name_normalized` ma unikalny indeks: dla nowego wpisu (id == 0) sprawdzamy
        // najpierw, czy taka nazwa już istnieje, i wtedy nadpisujemy ten wiersz —
        // zamiast liczyć na to, jak @Upsert obsłuży konflikt na indeksie innym niż klucz główny.
        val normalized = TextNormalizer.normalize(entry.name)
        val existingId = if (entry.id == 0L) densityDao.findExact(normalized)?.id else null
        val resolvedEntry = entry.copy(id = existingId ?: entry.id, isBuiltIn = false)
        densityDao.upsert(resolvedEntry.toEntity())
    }

    override suspend fun deleteCustom(id: Long) = withContext(io) {
        densityDao.deleteCustom(id)
    }

    private fun phraseMatches(entity: IngredientDensityEntity, normalized: String): Boolean {
        if (entity.nameNormalized.contains(normalized) || normalized.contains(entity.nameNormalized)) return true
        return entity.aliasList.any { alias -> alias == normalized || normalized.contains(alias) }
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }

    private val IngredientDensityEntity.aliasList: List<String>
        get() = aliases.split('|').filter { it.isNotEmpty() }

    private companion object {
        /** Słowa krótsze są przyimkami/spójnikami — nie dają się z niczym pewnie skojarzyć. */
        const val MIN_WORD_LENGTH = 3
    }
}
