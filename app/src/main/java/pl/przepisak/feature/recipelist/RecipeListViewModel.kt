package pl.przepisak.feature.recipelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.przepisak.data.prefs.RecipeViewMode
import pl.przepisak.data.prefs.SettingsRepository
import pl.przepisak.data.repository.RecipeRepository
import pl.przepisak.data.repository.TaxonomyRepository
import pl.przepisak.data.repository.TimerRepository
import pl.przepisak.domain.model.Category
import pl.przepisak.domain.model.Difficulty
import pl.przepisak.domain.model.RecipeFilter
import pl.przepisak.domain.model.RecipeListItem
import pl.przepisak.domain.model.RecipeSort
import pl.przepisak.domain.model.Tag
import javax.inject.Inject

data class RecipeListUiState(
    val recipes: List<RecipeListItem> = emptyList(),
    val filter: RecipeFilter = RecipeFilter(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val categoryUsage: Map<Long, Int> = emptyMap(),
    val tagUsage: Map<Long, Int> = emptyMap(),
    val activeTimerCount: Int = 0,
    val viewMode: RecipeViewMode = RecipeViewMode.LIST,
    val isLoading: Boolean = true,
) {
    /** Rozróżnienie ważne dla pustego stanu: „nic nie masz” to nie to samo co „nic nie pasuje”. */
    val hasAnyFilter: Boolean get() = !filter.isEmpty
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
    taxonomyRepository: TaxonomyRepository,
    timerRepository: TimerRepository,
) : ViewModel() {

    /** Tekst wpisywany w polu wyszukiwania — natychmiast widoczny w UI. */
    private val queryInput = MutableStateFlow("")

    /** Pozostałe kryteria; zmieniają się skokowo, więc nie wymagają opóźnienia. */
    private val criteria = MutableStateFlow(RecipeFilter())

    /**
     * Zapytanie tekstowe opóźniamy o 250 ms.
     *
     * Bez tego każda wpisana litera odpalałaby zapytanie pełnotekstowe do bazy,
     * a ostatnie z nich i tak unieważniałoby wcześniejsze wyniki.
     */
    private val effectiveFilter: StateFlow<RecipeFilter> = combine(
        queryInput.debounce { text -> if (text.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .distinctUntilChanged(),
        criteria,
    ) { query, filter -> filter.copy(query = query) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecipeFilter())

    private val recipes = effectiveFilter.flatMapLatest { filter ->
        recipeRepository.observeRecipes(filter)
    }

    val state: StateFlow<RecipeListUiState> = combine(
        recipes,
        // Filtr do UI składamy z pola tekstowego bez opóźnienia, żeby litery
        // pojawiały się natychmiast, nawet gdy zapytanie jeszcze nie poszło.
        combine(queryInput, criteria) { query, filter -> filter.copy(query = query) },
        taxonomyRepository.observeCategories(),
        taxonomyRepository.observeTags(),
        combine(
            taxonomyRepository.observeCategoryUsage(),
            taxonomyRepository.observeTagUsage(),
            timerRepository.observeActive().map { it.size },
            settingsRepository.settings.map { it.recipeViewMode },
        ) { categoryUsage, tagUsage, timerCount, viewMode -> Extras(categoryUsage, tagUsage, timerCount, viewMode) },
    ) { items, filter, categories, tags, extras ->
        RecipeListUiState(
            recipes = items,
            filter = filter,
            // Rząd szybkiego filtra ma sens tylko dla kategorii, które faktycznie
            // czegoś przechowują — pusta kategoria i tak nic by nie pokazała.
            categories = categories.filter { (extras.categoryUsage[it.id] ?: 0) > 0 },
            tags = tags,
            categoryUsage = extras.categoryUsage,
            tagUsage = extras.tagUsage,
            activeTimerCount = extras.activeTimerCount,
            viewMode = extras.viewMode,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState())

    /** Ostatnio usunięty przepis — pozwala cofnąć operację z paska Snackbar. */
    private val _lastDeletedId = MutableStateFlow<Long?>(null)
    val lastDeletedId: StateFlow<Long?> = _lastDeletedId

    fun setQuery(query: String) {
        queryInput.value = query
    }

    fun setSort(sort: RecipeSort) = updateCriteria { it.copy(sort = sort) }

    /** Kategorie w szybkim rzędzie są single-select: wybór zastępuje poprzedni, powtórny wybór czyści. */
    fun toggleCategory(categoryId: Long) = updateCriteria { filter ->
        filter.copy(categoryIds = if (filter.categoryIds == setOf(categoryId)) emptySet() else setOf(categoryId))
    }

    fun toggleTag(tagId: Long) = updateCriteria { filter ->
        filter.copy(tagIds = filter.tagIds.toggle(tagId))
    }

    fun toggleDifficulty(difficulty: Difficulty) = updateCriteria { filter ->
        filter.copy(difficulties = filter.difficulties.toggle(difficulty))
    }

    fun toggleFavoritesOnly() = updateCriteria { it.copy(onlyFavorites = !it.onlyFavorites) }

    fun setMaxTime(minutes: Int?) = updateCriteria { it.copy(maxTotalTimeMinutes = minutes) }

    fun setMinRating(rating: Int?) = updateCriteria { it.copy(minRating = rating) }

    fun clearFilters() {
        queryInput.value = ""
        criteria.value = RecipeFilter(sort = criteria.value.sort)
    }

    fun deleteRecipe(recipeId: Long) {
        viewModelScope.launch {
            recipeRepository.deleteRecipe(recipeId)
            _lastDeletedId.value = recipeId
        }
    }

    fun undoDelete() {
        val id = _lastDeletedId.value ?: return
        viewModelScope.launch {
            recipeRepository.restoreRecipe(id)
            _lastDeletedId.value = null
        }
    }

    fun consumeDeleted() {
        _lastDeletedId.value = null
    }

    fun setViewMode(mode: RecipeViewMode) {
        viewModelScope.launch { settingsRepository.setRecipeViewMode(mode) }
    }

    fun clearCategoryFilter() = updateCriteria { it.copy(categoryIds = emptySet()) }

    private fun updateCriteria(transform: (RecipeFilter) -> RecipeFilter) {
        criteria.value = transform(criteria.value)
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (contains(value)) this - value else this + value

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

/** Pola stanu, które zmieniają się rzadko i nie zależą od siebie nawzajem — tylko po to, by `combine` miał jeden argument zamiast trzech osobnych flow. */
private data class Extras(
    val categoryUsage: Map<Long, Int>,
    val tagUsage: Map<Long, Int>,
    val activeTimerCount: Int,
    val viewMode: RecipeViewMode,
)
