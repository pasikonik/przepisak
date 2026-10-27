package pl.przepisak.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.przepisak.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja [SecretStore] na EncryptedSharedPreferences (RF-07): klucz Gemini
 * i dane logowania do Supabase nie mają prawa leżeć w zwykłym, czytelnym pliku XML.
 *
 * Klucz szyfrujący siedzi w Android Keystore i jest przywiązany do konkretnego
 * urządzenia. Po przywróceniu kopii zapasowej na innym telefonie (albo po
 * sklonowaniu danych aplikacji) Keystore go nie ma — otwarcie starego pliku kończy
 * się wtedy wyjątkiem, a sam plik i tak jest już nieodczytywalny. Dlatego magazyn
 * tworzymy leniwie i w dwóch krokach: najpierw próba zwykła, potem — po skasowaniu
 * pliku — próba ponowna (świeży plik, świeży klucz). Jeśli i to zawiedzie (np.
 * urządzenie bez sprawnego Keystore), aplikacja ma pozostać używalna, więc spadamy
 * na zwykłe, nieszyfrowane SharedPreferences: brak zapisanych ustawień jest gorszy
 * niż chwilowy brak szyfrowania klucza API.
 */
@Singleton
class EncryptedSecretStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecretStore {

    private val prefs: SharedPreferences by lazy { openPreferences() }

    override var geminiApiKey: String?
        get() = prefs.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) = putOrRemove(KEY_GEMINI_API_KEY, value)

    override var supabaseAnonKey: String?
        get() = prefs.getString(KEY_SUPABASE_ANON_KEY, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SUPABASE_ANON_KEY.takeIf { it.isNotBlank() }
        set(value) = putOrRemove(KEY_SUPABASE_ANON_KEY, value)

    override var supabasePassword: String?
        get() = prefs.getString(KEY_SUPABASE_PASSWORD, null)?.takeIf { it.isNotBlank() }
        set(value) = putOrRemove(KEY_SUPABASE_PASSWORD, value)

    override val hasGeminiKey: Boolean get() = !geminiApiKey.isNullOrBlank()

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun putOrRemove(key: String, value: String?) {
        val editor = prefs.edit()
        if (value.isNullOrBlank()) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }

    /** Patrz KDoc klasy — dwie siatki bezpieczeństwa: skasuj-i-spróbuj-ponownie, potem plain fallback. */
    private fun openPreferences(): SharedPreferences =
        runCatching { createEncryptedPreferences() }
            .recoverCatching {
                context.deleteSharedPreferences(FILE_NAME)
                createEncryptedPreferences()
            }
            .getOrElse {
                // Awaryjna, osobna nazwa pliku — dawne (zaszyfrowane) bajty nie mają
                // prawa zostać kiedykolwiek odczytane jak zwykły, jawny tekst.
                context.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
            }

    private fun createEncryptedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val FILE_NAME = "przepisak_secrets"
        const val FALLBACK_FILE_NAME = "przepisak_secrets_fallback"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_SUPABASE_ANON_KEY = "supabase_anon_key"
        const val KEY_SUPABASE_PASSWORD = "supabase_password"
    }
}
