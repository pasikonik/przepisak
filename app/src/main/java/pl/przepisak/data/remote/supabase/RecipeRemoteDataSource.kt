package pl.przepisak.data.remote.supabase

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.Outcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Warstwa dostępu do tabeli `recipes` i kubełka ze zdjęciami w Supabase (RF-15).
 *
 * Każda operacja zwraca [Outcome] — brak konfiguracji nie jest tu wyjątkiem,
 * tylko normalnym stanem aplikacji, w którym kopia zapasowa po prostu nie działa.
 */
@Singleton
class RecipeRemoteDataSource @Inject constructor(
    private val provider: SupabaseClientProvider,
) {

    suspend fun upsert(rows: List<RemoteRecipeRow>): Outcome<Unit> = guard {
        val client = provider.client() ?: return Outcome.Failure(AppError.SupabaseNotConfigured)
        if (rows.isEmpty()) return Outcome.Success(Unit)
        client.from(SupabaseClientProvider.TABLE).upsert(rows) { onConflict = "id" }
        Outcome.Success(Unit)
    }

    suspend fun fetchChangedSince(sinceEpochMs: Long): Outcome<List<RemoteRecipeRow>> = guard {
        val client = provider.client() ?: return Outcome.Failure(AppError.SupabaseNotConfigured)
        val rows = client.from(SupabaseClientProvider.TABLE)
            .select {
                filter { gt("updated_at", sinceEpochMs) }
            }
            .decodeList<RemoteRecipeRow>()
        Outcome.Success(rows)
    }

    suspend fun uploadPhoto(path: String, bytes: ByteArray): Outcome<String> = guard {
        val client = provider.client() ?: return Outcome.Failure(AppError.SupabaseNotConfigured)
        client.storage.from(SupabaseClientProvider.BUCKET).upload(path, bytes) { upsert = true }
        Outcome.Success(path)
    }

    suspend fun downloadPhoto(path: String): Outcome<ByteArray> = guard {
        val client = provider.client() ?: return Outcome.Failure(AppError.SupabaseNotConfigured)
        val bytes = client.storage.from(SupabaseClientProvider.BUCKET).downloadAuthenticated(path)
        Outcome.Success(bytes)
    }

    /**
     * Wyjątki biblioteki Supabase tłumaczymy na błędy domenowe.
     *
     * Rozpoznajemy je po treści komunikatu, a nie po typie: hierarchia wyjątków
     * supabase-kt zmienia się między wersjami, a nam wystarczy rozróżnienie
     * „brak sieci” od „błąd po stronie serwera”.
     *
     * WAŻNE: nie logować tu `e` ani `e.message` nawet tymczasowo — wyjątki
     * supabase-kt (np. [io.github.jan.supabase.postgrest.exception.PostgrestRestException])
     * osadzają w treści komunikatu pełne nagłówki żądania, łącznie z tokenem
     * sesji i kluczem `apikey`. Zwykłe `Log.e(tag, e.message, e)` wystarczy,
     * żeby wypisać je jawnie do logcata.
     */
    private suspend inline fun <T> guard(block: () -> Outcome<T>): Outcome<T> =
        try {
            block()
        } catch (e: java.io.IOException) {
            Outcome.Failure(AppError.NoNetwork(e))
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            when {
                message.contains("401") || message.contains("JWT", ignoreCase = true) ->
                    Outcome.Failure(AppError.NotAuthenticated)
                // Pierwsza synchronizacja bez wcześniejszego uruchomienia
                // supabase/schema.sql: PostgREST zgłasza brak tabeli w cache
                // schematu, Storage zgłasza brak kubełka. Bez tego rozróżnienia
                // użytkownik dostawał tylko „nieoczekiwany błąd” bez wskazówki.
                message.contains("schema cache", ignoreCase = true) ||
                    message.contains("Bucket not found", ignoreCase = true) ->
                    Outcome.Failure(AppError.SupabaseSchemaMissing)
                else -> Outcome.Failure(AppError.Unknown(e))
            }
        }
}
