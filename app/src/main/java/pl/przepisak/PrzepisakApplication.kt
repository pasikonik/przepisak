package pl.przepisak

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import pl.przepisak.notification.NotificationChannels
import javax.inject.Inject

/**
 * Klasa aplikacji.
 *
 * Manifest wyłącza domyślną inicjalizację WorkManagera (`WorkManagerInitializer`),
 * bo tylko własna konfiguracja pozwala podstawić [HiltWorkerFactory] — bez niej
 * `SyncWorker` nie dostałby wstrzykniętych zależności.
 */
@HiltAndroidApp
class PrzepisakApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Kanały powiadomień muszą istnieć, zanim cokolwiek spróbuje ich użyć —
        // minutnik potrafi wystartować od razu po otwarciu przepisu.
        NotificationChannels.ensureCreated(this)
    }
}
