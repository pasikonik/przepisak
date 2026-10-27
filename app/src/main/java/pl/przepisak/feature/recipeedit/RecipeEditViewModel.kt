package pl.przepisak.feature.recipeedit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.przepisak.core.util.Outcome
import pl.przepisak.data.media.PhotoStore
import pl.przepisak.data.repository.RecipeRepository
import pl.przepisak.data.repository.TaxonomyRepository
import pl.przepisak.domain.model.Category
import pl.przepisak.domain.model.Difficulty
import pl.przepisak.domain.model.Ingredient
import pl.przepisak.domain.model.IngredientGroup
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.PhotoRef
import pl.przepisak.domain.model.Quantity
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.model.Step
import pl.przepisak.domain.model.Tag
import pl.przepisak.domain.scaling.IngredientLineParser
import pl.przepisak.domain.scaling.toIngredient
import javax.inject.Inject

data class RecipeEditUiState(
    val draft: RecipeAggregate? = null,
    val availableCategories: List<Category> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val titleError: Boolean = false,
)

@HiltViewModel
class RecipeEditViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val ingredientLineParser: IngredientLineParser,
    private val photoStore: PhotoStore,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeEditUiState())
    val state: StateFlow<RecipeEditUiState> = _state.asStateFlow()

    private var loadedId: Long? = null

    /**
     * Formularz wczytujemy JEDNORAZOWO, a nie przez obserwowanie bazy.
     * Gdyby stan płynął ze strumienia, zapis w tle albo synchronizacja mogłyby
     * przestawić pola, w których użytkownik właśnie pisze.
     */
    fun load(recipeId: Long) {
        if (loadedId == recipeId) return
        loadedId = recipeId
        viewModelScope.launch {
            val draft = if (recipeId == 0L) {
                recipeRepository.newDraft()
            } else {
                recipeRepository.getRecipe(recipeId) ?: recipeRepository.newDraft()
            }
            _state.value = _state.value.copy(
                draft = draft,
                availableCategories = taxonomyRepository.observeCategories().first(),
                availableTags = taxonomyRepository.observeTags().first(),
                hasUnsavedChanges = false,
            )
        }
    }

    // --- nagłówek ---------------------------------------------------------

    fun setTitle(value: String) = editRecipe { it.copy(title = value) }
    fun setDescription(value: String) = editRecipe { it.copy(description = value.ifBlank { null }) }
    fun setPrepTime(value: String) = editRecipe { it.copy(prepTimeMinutes = value.toIntOrNull()) }
    fun setCookTime(value: String) = editRecipe { it.copy(cookTimeMinutes = value.toIntOrNull()) }
    fun setServings(value: String) = editRecipe { it.copy(servings = value.toIntOrNull()) }
    fun setServingsLabel(value: String) = editRecipe { it.copy(servingsLabel = value.ifBlank { null }) }
    fun setDifficulty(value: Difficulty?) = editRecipe { it.copy(difficulty = value) }
    fun setSourceUrl(value: String) = editRecipe { it.copy(sourceUrl = value.ifBlank { null }) }

    fun setCategory(category: Category?) = edit { draft ->
        draft.copy(category = category, recipe = draft.recipe.copy(categoryId = category?.id))
    }

    fun addTag(name: String) = edit { draft ->
        val trimmed = name.trim()
        if (trimmed.isEmpty() || draft.tags.any { it.name.equals(trimmed, ignoreCase = true) }) {
            draft
        } else {
            draft.copy(tags = draft.tags + Tag(name = trimmed))
        }
    }

    fun removeTag(tag: Tag) = edit { draft -> draft.copy(tags = draft.tags - tag) }

    fun setPhoto(photo: PhotoRef) = editRecipe { it.copy(photo = photo) }

    // --- składniki --------------------------------------------------------

    fun addGroup() = edit { draft ->
        draft.copy(groups = draft.groups + IngredientGroup(position = draft.groups.size))
    }

    fun setGroupName(groupIndex: Int, name: String) = editGroup(groupIndex) {
        it.copy(name = name.ifBlank { null })
    }

    fun removeGroup(groupIndex: Int) = edit { draft ->
        // Zawsze musi zostać przynajmniej jedna grupa — inaczej nie ma gdzie
        // dopisywać składników.
        if (draft.groups.size <= 1) draft else draft.copy(groups = draft.groups.removeAt(groupIndex))
    }

    fun addIngredient(groupIndex: Int) = editGroup(groupIndex) { group ->
        group.copy(
            ingredients = group.ingredients + Ingredient(position = group.ingredients.size, name = ""),
        )
    }

    fun removeIngredient(groupIndex: Int, index: Int) = editGroup(groupIndex) { group ->
        group.copy(ingredients = group.ingredients.removeAt(index))
    }

    fun moveIngredient(groupIndex: Int, index: Int, delta: Int) = editGroup(groupIndex) { group ->
        group.copy(ingredients = group.ingredients.move(index, index + delta))
    }

    fun setIngredientAmount(groupIndex: Int, index: Int, value: String) =
        editIngredient(groupIndex, index) { ingredient ->
            val amount = value.replace(',', '.').toDoubleOrNull()
            ingredient.copy(quantity = amount?.let { Quantity(it) })
        }

    fun setIngredientUnit(groupIndex: Int, index: Int, unit: MeasureUnit?) =
        editIngredient(groupIndex, index) { it.copy(unit = unit) }

    fun setIngredientName(groupIndex: Int, index: Int, value: String) =
        editIngredient(groupIndex, index) { it.copy(name = value) }

    fun setIngredientNote(groupIndex: Int, index: Int, value: String) =
        editIngredient(groupIndex, index) { it.copy(note = value.ifBlank { null }) }

    fun toggleIngredientOptional(groupIndex: Int, index: Int) =
        editIngredient(groupIndex, index) { it.copy(isOptional = !it.isOptional) }

    fun toggleIngredientScalable(groupIndex: Int, index: Int) =
        editIngredient(groupIndex, index) { it.copy(isScalable = !it.isScalable) }

    /**
     * Wklejenie wielu linii naraz. Najszybsza droga do przepisania przepisu
     * z kartki albo ze strony, której nie da się zaimportować.
     */
    fun pasteIngredients(groupIndex: Int, text: String) = editGroup(groupIndex) { group ->
        val parsed = ingredientLineParser.parseMany(text)
        val startPosition = group.ingredients.size
        group.copy(
            ingredients = group.ingredients + parsed.mapIndexed { index, line ->
                line.toIngredient(startPosition + index)
            },
        )
    }

    // --- kroki ------------------------------------------------------------

    fun addStep() = edit { draft ->
        draft.copy(steps = draft.steps + Step(position = draft.steps.size, text = ""))
    }

    fun setStepText(index: Int, value: String) = editStep(index) { it.copy(text = value) }

    fun setStepTimerMinutes(index: Int, value: String) = editStep(index) { step ->
        step.copy(timerSeconds = value.toIntOrNull()?.takeIf { it > 0 }?.times(60))
    }

    fun setStepPhoto(index: Int, photo: PhotoRef) = editStep(index) { it.copy(photo = photo) }

    fun removeStep(index: Int) = edit { draft -> draft.copy(steps = draft.steps.removeAt(index)) }

    fun moveStep(index: Int, delta: Int) = edit { draft ->
        draft.copy(steps = draft.steps.move(index, index + delta))
    }

    // --- zdjęcia i zapis --------------------------------------------------

    /**
     * Zapisuje wybrane zdjęcie i podstawia je pod wskazane miejsce.
     *
     * Usunięcie zdjęcia z formularza nie kasuje pliku od razu — użytkownik może
     * jeszcze anulować edycję. Osierocone pliki sprząta `PhotoStore.deleteOrphans`.
     */
    fun attachPhoto(uri: Uri, stepIndex: Int? = null) {
        viewModelScope.launch {
            when (val result = photoStore.saveFromUri(uri)) {
                is Outcome.Failure -> Unit
                is Outcome.Success -> {
                    val photo = PhotoRef(localFileName = result.value)
                    if (stepIndex == null) setPhoto(photo) else setStepPhoto(stepIndex, photo)
                }
            }
        }
    }

    /** Composable nie ma dostępu do PhotoStore, więc URI dla aparatu podaje ViewModel. */
    suspend fun prepareCameraUri(): Uri = photoStore.createCameraOutput().first

    fun save(onSaved: (Long) -> Unit) {
        val draft = _state.value.draft ?: return
        if (draft.recipe.title.isBlank()) {
            _state.value = _state.value.copy(titleError = true)
            return
        }
        _state.value = _state.value.copy(isSaving = true, titleError = false)
        viewModelScope.launch {
            val id = recipeRepository.saveRecipe(draft)
            _state.value = _state.value.copy(isSaving = false, hasUnsavedChanges = false)
            onSaved(id)
        }
    }

    // --- pomocnicze -------------------------------------------------------

    private fun edit(transform: (RecipeAggregate) -> RecipeAggregate) {
        val draft = _state.value.draft ?: return
        _state.value = _state.value.copy(draft = transform(draft), hasUnsavedChanges = true)
    }

    private fun editRecipe(transform: (pl.przepisak.domain.model.Recipe) -> pl.przepisak.domain.model.Recipe) =
        edit { draft -> draft.copy(recipe = transform(draft.recipe)) }

    private fun editGroup(groupIndex: Int, transform: (IngredientGroup) -> IngredientGroup) = edit { draft ->
        if (groupIndex !in draft.groups.indices) {
            draft
        } else {
            draft.copy(
                groups = draft.groups.mapIndexed { index, group ->
                    if (index == groupIndex) transform(group) else group
                },
            )
        }
    }

    private fun editIngredient(
        groupIndex: Int,
        index: Int,
        transform: (Ingredient) -> Ingredient,
    ) = editGroup(groupIndex) { group ->
        if (index !in group.ingredients.indices) {
            group
        } else {
            group.copy(
                ingredients = group.ingredients.mapIndexed { position, ingredient ->
                    if (position == index) transform(ingredient) else ingredient
                },
            )
        }
    }

    private fun editStep(index: Int, transform: (Step) -> Step) = edit { draft ->
        if (index !in draft.steps.indices) {
            draft
        } else {
            draft.copy(
                steps = draft.steps.mapIndexed { position, step ->
                    if (position == index) transform(step) else step
                },
            )
        }
    }

    private fun <T> List<T>.removeAt(index: Int): List<T> =
        if (index in indices) filterIndexed { position, _ -> position != index } else this

    private fun <T> List<T>.move(from: Int, to: Int): List<T> {
        if (from !in indices || to !in indices || from == to) return this
        val mutable = toMutableList()
        mutable.add(to, mutable.removeAt(from))
        return mutable
    }
}
