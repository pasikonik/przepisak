package pl.przepisak.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.TimeProvider
import pl.przepisak.data.local.dao.TimerDao
import pl.przepisak.data.local.entity.TimerEntity
import pl.przepisak.data.local.toDomain
import pl.przepisak.domain.model.CookingTimer
import pl.przepisak.domain.model.TimerState
import pl.przepisak.notification.TimerAlarmScheduler
import pl.przepisak.notification.TimerNotifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minutniki kuchenne (RF-13).
 *
 * Nie używamy usługi pierwszoplanowej. Minutnik to trzy rzeczy:
 *  1. zapisany w bazie moment końca (`endAtEpochMs`) — przeżywa ubicie procesu,
 *  2. dokładny alarm systemowy — budzi aplikację w odpowiedniej chwili,
 *  3. trwałe powiadomienie z licznikiem odliczanym przez system.
 *
 * Dzięki temu nic nie odlicza w tle, bateria nie cierpi, a aplikacja nie potrzebuje
 * uprawnienia do usługi działającej na pierwszym planie.
 */
@Singleton
class TimerRepositoryImpl @Inject constructor(
    private val timerDao: TimerDao,
    private val scheduler: TimerAlarmScheduler,
    private val notifier: TimerNotifier,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TimerRepository {

    override fun observeActive(): Flow<List<CookingTimer>> =
        timerDao.observeActive().map { entities -> entities.map { it.toDomain() } }

    override suspend fun start(
        label: String,
        seconds: Int,
        recipeId: Long?,
        stepPosition: Int?,
    ): Long = withContext(io) {
        val safeSeconds = seconds.coerceAtLeast(1)

        // Powtórne dotknięcie tego samego chipa ma przestawić minutnik, a nie
        // uruchomić drugi obok — inaczej po kilku dotknięciach użytkownik ma
        // cztery alarmy na ten sam krok.
        if (recipeId != null && stepPosition != null) {
            timerDao.findForStep(recipeId, stepPosition)?.let { existing ->
                cancelInternal(existing.id)
            }
        }

        val now = time.nowMs()
        val entity = TimerEntity(
            label = label.ifBlank { "Minutnik" },
            totalSeconds = safeSeconds,
            remainingSeconds = safeSeconds,
            endAtEpochMs = now + safeSeconds * 1000L,
            state = TimerState.RUNNING,
            recipeId = recipeId,
            stepPosition = stepPosition,
            createdAt = now,
        )
        val id = timerDao.insert(entity)
        val stored = entity.copy(id = id)
        scheduler.schedule(id, stored.endAtEpochMs ?: now)
        notifier.showRunning(stored.toDomain())
        id
    }

    override suspend fun pause(timerId: Long) = withContext(io) {
        val timer = timerDao.getById(timerId) ?: return@withContext
        if (timer.state != TimerState.RUNNING) return@withContext

        val remaining = timer.toDomain().remainingSecondsAt(time.nowMs())
        val paused = timer.copy(
            state = TimerState.PAUSED,
            remainingSeconds = remaining,
            endAtEpochMs = null,
        )
        timerDao.update(paused)
        scheduler.cancel(timerId)
        notifier.showPaused(paused.toDomain())
    }

    override suspend fun resume(timerId: Long) = withContext(io) {
        val timer = timerDao.getById(timerId) ?: return@withContext
        if (timer.state == TimerState.RUNNING) return@withContext

        val remaining = timer.remainingSeconds.coerceAtLeast(1)
        val now = time.nowMs()
        val running = timer.copy(
            state = TimerState.RUNNING,
            remainingSeconds = remaining,
            endAtEpochMs = now + remaining * 1000L,
        )
        timerDao.update(running)
        scheduler.schedule(timerId, running.endAtEpochMs ?: now)
        notifier.showRunning(running.toDomain())
    }

    override suspend fun addSeconds(timerId: Long, seconds: Int) = withContext(io) {
        val timer = timerDao.getById(timerId) ?: return@withContext
        val now = time.nowMs()

        when (timer.state) {
            TimerState.RUNNING -> {
                val endAt = (timer.endAtEpochMs ?: now) + seconds * 1000L
                val updated = timer.copy(
                    endAtEpochMs = endAt,
                    totalSeconds = timer.totalSeconds + seconds,
                )
                timerDao.update(updated)
                scheduler.schedule(timerId, endAt)
                notifier.showRunning(updated.toDomain())
            }

            TimerState.PAUSED -> {
                val updated = timer.copy(
                    remainingSeconds = (timer.remainingSeconds + seconds).coerceAtLeast(0),
                    totalSeconds = timer.totalSeconds + seconds,
                )
                timerDao.update(updated)
                notifier.showPaused(updated.toDomain())
            }

            // Obsługa przycisku „+1 min” w alercie: minutnik już zadzwonił,
            // a użytkownik potrzebuje jeszcze chwili.
            TimerState.FINISHED -> {
                val extra = seconds.coerceAtLeast(1)
                val updated = timer.copy(
                    state = TimerState.RUNNING,
                    totalSeconds = extra,
                    remainingSeconds = extra,
                    endAtEpochMs = now + extra * 1000L,
                )
                timerDao.update(updated)
                scheduler.schedule(timerId, updated.endAtEpochMs ?: now)
                notifier.showRunning(updated.toDomain())
            }
        }
    }

    override suspend fun cancel(timerId: Long) = withContext(io) {
        cancelInternal(timerId)
    }

    override suspend fun cancelAll() = withContext(io) {
        timerDao.getActive().forEach { cancelInternal(it.id) }
        timerDao.deleteFinished()
        notifier.cancelAll()
    }

    override suspend fun markFinished(timerId: Long) = withContext(io) {
        val timer = timerDao.getById(timerId) ?: return@withContext
        val finished = timer.copy(
            state = TimerState.FINISHED,
            remainingSeconds = 0,
            endAtEpochMs = null,
        )
        timerDao.update(finished)
        scheduler.cancel(timerId)
        notifier.showFinished(finished.toDomain())
    }

    /**
     * Alarmy nie przeżywają restartu urządzenia, więc po każdym uruchomieniu
     * systemu trzeba je ustawić na nowo. Minutniki, których termin minął w czasie,
     * gdy telefon był wyłączony, zamykamy od razu.
     */
    override suspend fun rescheduleAfterBoot() = withContext(io) {
        val now = time.nowMs()
        timerDao.getRunning().forEach { timer ->
            val endAt = timer.endAtEpochMs
            if (endAt == null || endAt <= now) {
                markFinishedInternal(timer, now)
            } else {
                scheduler.schedule(timer.id, endAt)
                notifier.showRunning(timer.toDomain())
            }
        }
    }

    private suspend fun cancelInternal(timerId: Long) {
        scheduler.cancel(timerId)
        notifier.cancel(timerId)
        timerDao.delete(timerId)
    }

    private suspend fun markFinishedInternal(timer: TimerEntity, nowMs: Long) {
        val finished = timer.copy(
            state = TimerState.FINISHED,
            remainingSeconds = 0,
            endAtEpochMs = null,
        )
        timerDao.update(finished)
        scheduler.cancel(timer.id)
        notifier.showFinished(finished.toDomain())
    }
}
