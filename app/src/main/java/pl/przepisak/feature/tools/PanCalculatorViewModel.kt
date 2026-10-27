package pl.przepisak.feature.tools

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import pl.przepisak.domain.model.BakingPan
import pl.przepisak.domain.model.PanConversion
import pl.przepisak.domain.scaling.PanCalculator
import javax.inject.Inject

data class PanCalculatorUiState(
    val source: BakingPan = BakingPan.round(20.0),
    val target: BakingPan = BakingPan.round(24.0),
    val conversion: PanConversion? = null,
)

@HiltViewModel
class PanCalculatorViewModel @Inject constructor() : ViewModel() {

    private val input = MutableStateFlow(
        // Startujemy od dwóch najpopularniejszych tortownic — użytkownik zwykle
        // przelicza właśnie między nimi.
        PanCalculatorUiState(
            source = BakingPan.round(20.0),
            target = BakingPan.round(24.0),
        ),
    )

    val state: StateFlow<PanCalculatorUiState> = input
        .map { current -> current.copy(conversion = PanCalculator.convert(current.source, current.target)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), input.value)

    fun setSource(pan: BakingPan) {
        input.value = input.value.copy(source = pan)
    }

    fun setTarget(pan: BakingPan) {
        input.value = input.value.copy(target = pan)
    }
}
