package pl.przepisak.feature.importflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.Outcome
import pl.przepisak.data.repository.ImportRepository
import pl.przepisak.data.repository.RecipeRepository
import pl.przepisak.domain.model.ImportedRecipe
import javax.inject.Inject

/** Etap importu — pokazywany użytkownikowi, bo całość może zająć kilkanaście sekund. */
enum class ImportStage { FETCHING, PARSING, ASKING_MODEL, SAVING }

data class ImportPreviewUiState(
    val isLoading: Boolean = true,
    val stage: ImportStage = ImportStage.FETCHING,
    val imported: ImportedRecipe? = null,
    val error: AppError? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class ImportPreviewViewModel @Inject constructor(
    private val importRepository: ImportRepository,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportPreviewUiState())
    val state: StateFlow<ImportPreviewUiState> = _state.asStateFlow()

    private var startedFor: String? = null

    fun start(url: String) {
        if (startedFor == url) return
        startedFor = url

        _state.value = ImportPreviewUiState(isLoading = true, stage = ImportStage.FETCHING)
        viewModelScope.launch {
            // Etapów nie da się śledzić dokładnie bez przeciekania szczegółów
            // importera do UI, więc pokazujemy je zgrubnie: pobieranie, potem
            // rozpoznawanie. To wystarcza, by ekran nie wyglądał na zawieszony.
            _state.value = _state.value.copy(stage = ImportStage.PARSING)
            // importFromText sam rozpoznaje, czy to link (wtedy zachowuje się jak
            // importFromUrl), czy wklejony wolny tekst przepisu (wtedy leci przez
            // Gemini) — ten sam ekran obsługuje oba wejścia z Udostępnij i z „+".
            when (val result = importRepository.importFromText(url)) {
                is Outcome.Success -> _state.value = ImportPreviewUiState(
                    isLoading = false,
                    imported = result.value,
                )

                is Outcome.Failure -> _state.value = ImportPreviewUiState(
                    isLoading = false,
                    error = result.error,
                )
            }
        }
    }

    fun retry() {
        val url = startedFor ?: return
        startedFor = null
        start(url)
    }

    fun save(onSaved: (Long) -> Unit) {
        val imported = _state.value.imported ?: return
        _state.value = _state.value.copy(isSaving = true, stage = ImportStage.SAVING)
        viewModelScope.launch {
            val aggregate = importRepository.toAggregate(imported)
            val id = recipeRepository.saveRecipe(aggregate)
            _state.value = _state.value.copy(isSaving = false)
            onSaved(id)
        }
    }
}
