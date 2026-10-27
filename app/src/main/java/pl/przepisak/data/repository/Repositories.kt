package pl.przepisak.data.repository

import kotlinx.coroutines.flow.Flow
import pl.przepisak.core.util.Outcome
import pl.przepisak.domain.model.Category
import pl.przepisak.domain.model.CookingTimer
import pl.przepisak.domain.model.ImportedRecipe
import pl.przepisak.domain.model.IngredientDensity
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.model.Tag

/**
 * Kategorie i tagi (RF-01).
 *
 * Tagi bez przypisanego przepisu są usuwane przy zapisie przepisu — lista filtrów
 * ma pokazywać to, co faktycznie jest w użyciu.
 */
interface TaxonomyRepository {
    fun observeCategories(): Flow<List<Category>>
    fun observeTags(): Flow<List<Tag>>

    /** Liczba przepisów w kategorii — mapa `categoryId` → liczba. */
    fun observeCategoryUsage(): Flow<Map<Long, Int>>

    /** Liczba przepisów z danym tagiem — mapa `tagId` → liczba. */
    fun observeTagUsage(): Flow<Map<Long, Int>>

    /** Zwraca istniejącą kategorię o tej nazwie albo zakłada nową. */
    suspend fun ensureCategory(name: String): Category?

    /** Zwraca istniejący tag o tej nazwie albo zakłada nowy. */
    suspend fun ensureTag(name: String): Tag?

    suspend fun renameCategory(category: Category)
    suspend fun renameTag(tag: Tag)
    suspend fun deleteCategory(categoryId: Long)
    suspend fun deleteTag(tagId: Long)
}

/** Wbudowana baza wagowa (RF-10). */
interface DensityRepository {
    fun observeAll(): Flow<List<IngredientDensity>>
    fun observeCategories(): Flow<List<String>>

    /** Szukanie po nazwie i aliasach; [query] może zawierać polskie znaki. */
    suspend fun search(query: String, limit: Int = 30): List<IngredientDensity>

    /**
     * Próbuje dopasować składnik przepisu do wpisu bazy wagowej.
     * Zwraca `null`, gdy nie ma wystarczająco pewnego trafienia.
     */
    suspend fun matchForIngredient(ingredientName: String): IngredientDensity?

    suspend fun getById(id: Long): IngredientDensity?
    suspend fun save(entry: IngredientDensity): Long
    suspend fun deleteCustom(id: Long)
}

/** Import przepisów ze stron i filmów (RF-04, RF-05, RF-06). */
interface ImportRepository {
    /**
     * Pobiera i rozpoznaje przepis pod wskazanym adresem.
     *
     * Kolejność prób: dane strukturalne Schema.org → mikrodane → Gemini.
     * Dla filmów: napisy → opis, w obu przypadkach przez Gemini.
     */
    suspend fun importFromUrl(url: String): Outcome<ImportedRecipe>

    /**
     * Rozpoznaje tekst wklejony przez użytkownika. Sam link (także z krótkim podpisem)
     * importuje jak [importFromUrl]; dłuższy tekst — np. przepis z czatu — jako przepis,
     * nawet jeśli zawiera link do źródła.
     */
    suspend fun importFromText(text: String): Outcome<ImportedRecipe>

    /**
     * Zamienia wynik importu na przepis gotowy do zapisu: rozkłada linie
     * składników na ilość/jednostkę/nazwę i pobiera zdjęcie, jeśli jest.
     */
    suspend fun toAggregate(imported: ImportedRecipe, downloadPhoto: Boolean = true): RecipeAggregate

    /** Wyciąga pierwszy adres URL z tekstu udostępnionego przez Share Sheet (RF-04). */
    fun extractUrl(sharedText: String): String?
}

/** Stan procesu synchronizacji pokazywany w Ustawieniach (RF-15). */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Success(val pushed: Int, val pulled: Int, val atEpochMs: Long) : SyncStatus
    data class Failed(val error: pl.przepisak.core.util.AppError) : SyncStatus
}

/** Kopia zapasowa i synchronizacja z Supabase (RF-15). */
interface SyncRepository {
    val status: Flow<SyncStatus>

    /** Liczba przepisów czekających na wysłanie. */
    fun observePendingCount(): Flow<Int>

    /** Pełny cykl: wyślij zmiany lokalne, pobierz zdalne. */
    suspend fun syncNow(): Outcome<SyncStatus.Success>

    /** Wysyła lokalne zmiany i zdjęcia bez pobierania. */
    suspend fun pushOnly(): Outcome<Int>
}

/** Logowanie do Supabase — warunek działania kopii zapasowej (RF-15). */
interface AuthRepository {
    /** `null`, gdy nikt nie jest zalogowany. */
    val currentUserEmail: Flow<String?>

    suspend fun signIn(email: String, password: String): Outcome<Unit>
    suspend fun signUp(email: String, password: String): Outcome<Unit>
    suspend fun signOut()

    /** Odtwarza sesję po restarcie aplikacji; `false`, gdy się nie udało. */
    suspend fun restoreSession(): Boolean
}

/** Minutniki kuchenne (RF-13). */
interface TimerRepository {
    fun observeActive(): Flow<List<CookingTimer>>

    suspend fun start(label: String, seconds: Int, recipeId: Long? = null, stepPosition: Int? = null): Long
    suspend fun pause(timerId: Long)
    suspend fun resume(timerId: Long)
    suspend fun addSeconds(timerId: Long, seconds: Int)
    suspend fun cancel(timerId: Long)
    suspend fun cancelAll()

    /** Oznacza minutnik jako zakończony — wołane przez alarm systemowy. */
    suspend fun markFinished(timerId: Long)

    /** Po restarcie urządzenia ustawia alarmy dla minutników, które nadal biegną. */
    suspend fun rescheduleAfterBoot()
}
