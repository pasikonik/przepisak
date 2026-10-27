package pl.przepisak.feature.importflow

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.przepisak.R
import pl.przepisak.core.ui.theme.PrzepisakTheme
import pl.przepisak.data.prefs.SettingsRepository
import pl.przepisak.data.repository.ImportRepository
import javax.inject.Inject

/**
 * Odbiera odnośniki udostępnione z innych aplikacji (RF-04).
 *
 * Rejestrujemy wyłącznie `ACTION_SEND` i `ACTION_PROCESS_TEXT` — dzięki temu
 * Przepisak pojawia się w systemowym „Udostępnij”, ale nie zgłasza się jako
 * przeglądarka do otwierania adresów.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var importRepository: ImportRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val url = sharedText?.let { importRepository.extractUrl(it) }

        if (url == null) {
            Toast.makeText(this, R.string.import_no_url, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            setContent {
                PrzepisakTheme(
                    themeMode = settings.themeMode,
                    useDynamicColor = settings.useDynamicColor,
                ) {
                    ImportPreviewScreen(
                        url = url,
                        // Po zapisie zamykamy okno zamiast otwierać szczegóły:
                        // użytkownik był w innej aplikacji i tam chce wrócić.
                        onSaved = {
                            Toast.makeText(
                                this@ShareReceiverActivity,
                                R.string.import_saved,
                                Toast.LENGTH_SHORT,
                            ).show()
                            finish()
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }
}
