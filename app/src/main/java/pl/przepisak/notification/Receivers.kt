package pl.przepisak.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.przepisak.data.repository.TimerRepository
import javax.inject.Inject

/**
 * Wspólny sposób wykonania pracy w odbiorniku.
 *
 * `onReceive` działa na głównym wątku i nie może blokować, a my musimy sięgnąć do
 * bazy. [BroadcastReceiver.goAsync] przedłuża życie odbiornika, dopóki nie wywołamy
 * `finish()` — dlatego `finish()` jest w `finally`, inaczej system po kilkunastu
 * sekundach ubiłby proces z ostrzeżeniem.
 */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}

/** Alarm systemowy: minutnik dobiegł końca. */
@AndroidEntryPoint
class TimerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var timerRepository: TimerRepository

    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
        if (timerId <= 0L) return
        runAsync { timerRepository.markFinished(timerId) }
    }

    companion object {
        const val EXTRA_TIMER_ID = "timer_id"
    }
}

/** Przyciski w powiadomieniu minutnika. */
@AndroidEntryPoint
class TimerActionReceiver : BroadcastReceiver() {

    @Inject lateinit var timerRepository: TimerRepository

    @Inject lateinit var notifier: TimerNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getLongExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, -1L)
        if (timerId <= 0L) return

        when (intent.action) {
            ACTION_PAUSE -> runAsync { timerRepository.pause(timerId) }
            ACTION_RESUME -> runAsync { timerRepository.resume(timerId) }
            ACTION_CANCEL -> runAsync { timerRepository.cancel(timerId) }
            ACTION_ADD_MINUTE -> runAsync { timerRepository.addSeconds(timerId, 60) }
            ACTION_DISMISS -> runAsync {
                timerRepository.cancel(timerId)
                notifier.cancel(timerId)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "pl.przepisak.action.TIMER_PAUSE"
        const val ACTION_RESUME = "pl.przepisak.action.TIMER_RESUME"
        const val ACTION_CANCEL = "pl.przepisak.action.TIMER_CANCEL"
        const val ACTION_ADD_MINUTE = "pl.przepisak.action.TIMER_ADD_MINUTE"
        const val ACTION_DISMISS = "pl.przepisak.action.TIMER_DISMISS"
    }
}

/**
 * Po restarcie urządzenia (i po aktualizacji aplikacji) trzeba ustawić alarmy
 * ponownie — system usuwa je wraz z wyłączeniem telefonu.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var timerRepository: TimerRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                runAsync { timerRepository.rescheduleAfterBoot() }
        }
    }
}
