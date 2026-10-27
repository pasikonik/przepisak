package pl.przepisak.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.Outcome
import pl.przepisak.data.repository.SyncRepository

/**
 * Wysyła zmiany do chmury i pobiera zdalne (RF-15).
 *
 * Ponawiamy tylko błędy sieciowe. Brak konfiguracji albo nieudane logowanie nie
 * naprawią się same, więc ponawianie ich tylko zużywałoby baterię.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (val outcome = syncRepository.syncNow()) {
        is Outcome.Success -> Result.success()
        is Outcome.Failure -> when (outcome.error) {
            is AppError.NoNetwork, is AppError.Http, is AppError.Unknown ->
                if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()

            else -> Result.failure()
        }
    }

    companion object {
        const val NAME_PERIODIC = "przepisak-sync-periodic"
        const val NAME_ONE_TIME = "przepisak-sync-now"

        private const val MAX_ATTEMPTS = 4
    }
}
