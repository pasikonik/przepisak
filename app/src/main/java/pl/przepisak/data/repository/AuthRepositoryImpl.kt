package pl.przepisak.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.Outcome
import pl.przepisak.data.prefs.SecretStore
import pl.przepisak.data.prefs.SettingsRepository
import pl.przepisak.data.remote.supabase.SupabaseClientProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logowanie do Supabase — warunek działania kopii zapasowej (RF-15).
 *
 * Stan zalogowania trzymamy we własnym [MutableStateFlow], a nie w strumieniu sesji
 * biblioteki: potrzebujemy tylko adresu e-mail do wyświetlenia, a własne pole jest
 * odporne na zmiany API supabase-kt między wersjami.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AuthRepository {

    private val emailState = MutableStateFlow<String?>(null)

    override val currentUserEmail: Flow<String?> = emailState.asStateFlow()

    override suspend fun signIn(email: String, password: String): Outcome<Unit> = withContext(io) {
        val client = provider.client() ?: return@withContext Outcome.Failure(AppError.SupabaseNotConfigured)
        try {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            onSignedIn(email.trim(), password)
            Outcome.Success(Unit)
        } catch (e: java.io.IOException) {
            Outcome.Failure(AppError.NoNetwork(e))
        } catch (e: Exception) {
            Outcome.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun signUp(email: String, password: String): Outcome<Unit> = withContext(io) {
        val client = provider.client() ?: return@withContext Outcome.Failure(AppError.SupabaseNotConfigured)
        try {
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            // Gdy projekt wymaga potwierdzenia adresu, sesji jeszcze nie ma —
            // zapisujemy dane, żeby po potwierdzeniu logowanie przeszło samo.
            onSignedIn(email.trim(), password)
            Outcome.Success(Unit)
        } catch (e: java.io.IOException) {
            Outcome.Failure(AppError.NoNetwork(e))
        } catch (e: Exception) {
            Outcome.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun signOut() = withContext(io) {
        runCatching { provider.client()?.auth?.signOut() }
        secrets.supabasePassword = null
        settings.setSupabaseEmail("")
        emailState.value = null
    }

    /**
     * Odtwarza sesję przy starcie i przed synchronizacją w tle.
     *
     * Hasło trzymamy w zaszyfrowanych preferencjach właśnie po to: `SyncWorker`
     * budzi się bez udziału użytkownika i musi umieć odnowić sesję sam, bez
     * pokazywania ekranu logowania.
     */
    override suspend fun restoreSession(): Boolean = withContext(io) {
        val client = provider.client() ?: return@withContext false

        val existing = runCatching { client.auth.currentSessionOrNull() }.getOrNull()
        if (existing != null) {
            emailState.value = settings.current().supabaseEmail.takeIf { it.isNotBlank() }
            return@withContext true
        }

        val email = settings.current().supabaseEmail.takeIf { it.isNotBlank() } ?: return@withContext false
        val password = secrets.supabasePassword?.takeIf { it.isNotBlank() } ?: return@withContext false

        signIn(email, password) is Outcome.Success
    }

    private suspend fun onSignedIn(email: String, password: String) {
        secrets.supabasePassword = password
        settings.setSupabaseEmail(email)
        emailState.value = email
    }
}
