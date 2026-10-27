package pl.przepisak.feature.timers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.przepisak.core.util.TimeProvider
import pl.przepisak.data.repository.TimerRepository
import pl.przepisak.domain.model.CookingTimer
import javax.inject.Inject

data class TimersUiState(
    val timers: List<CookingTimer> = emptyList(),
    val nowMs: Long = 0,
)

@HiltViewModel
class TimersViewModel @Inject constructor(
    private val timerRepository: TimerRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val nowMs = MutableStateFlow(0L)

    val state: StateFlow<TimersUiState> = combine(
        timerRepository.observeActive(),
        nowMs,
    ) { timers, now -> TimersUiState(timers = timers, nowMs = now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimersUiState())

    init {
        // Jedna pętla dla całego ekranu; minutniki same wiedzą, kiedy się kończą,
        // więc UI potrzebuje wyłącznie bieżącego czasu.
        viewModelScope.launch {
            while (isActive) {
                nowMs.value = time.nowMs()
                delay(1_000)
            }
        }
    }

    fun start(label: String, seconds: Int) {
        viewModelScope.launch { timerRepository.start(label, seconds) }
    }

    fun pause(id: Long) {
        viewModelScope.launch { timerRepository.pause(id) }
    }

    fun resume(id: Long) {
        viewModelScope.launch { timerRepository.resume(id) }
    }

    fun addMinute(id: Long) {
        viewModelScope.launch { timerRepository.addSeconds(id, 60) }
    }

    fun cancel(id: Long) {
        viewModelScope.launch { timerRepository.cancel(id) }
    }

    fun cancelAll() {
        viewModelScope.launch { timerRepository.cancelAll() }
    }
}
