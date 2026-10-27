package pl.przepisak.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Planowanie synchronizacji w tle (RF-15). */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Jednorazowa synchronizacja „na żądanie” z Ustawień. */
    fun syncNow() {
        workManager.enqueueUniqueWork(
            SyncWorker.NAME_ONE_TIME,
            // KEEP, nie REPLACE: kilkukrotne dotknięcie przycisku nie ma zaczynać
            // synchronizacji od nowa.
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints(wifiOnly = false))
                .build(),
        )
    }

    fun setPeriodicEnabled(enabled: Boolean, wifiOnly: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(SyncWorker.NAME_PERIODIC)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints(wifiOnly))
                .build(),
        )
    }

    /** `true`, gdy synchronizacja właśnie trwa — do pokazania postępu w Ustawieniach. */
    fun observeSyncing(): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.NAME_ONE_TIME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    private fun constraints(wifiOnly: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    private companion object {
        /** Sześć godzin: kopia zapasowa przepisów nie musi być częstsza. */
        const val SYNC_INTERVAL_HOURS = 6L
    }
}
