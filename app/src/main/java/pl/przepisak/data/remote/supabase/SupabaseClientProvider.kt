package pl.przepisak.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.przepisak.data.prefs.SecretStore
import pl.przepisak.data.prefs.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dostarcza klienta Supabase zbudowanego na danych wpisanych przez użytkownika.
 *
 * Klient nie może powstać w module Hilta, bo adres i klucz nie są znane w chwili
 * startu aplikacji — użytkownik wpisuje je w Ustawieniach, a kopia zapasowa jest
 * w pełni opcjonalna. Dlatego budujemy go leniwie i buforujemy do czasu zmiany
 * konfiguracji.
 */
@Singleton
class SupabaseClientProvider @Inject constructor(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
) {

    private val mutex = Mutex()
    private var cached: SupabaseClient? = null
    private var cachedFor: Pair<String, String>? = null

    /** `null`, gdy kopia zapasowa nie została skonfigurowana. */
    suspend fun client(): SupabaseClient? = mutex.withLock {
        val url = settings.current().supabaseUrl.trim()
        val anonKey = secrets.supabaseAnonKey?.trim().orEmpty()
        if (url.isBlank() || anonKey.isBlank()) return@withLock null

        val key = url to anonKey
        cached?.takeIf { cachedFor == key }?.let { return@withLock it }

        val client = createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
        cached = client
        cachedFor = key
        client
    }

    /** Wołane po zmianie adresu albo klucza w Ustawieniach. */
    suspend fun invalidate() = mutex.withLock {
        cached = null
        cachedFor = null
    }

    companion object {
        const val TABLE = "recipes"
        const val BUCKET = "recipe-photos"
    }
}
