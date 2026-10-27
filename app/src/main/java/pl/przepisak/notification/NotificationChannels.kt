package pl.przepisak.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.content.getSystemService

/**
 * Kanały powiadomień.
 *
 * Nazwy kanałów wpisujemy w kodzie, a nie w zasobach: kanał zakłada się raz i
 * jego nazwa zostaje w systemie na zawsze — późniejsza zmiana tłumaczenia i tak
 * nie zaktualizowałaby tego, co widzi użytkownik w ustawieniach systemowych.
 */
object NotificationChannels {

    /** Trwałe, ciche powiadomienie z odliczaniem. */
    const val TIMERS = "timers"

    /** Sygnał zakończenia minutnika — musi się przebić. */
    const val TIMER_ALERTS = "timer_alerts"

    /** Informacje o kopii zapasowej. */
    const val SYNC = "sync"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(TIMERS, "Minutniki", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Trwałe powiadomienie z odliczaniem czasu"
                setShowBadge(false)
                enableVibration(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                TIMER_ALERTS,
                "Koniec minutnika",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Dźwięk i wibracja, gdy minutnik dobiegnie końca"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(SYNC, "Synchronizacja", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Informacje o kopii zapasowej w chmurze"
                setShowBadge(false)
            },
        )
    }
}
