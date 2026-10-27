package pl.przepisak.feature.recipedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.przepisak.data.repository.RecipeRepository
import pl.przepisak.data.repository.TimerRepository
import pl.przepisak.domain.model.DetectedTimer
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.scaling.RecipeScaler
import pl.przepisak.domain.timer.TimeDetector
import javax.inject.Inject

data class RecipeDetailUiState(
    val recipe: RecipeAggregate? = null,
    val isLoading: Boolean = true,
    /** Liczba porcji wybrana przez użytkownika; `null` = oryginalna. */
    val targetServings: Int? = null,
    /** Łączny mnożnik zastosowany do składników. */
    val factor: Double = 1.0,
    /** Mnożnik z kalkulatora form, jeśli użytkownik go ustawił. */
    val panFactor: Double? = null,
    /** Minutniki wykryte w treści kroków — indeks kroku → lista minutników. */
    val detectedTimers: Map<Int, List<DetectedTimer>> = emptyMap(),
    val checkedIngredientIds: Set<Long> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val timerRepository: TimerRepository,
    private val scaler: RecipeScaler,
) : ViewModel() {

    private val recipeId = MutableStateFlow(0L)
    private val targetServings = MutableStateFlow<Int?>(null)
    private val panFactor = MutableStateFlow<Double?>(null)
    private val checked = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<RecipeDetailUiState> = recipeId
        .flatMapLatest { id ->
            combine(
                recipeRepository.observeRecipe(id),
                targetServings,
                panFactor,
                checked,
            ) { aggregate, servings, pan, checkedIds ->
                if (aggregate == null) {
                    return@combine RecipeDetailUiState(recipe = null, isLoading = id != 0L)
                }

                // Mnożniki się składają: zmiana liczby porcji i zmiana formy to dwa
                // niezależne powody, by przeliczyć te same składniki.
                val servingsFactor = scaler.factorFor(aggregate.recipe.servings, servings)
                val total = servingsFactor * (pan ?: 1.0)
                val scaled = scaler.scaleAggregate(aggregate, total)

                RecipeDetailUiState(
                    recipe = scaled,
                    isLoading = false,
                    targetServings = servings ?: aggregate.recipe.servings,
                    factor = total,
                    panFactor = pan,
                    // Wykrywanie czasu robimy raz tutaj, a nie przy każdym
                    // przerysowaniu listy kroków.
                    detectedTimers = scaled.steps.associate { step ->
                        step.position to TimeDetector.detect(step.text)
                    },
                    checkedIngredientIds = checkedIds,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    fun load(id: Long) {
        if (recipeId.value != id) {
            recipeId.value = id
            targetServings.value = null
            panFactor.value = null
            checked.value = emptySet()
        }
    }

    fun setTargetServings(servings: Int?) {
        targetServings.value = servings?.takeIf { it > 0 }
    }

    fun applyMultiplier(multiplier: Double) {
        val original = state.value.recipe?.recipe?.servings
        if (original != null && original > 0) {
            targetServings.value = (original * multiplier).toInt().coerceAtLeast(1)
        } else {
            panFactor.value = multiplier
        }
    }

    fun setPanFactor(factor: Double?) {
        panFactor.value = factor
    }

    fun resetScale() {
        targetServings.value = null
        panFactor.value = null
    }

    fun toggleIngredientChecked(ingredientId: Long) {
        checked.value = checked.value.let { current ->
            if (ingredientId in current) current - ingredientId else current + ingredientId
        }
    }

    fun toggleFavorite() {
        val recipe = state.value.recipe?.recipe ?: return
        viewModelScope.launch { recipeRepository.setFavorite(recipe.id, !recipe.isFavorite) }
    }

    fun setRating(rating: Int?) {
        val id = state.value.recipe?.recipe?.id ?: return
        viewModelScope.launch { recipeRepository.setRating(id, rating) }
    }

    fun saveNotes(notes: String) {
        val id = state.value.recipe?.recipe?.id ?: return
        viewModelScope.launch { recipeRepository.setNotes(id, notes) }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = state.value.recipe?.recipe?.id ?: return
        viewModelScope.launch {
            recipeRepository.deleteRecipe(id)
            onDeleted()
        }
    }

    fun duplicate(onDuplicated: (Long) -> Unit) {
        val id = state.value.recipe?.recipe?.id ?: return
        viewModelScope.launch { recipeRepository.duplicateRecipe(id)?.let(onDuplicated) }
    }

    fun startTimer(label: String, seconds: Int, stepPosition: Int) {
        val id = state.value.recipe?.recipe?.id ?: return
        viewModelScope.launch {
            timerRepository.start(
                label = label,
                seconds = seconds,
                recipeId = id,
                stepPosition = stepPosition,
            )
        }
    }
}
