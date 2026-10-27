package pl.przepisak.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.przepisak.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Planowanie dokładnych alarmów dla minutników (RF-13).
 *
 * Używamy [AlarmManager.setAlarmClock], bo to jedyna kategoria alarmu, której
 * system nie opóźnia w trybie Doze — a minutnik kuchenny, który dzwoni pięć minut
 * za późno, jest bezwartościowy. Dodatkowo system pokazuje wtedy ikonę budzika,
 * więc użytkownik widzi, że coś jest ustawione.
 */
@Singleton
class TimerAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    fun schedule(timerId: Long, triggerAtEpochMs: Long) {
        val manager = alarmManager ?: return
        val operation = alarmIntent(timerId)

        if (canScheduleExact()) {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtEpochMs, showAppIntent()),
                operation,
            )
        } else {
            // Bez zgody na dokładne alarmy zostaje wariant „w miarę dokładny”.
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, operation)
        }
    }

    fun cancel(timerId: Long) {
        alarmManager?.cancel(alarmIntent(timerId))
    }

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /** Intencja do ekranu systemowego, na którym użytkownik może przyznać zgodę. */
    fun exactAlarmSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }

    private fun alarmIntent(timerId: Long): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            context,
            timerId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
