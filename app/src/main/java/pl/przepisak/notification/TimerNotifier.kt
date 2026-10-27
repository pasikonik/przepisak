package pl.przepisak.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.przepisak.MainActivity
import pl.przepisak.domain.model.CookingTimer
import pl.przepisak.domain.scaling.QuantityFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Powiadomienia minutników (RF-13).
 *
 * Trwałe powiadomienie korzysta z systemowego licznika
 * ([NotificationCompat.Builder.setUsesChronometer] razem z
 * `setChronometerCountDown`), więc odliczanie rysuje system, a aplikacja nie musi
 * budzić się co sekundę.
 *
 * Ikony bierzemy z zasobów systemowych — aplikacja jest na własny użytek i nie
 * warto tu mnożyć plików graficznych.
 */
@Singleton
class TimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager: NotificationManager? = context.getSystemService()

    fun showRunning(timer: CookingTimer) {
        val endAt = timer.endAtEpochMs ?: return
        val notification = baseBuilder(timer)
            .setChannelId(NotificationChannels.TIMERS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .addAction(0, "Pauza", actionIntent(timer.id, TimerActionReceiver.ACTION_PAUSE))
            .addAction(0, "Zatrzymaj", actionIntent(timer.id, TimerActionReceiver.ACTION_CANCEL))
            .build()
        notify(runningId(timer.id), notification)
    }

    fun showPaused(timer: CookingTimer) {
        val notification = baseBuilder(timer)
            .setChannelId(NotificationChannels.TIMERS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentText(
                "Wstrzymany — zostało ${QuantityFormatter.formatSeconds(timer.remainingSecondsWhenPaused)}",
            )
            .addAction(0, "Wznów", actionIntent(timer.id, TimerActionReceiver.ACTION_RESUME))
            .addAction(0, "Zatrzymaj", actionIntent(timer.id, TimerActionReceiver.ACTION_CANCEL))
            .build()
        notify(runningId(timer.id), notification)
    }

    fun showFinished(timer: CookingTimer) {
        cancel(timer.id)
        val notification = baseBuilder(timer)
            .setChannelId(NotificationChannels.TIMER_ALERTS)
            .setContentText("Czas minął!")
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 600))
            .addAction(0, "+1 min", actionIntent(timer.id, TimerActionReceiver.ACTION_ADD_MINUTE))
            .addAction(0, "OK", actionIntent(timer.id, TimerActionReceiver.ACTION_DISMISS))
            .build()
        notify(finishedId(timer.id), notification)
    }

    fun cancel(timerId: Long) {
        manager?.cancel(runningId(timerId))
        manager?.cancel(finishedId(timerId))
    }

    fun cancelAll() {
        manager?.cancelAll()
    }

    private fun baseBuilder(timer: CookingTimer): NotificationCompat.Builder =
        NotificationCompat.Builder(context, NotificationChannels.TIMERS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(timer.label.ifBlank { "Minutnik" })
            .setContentIntent(openAppIntent())
            .setSilent(false)

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 0, intent, immutableFlags())
    }

    /**
     * Osobny `requestCode` dla każdej pary (minutnik, akcja).
     * Bez tego Android potraktowałby wszystkie te intencje jako jedną i przycisk
     * „Pauza” w jednym powiadomieniu zatrzymywałby inny minutnik.
     */
    private fun actionIntent(timerId: Long, action: String): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java).apply {
            this.action = action
            putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, timerId)
        }
        val requestCode = (timerId * 31 + action.hashCode()).toInt()
        return PendingIntent.getBroadcast(context, requestCode, intent, immutableFlags())
    }

    private fun immutableFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun notify(id: Int, notification: android.app.Notification) {
        // Na Androidzie 13+ brak zgody oznacza, że powiadomienie i tak nie dotrze.
        // Milczymy zamiast rzucać wyjątkiem — minutnik ma dalej działać.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager?.notify(id, notification)
    }

    private fun runningId(timerId: Long): Int = (1000 + timerId).toInt()

    /** Alert dostaje osobny identyfikator, żeby nie zastąpił trwałego powiadomienia. */
    private fun finishedId(timerId: Long): Int = (5000 + timerId).toInt()
}
