package pl.przepisak.data.prefs

import kotlinx.coroutines.flow.Flow

/** Motyw aplikacji. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Układ listy przepisów. */
enum class RecipeViewMode { LIST, GRID, COMPACT }

/** Model Gemini używany do importu (RF-05, RF-06). */
enum class GeminiModel(val apiName: String, val displayName: String) {
    FLASH("gemini-2.5-flash", "Gemini 2.5 Flash"),
    FLASH_LITE("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
    PRO("gemini-2.5-pro", "Gemini 2.5 Pro"),
}

/**
 * Ustawienia bez sekretów. Klucze API i hasła mieszkają w [SecretStore],
 * bo trafiają do EncryptedSharedPreferences (RF-07).
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val geminiModel: GeminiModel = GeminiModel.FLASH,
    /** Powiększenie czcionki w trybie gotowania (RF-11). */
    val kitchenFontScale: Float = 1.3f,
    /** Domyślna liczba porcji przy nowym przepisie. */
    val defaultServings: Int = 4,
    val autoSyncEnabled: Boolean = false,
    val syncOnWifiOnly: Boolean = true,
    val supabaseUrl: String = "",
    val supabaseEmail: String = "",
    /** Znacznik ostatniego udanego pobrania z chmury (RF-15). */
    val lastSyncAtEpochMs: Long = 0,
    /** Maksymalny rozmiar dłuższej krawędzi zdjęcia po kompresji (RF-14). */
    val photoMaxDimensionPx: Int = 1600,
    /** Docelowy rozmiar pliku zdjęcia w kilobajtach (RF-14). */
    val photoTargetKb: Int = 300,
    /** Czy przy wejściu w tryb gotowania od razu blokować wygaszanie ekranu (RF-12). */
    val keepScreenOnInKitchenMode: Boolean = true,
    val recipeViewMode: RecipeViewMode = RecipeViewMode.LIST,
) {
    val isSupabaseConfigured: Boolean get() = supabaseUrl.isNotBlank()
}

/** Ustawienia aplikacji trzymane w DataStore. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setUseDynamicColor(enabled: Boolean)
    suspend fun setGeminiModel(model: GeminiModel)
    suspend fun setKitchenFontScale(scale: Float)
    suspend fun setDefaultServings(servings: Int)
    suspend fun setAutoSyncEnabled(enabled: Boolean)
    suspend fun setSyncOnWifiOnly(enabled: Boolean)
    suspend fun setSupabaseUrl(url: String)
    suspend fun setSupabaseEmail(email: String)
    suspend fun setLastSyncAt(epochMs: Long)
    suspend fun setPhotoMaxDimension(px: Int)
    suspend fun setPhotoTargetKb(kb: Int)
    suspend fun setKeepScreenOnInKitchenMode(enabled: Boolean)
    suspend fun setRecipeViewMode(mode: RecipeViewMode)
}

/**
 * Magazyn sekretów oparty na EncryptedSharedPreferences (RF-07).
 *
 * Nie przechowujemy tu nic, czego nie da się odtworzyć — utrata klucza po
 * wyczyszczeniu danych aplikacji oznacza jedynie ponowne wpisanie go w Ustawieniach.
 */
interface SecretStore {
    /** Klucz Google Gemini API wpisany przez użytkownika. */
    var geminiApiKey: String?

    /** Klucz `anon` projektu Supabase. */
    var supabaseAnonKey: String?

    /** Hasło do konta Supabase — pozwala odnowić sesję bez pytania użytkownika. */
    var supabasePassword: String?

    val hasGeminiKey: Boolean

    fun clearAll()
}
