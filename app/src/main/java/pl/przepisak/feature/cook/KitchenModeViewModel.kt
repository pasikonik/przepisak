package pl.przepisak.feature.cook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.przepisak.core.util.TimeProvider
import pl.przepisak.data.prefs.SettingsRepository
import pl.przepisak.data.repository.RecipeRepository
import pl.przepisak.data.repository.TimerRepository
import pl.przepisak.domain.model.CookingTimer
import pl.przepisak.domain.model.DetectedTimer
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.scaling.RecipeScaler
import pl.przepisak.domain.timer.TimeDetector
import javax.inject.Inject

data class KitchenModeUiState(
    val recipe: RecipeAggregate? = null,
    val isLoading: Boolean = true,
    val detectedTimers: Map<Int, List<DetectedTimer>> = emptyMap(),
    val timers: List<CookingTimer> = emptyList(),
    val nowMs: Long = 0,
    val fontScale: Float = 1.3f,
    val keepScreenOn: Boolean = true,
    val checkedIngredientIds: Set<Long> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KitchenModeViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val timerRepository: TimerRepository,
    private val settingsRepository: SettingsRepository,
    private val scaler: RecipeScaler,
    private val time: TimeProvider,
) : ViewModel() {

    private val recipeId = MutableStateFlow(0L)
    private val servings = MutableStateFlow<Int?>(null)
    private val checked = MutableStateFlow<Set<Long>>(emptySet())
    private val nowMs = MutableStateFlow(0L)

    private var tickJob: Job? = null

    val state: StateFlow<KitchenModeUiState> = combine(
        recipeId.flatMapLatest { id -> recipeRepository.observeRecipe(id) },
        servings,
        combine(timerRepository.observeActive(), nowMs) { timers, now -> timers to now },
        settingsRepository.settings,
        checked,
    ) { aggregate, targetServings, timersAndNow, settings, checkedIds ->
        val (timers, now) = timersAndNow
        val scaled = aggregate?.let {
            scaler.scaleAggregate(it, scaler.factorFor(it.recipe.servings, targetServings))
        }
        KitchenModeUiState(
            recipe = scaled,
            isLoading = aggregate == null,
            detectedTimers = scaled?.steps
                ?.associate { step -> step.position to TimeDetector.detect(step.text) }
                .orEmpty(),
            timers = timers,
            nowMs = now,
            fontScale = settings.kitchenFontScale,
            keepScreenOn = settings.keepScreenOnInKitchenMode,
            checkedIngredientIds = checkedIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KitchenModeUiState())

    fun load(id: Long, initialServings: Int) {
        if (recipeId.value != id) {
            recipeId.value = id
            servings.value = initialServings.takeIf { it > 0 }
            checked.value = emptySet()
        }
        startTicking()
    }

    /**
     * Jedna pętla odliczająca dla całego ekranu.
     *
     * Minutniki wiedzą, kiedy się kończą (`endAtEpochMs`), więc UI potrzebuje tylko
     * bieżącego czasu. Osobny licznik na każdy minutnik byłby marnotrawstwem, a przy
     * braku minutników pętla w ogóle nie chodzi — nie ma po co budzić procesora.
     */
    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (isActive) {
                nowMs.value = time.nowMs()
                if (state.value.timers.isEmpty()) {
                    // Bez aktywnych minutników wystarczy rzadkie sprawdzanie.
                    delay(IDLE_TICK_MS)
                } else {
                    delay(ACTIVE_TICK_MS)
                }
            }
        }
    }

    fun toggleIngredientChecked(ingredientId: Long) {
        checked.value = checked.value.let { current ->
            if (ingredientId in current) current - ingredientId else current + ingredientId
        }
    }

    fun startTimer(detected: DetectedTimer, stepPosition: Int) {
        val id = recipeId.value
        viewModelScope.launch {
            timerRepository.start(detected.label, detected.seconds, id, stepPosition)
        }
    }

    fun pauseTimer(timerId: Long) = viewModelScope.launch { timerRepository.pause(timerId) }.let { }

    fun resumeTimer(timerId: Long) = viewModelScope.launch { timerRepository.resume(timerId) }.let { }

    fun addMinute(timerId: Long) = viewModelScope.launch { timerRepository.addSeconds(timerId, 60) }.let { }

    fun cancelTimer(timerId: Long) = viewModelScope.launch { timerRepository.cancel(timerId) }.let { }

    fun cancelAllTimers() = viewModelScope.launch { timerRepository.cancelAll() }.let { }

    private companion object {
        const val ACTIVE_TICK_MS = 1_000L
        const val IDLE_TICK_MS = 10_000L
    }
}
