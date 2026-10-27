package pl.przepisak.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import pl.przepisak.data.repository.DensityRepository
import pl.przepisak.domain.model.IngredientDensity
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.WeightConversion
import pl.przepisak.domain.scaling.VolumeToWeightConverter
import javax.inject.Inject

/** Kierunek przeliczania w narzędziu (RF-10). */
enum class ConversionDirection { VOLUME_TO_WEIGHT, WEIGHT_TO_VOLUME }

data class QuickRow(val label: String, val grams: Double)

data class WeightConverterUiState(
    val query: String = "",
    val results: List<IngredientDensity> = emptyList(),
    val selected: IngredientDensity? = null,
    val direction: ConversionDirection = ConversionDirection.VOLUME_TO_WEIGHT,
    val amountInput: String = "1",
    val unit: MeasureUnit = MeasureUnit.CUP,
    val conversion: WeightConversion? = null,
    val volumeResult: Double? = null,
    /** Tabela „1 szklanka / ½ szklanki / 1 łyżka / 1 łyżeczka” dla wybranego produktu. */
    val quickTable: List<QuickRow> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class WeightConverterViewModel @Inject constructor(
    private val densityRepository: DensityRepository,
    private val converter: VolumeToWeightConverter,
) : ViewModel() {

    private val _state = MutableStateFlow(WeightConverterUiState())
    val state: StateFlow<WeightConverterUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        // Wyszukiwanie z opóźnieniem — baza wagowa ma kilkaset wpisów i nie ma
        // sensu odpytywać jej na każdą literę.
        queryFlow
            .debounce(200)
            .distinctUntilChanged()
            .onEach { query ->
                val results = if (query.isBlank()) {
                    popularProducts()
                } else {
                    densityRepository.search(query)
                }
                _state.value = _state.value.copy(results = results)
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            _state.value = _state.value.copy(results = popularProducts())
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        queryFlow.value = query
    }

    fun select(density: IngredientDensity) {
        _state.value = _state.value.copy(
            selected = density,
            quickTable = buildQuickTable(density),
        )
        recalculate()
    }

    fun setAmount(value: String) {
        _state.value = _state.value.copy(amountInput = value)
        recalculate()
    }

    fun setUnit(unit: MeasureUnit) {
        _state.value = _state.value.copy(unit = unit)
        recalculate()
    }

    fun toggleDirection() {
        val next = if (_state.value.direction == ConversionDirection.VOLUME_TO_WEIGHT) {
            ConversionDirection.WEIGHT_TO_VOLUME
        } else {
            ConversionDirection.VOLUME_TO_WEIGHT
        }
        _state.value = _state.value.copy(direction = next)
        recalculate()
    }

    private fun recalculate() {
        val current = _state.value
        val density = current.selected ?: return
        val amount = current.amountInput.replace(',', '.').toDoubleOrNull()

        if (amount == null || amount <= 0.0) {
            _state.value = current.copy(conversion = null, volumeResult = null)
            return
        }

        _state.value = when (current.direction) {
            ConversionDirection.VOLUME_TO_WEIGHT -> current.copy(
                conversion = converter.toGrams(amount, current.unit, density),
                volumeResult = null,
            )

            ConversionDirection.WEIGHT_TO_VOLUME -> current.copy(
                conversion = null,
                volumeResult = converter.toVolume(amount, current.unit, density),
            )
        }
    }

    /**
     * Najczęstsze pytanie w kuchni brzmi „ile to jest szklanka mąki” — dlatego
     * gotowa tabelka jest widoczna od razu po wybraniu produktu.
     */
    private fun buildQuickTable(density: IngredientDensity): List<QuickRow> = buildList {
        converter.toGrams(1.0, MeasureUnit.CUP, density)?.let { add(QuickRow("1 szklanka", it.grams)) }
        converter.toGrams(0.5, MeasureUnit.CUP, density)?.let { add(QuickRow("½ szklanki", it.grams)) }
        converter.toGrams(1.0, MeasureUnit.TABLESPOON, density)?.let { add(QuickRow("1 łyżka", it.grams)) }
        converter.toGrams(1.0, MeasureUnit.TEASPOON, density)?.let { add(QuickRow("1 łyżeczka", it.grams)) }
    }

    private suspend fun popularProducts(): List<IngredientDensity> =
        POPULAR.mapNotNull { name -> densityRepository.matchForIngredient(name) }.distinctBy { it.id }

    private companion object {
        val POPULAR = listOf(
            "mąka pszenna",
            "cukier",
            "masło",
            "mleko",
            "kakao",
            "mąka ziemniaczana",
        )
    }
}
