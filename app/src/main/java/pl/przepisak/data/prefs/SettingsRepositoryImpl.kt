package pl.przepisak.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.przepisak.BuildConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegat DataStore na poziomie pliku — ma istnieć dokładnie jedna instancja na
 * daną nazwę w całym procesie aplikacji (Android zgłasza błąd, gdy powstaną dwie).
 */
private val Context.settingsDataStore by preferencesDataStore(name = "przepisak_settings")

/**
 * Klucze DataStore trzymane osobno od [AppSettings] — zmiana nazwy pola domenowego
 * albo dodanie nowego nie wymusza wtedy migracji zapisanych już preferencji.
 */
private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val GEMINI_MODEL = stringPreferencesKey("gemini_model")
    val KITCHEN_FONT_SCALE = floatPreferencesKey("kitchen_font_scale")
    val DEFAULT_SERVINGS = intPreferencesKey("default_servings")
    val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
    val SYNC_ON_WIFI_ONLY = booleanPreferencesKey("sync_on_wifi_only")
    val SUPABASE_URL = stringPreferencesKey("supabase_url")
    val SUPABASE_EMAIL = stringPreferencesKey("supabase_email")
    val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    val PHOTO_MAX_DIMENSION_PX = intPreferencesKey("photo_max_dimension_px")
    val PHOTO_TARGET_KB = intPreferencesKey("photo_target_kb")
    val KEEP_SCREEN_ON_IN_KITCHEN_MODE = booleanPreferencesKey("keep_screen_on_in_kitchen_mode")
    val RECIPE_VIEW_MODE = stringPreferencesKey("recipe_view_mode")
}

/**
 * Odczyt enuma po zapisanej nazwie ([Enum.name]), odporny na wpis spoza aktualnego
 * zbioru wartości (np. wariant usunięty w nowszej wersji aplikacji albo plik
 * podmieniony ręcznie) — wtedy zamiast wyjątku wraca wartość domyślna.
 */
private inline fun <reified E : Enum<E>> Preferences.enumOrDefault(
    key: Preferences.Key<String>,
    default: E,
): E {
    val stored = this[key] ?: return default
    return enumValues<E>().firstOrNull { it.name == stored } ?: default
}

/**
 * Implementacja [SettingsRepository] na Jetpack DataStore Preferences (RF-07).
 *
 * Trzyma wyłącznie jawne ustawienia — sekrety (klucze API, hasło) mieszkają
 * w [EncryptedSecretStore], bo DataStore Preferences zapisuje dane jawnym tekstem.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            // Uszkodzony plik preferencji (np. proces zabity w trakcie zapisu) nie
            // może zablokować startu aplikacji — traktujemy go jak pusty i lecimy
            // dalej na wartościach domyślnych, zamiast zawiesić cały ekran startowy.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs.toAppSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setGeminiModel(model: GeminiModel) {
        context.settingsDataStore.edit { it[Keys.GEMINI_MODEL] = model.name }
    }

    override suspend fun setKitchenFontScale(scale: Float) {
        context.settingsDataStore.edit { it[Keys.KITCHEN_FONT_SCALE] = scale }
    }

    override suspend fun setDefaultServings(servings: Int) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_SERVINGS] = servings }
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_SYNC_ENABLED] = enabled }
    }

    override suspend fun setSyncOnWifiOnly(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SYNC_ON_WIFI_ONLY] = enabled }
    }

    override suspend fun setSupabaseUrl(url: String) {
        context.settingsDataStore.edit { it[Keys.SUPABASE_URL] = url }
    }

    override suspend fun setSupabaseEmail(email: String) {
        context.settingsDataStore.edit { it[Keys.SUPABASE_EMAIL] = email }
    }

    override suspend fun setLastSyncAt(epochMs: Long) {
        context.settingsDataStore.edit { it[Keys.LAST_SYNC_AT] = epochMs }
    }

    override suspend fun setPhotoMaxDimension(px: Int) {
        context.settingsDataStore.edit { it[Keys.PHOTO_MAX_DIMENSION_PX] = px }
    }

    override suspend fun setPhotoTargetKb(kb: Int) {
        context.settingsDataStore.edit { it[Keys.PHOTO_TARGET_KB] = kb }
    }

    override suspend fun setKeepScreenOnInKitchenMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KEEP_SCREEN_ON_IN_KITCHEN_MODE] = enabled }
    }

    override suspend fun setRecipeViewMode(mode: RecipeViewMode) {
        context.settingsDataStore.edit { it[Keys.RECIPE_VIEW_MODE] = mode.name }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = enumOrDefault(Keys.THEME_MODE, defaults.themeMode),
            useDynamicColor = this[Keys.USE_DYNAMIC_COLOR] ?: defaults.useDynamicColor,
            geminiModel = enumOrDefault(Keys.GEMINI_MODEL, defaults.geminiModel),
            kitchenFontScale = this[Keys.KITCHEN_FONT_SCALE] ?: defaults.kitchenFontScale,
            defaultServings = this[Keys.DEFAULT_SERVINGS] ?: defaults.defaultServings,
            autoSyncEnabled = this[Keys.AUTO_SYNC_ENABLED] ?: defaults.autoSyncEnabled,
            syncOnWifiOnly = this[Keys.SYNC_ON_WIFI_ONLY] ?: defaults.syncOnWifiOnly,
            // Brak wpisu = użytkownik nigdy nie zmienił adresu w Ustawieniach —
            // wracamy do wartości wbudowanej przy kompilacji (może być pusta, jeśli
            // nikt nie uzupełnił local.properties).
            supabaseUrl = this[Keys.SUPABASE_URL] ?: BuildConfig.DEFAULT_SUPABASE_URL,
            supabaseEmail = this[Keys.SUPABASE_EMAIL] ?: defaults.supabaseEmail,
            lastSyncAtEpochMs = this[Keys.LAST_SYNC_AT] ?: defaults.lastSyncAtEpochMs,
            photoMaxDimensionPx = this[Keys.PHOTO_MAX_DIMENSION_PX] ?: defaults.photoMaxDimensionPx,
            photoTargetKb = this[Keys.PHOTO_TARGET_KB] ?: defaults.photoTargetKb,
            keepScreenOnInKitchenMode = this[Keys.KEEP_SCREEN_ON_IN_KITCHEN_MODE]
                ?: defaults.keepScreenOnInKitchenMode,
            recipeViewMode = enumOrDefault(Keys.RECIPE_VIEW_MODE, defaults.recipeViewMode),
        )
    }
}
