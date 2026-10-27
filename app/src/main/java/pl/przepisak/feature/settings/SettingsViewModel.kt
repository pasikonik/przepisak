package pl.przepisak.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.Outcome
import pl.przepisak.data.prefs.AppSettings
import pl.przepisak.data.prefs.GeminiModel
import pl.przepisak.data.prefs.SecretStore
import pl.przepisak.data.prefs.SettingsRepository
import pl.przepisak.data.prefs.ThemeMode
import pl.przepisak.data.remote.cookmate.CookmateImporter
import pl.przepisak.data.remote.gemini.GeminiApi
import pl.przepisak.data.remote.supabase.SupabaseClientProvider
import pl.przepisak.data.repository.AuthRepository
import pl.przepisak.data.repository.RecipeRepository
import pl.przepisak.data.repository.SyncRepository
import pl.przepisak.data.repository.SyncStatus
import pl.przepisak.notification.TimerAlarmScheduler
import pl.przepisak.sync.SyncScheduler
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val geminiKey: String = "",
    val supabaseAnonKey: String = "",
    val password: String = "",
    val signedInEmail: String? = null,
    val pendingChanges: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val recipeCount: Int = 0,
    val canScheduleExactAlarms: Boolean = true,
    /** Komunikat do pokazania w pasku Snackbar; czyszczony po wyświetleniu. */
    val message: String? = null,
    /** Błąd do pokazania w pasku Snackbar — ekran sam dobiera dla niego tekst przez [pl.przepisak.core.ui.component.appErrorMessage]. */
    val error: AppError? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secrets: SecretStore,
    private val geminiApi: GeminiApi,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val syncScheduler: SyncScheduler,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val alarmScheduler: TimerAlarmScheduler,
    private val cookmateImporter: CookmateImporter,
    recipeRepository: RecipeRepository,
) : ViewModel() {

    private val geminiKey = MutableStateFlow(secrets.geminiApiKey.orEmpty())
    private val anonKey = MutableStateFlow(secrets.supabaseAnonKey.orEmpty())
    private val password = MutableStateFlow(secrets.supabasePassword.orEmpty())
    private val message = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<AppError?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        combine(geminiKey, anonKey, password) { gemini, anon, pass -> Triple(gemini, anon, pass) },
        combine(
            authRepository.currentUserEmail,
            syncRepository.observePendingCount(),
            syncRepository.status,
        ) { email, pending, status -> Triple(email, pending, status) },
        recipeRepository.observeRecipeCount(),
        combine(message, error) { currentMessage, currentError -> currentMessage to currentError },
    ) { settings, secretsTriple, syncTriple, recipeCount, notice ->
        SettingsUiState(
            settings = settings,
            geminiKey = secretsTriple.first,
            supabaseAnonKey = secretsTriple.second,
            password = secretsTriple.third,
            signedInEmail = syncTriple.first,
            pendingChanges = syncTriple.second,
            syncStatus = syncTriple.third,
            recipeCount = recipeCount,
            canScheduleExactAlarms = alarmScheduler.canScheduleExact(),
            message = notice.first,
            error = notice.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        // Odtworzenie sesji przy wejściu w Ustawienia, żeby od razu widzieć,
        // czy użytkownik jest zalogowany.
        viewModelScope.launch { authRepository.restoreSession() }
    }

    // --- wygląd -----------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { settingsRepository.setUseDynamicColor(enabled) }
    fun setKitchenFontScale(scale: Float) = launch { settingsRepository.setKitchenFontScale(scale) }
    fun setKeepScreenOn(enabled: Boolean) = launch { settingsRepository.setKeepScreenOnInKitchenMode(enabled) }

    // --- Gemini -----------------------------------------------------------

    fun setGeminiKey(key: String) {
        geminiKey.value = key
        secrets.geminiApiKey = key
    }

    fun setGeminiModel(model: GeminiModel) = launch { settingsRepository.setGeminiModel(model) }

    fun testGemini(okTemplate: String) {
        viewModelScope.launch {
            when (val result = geminiApi.testConnection()) {
                is Outcome.Success -> {
                    message.value = okTemplate.format(result.value)
                    error.value = null
                }
                // Klucza ani jego fragmentu nigdy nie pokazujemy w komunikacie —
                // ale konkretny powód (zły klucz, brak sieci, limit) owszem.
                is Outcome.Failure -> {
                    error.value = result.error
                    message.value = null
                }
            }
        }
    }

    // --- kopia zapasowa ---------------------------------------------------

    fun setSupabaseUrl(url: String) = launch {
        settingsRepository.setSupabaseUrl(url)
        supabaseClientProvider.invalidate()
    }

    fun setSupabaseAnonKey(key: String) {
        anonKey.value = key
        secrets.supabaseAnonKey = key
        viewModelScope.launch { supabaseClientProvider.invalidate() }
    }

    fun setPassword(value: String) {
        password.value = value
    }

    fun setEmail(email: String) = launch { settingsRepository.setSupabaseEmail(email) }

    fun signIn(email: String) {
        viewModelScope.launch {
            val result = authRepository.signIn(email, password.value)
            if (result is Outcome.Failure) error.value = result.error
        }
    }

    fun signUp(email: String) {
        viewModelScope.launch {
            val result = authRepository.signUp(email, password.value)
            if (result is Outcome.Failure) error.value = result.error
        }
    }

    fun signOut() = launch { authRepository.signOut() }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSyncEnabled(enabled)
            syncScheduler.setPeriodicEnabled(enabled, settingsRepository.current().syncOnWifiOnly)
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSyncOnWifiOnly(enabled)
            val current = settingsRepository.current()
            if (current.autoSyncEnabled) syncScheduler.setPeriodicEnabled(true, enabled)
        }
    }

    fun syncNow(successTemplate: String) {
        viewModelScope.launch {
            when (val result = syncRepository.syncNow()) {
                is Outcome.Success -> {
                    message.value = successTemplate.format(result.value.pushed, result.value.pulled)
                    error.value = null
                }
                is Outcome.Failure -> {
                    error.value = result.error
                    message.value = null
                }
            }
        }
    }

    // --- zdjęcia ----------------------------------------------------------

    fun setPhotoMaxDimension(px: Int) = launch { settingsRepository.setPhotoMaxDimension(px) }
    fun setPhotoTargetKb(kb: Int) = launch { settingsRepository.setPhotoTargetKb(kb) }

    fun exactAlarmSettingsIntent() = alarmScheduler.exactAlarmSettingsIntent()

    // --- import z Cookmate (narzędzie deweloperskie) -----------------------

    fun importFromCookmate(uri: Uri, resultTemplate: String, errorFallback: String) {
        viewModelScope.launch {
            message.value = when (val result = cookmateImporter.importFromMcb(uri)) {
                is Outcome.Success -> resultTemplate.format(
                    result.value.imported,
                    result.value.total,
                    result.value.withoutPhoto,
                )
                is Outcome.Failure -> errorFallback
            }
        }
    }

    fun consumeMessage() {
        message.value = null
        error.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
