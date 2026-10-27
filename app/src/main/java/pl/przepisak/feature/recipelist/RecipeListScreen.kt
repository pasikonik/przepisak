package pl.przepisak.feature.recipelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DensitySmall
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.przepisak.R
import pl.przepisak.core.ui.component.EmptyState
import pl.przepisak.core.ui.component.LoadingState
import pl.przepisak.core.ui.component.RecipeCard
import pl.przepisak.core.ui.component.RecipeCompactRow
import pl.przepisak.core.ui.component.RecipeGridCard
import pl.przepisak.core.ui.component.SectionHeader
import pl.przepisak.core.ui.component.TextInputDialog
import pl.przepisak.core.ui.component.labelRes
import pl.przepisak.data.prefs.RecipeViewMode
import pl.przepisak.domain.model.Difficulty
import pl.przepisak.domain.model.RecipeSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    onOpenRecipe: (Long) -> Unit,
    onAddRecipe: () -> Unit,
    onImportText: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTimers: () -> Unit,
    viewModel: RecipeListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastDeleted by viewModel.lastDeletedId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showAddChooser by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }

    val deletedMessage = stringResource(R.string.list_deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    val clipboardEmptyMessage = stringResource(R.string.list_paste_empty)

    LaunchedEffect(lastDeleted) {
        val id = lastDeleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.consumeDeleted()
    }

    // Rząd kategorii i rząd Ulubione/Sortuj/Filtry chowają się przy przewijaniu jak pasek
    // adresu w przeglądarce (zob. CollapsingHeaderState); górny pasek z tytułem zostaje.
    //
    // To NIE jest `TopAppBarScrollBehavior` — ten wymaga podpięcia do prawdziwego
    // TopAppBar, żeby zmierzyć swój zakres przewijania (`heightOffsetLimit`); bez
    // tego połyka całe przewijanie, tylko nic z nim nie robi — a lista wygląda na
    // zablokowaną. CollapsingHeaderState mierzy pasek sam.
    val filtersHeader = remember { CollapsingHeaderState() }
    LaunchedEffect(state.filter, state.viewMode) { filtersHeader.show() }

    Scaffold(
        modifier = Modifier.nestedScroll(filtersHeader.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = state.filter.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text(stringResource(R.string.list_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(stringResource(R.string.list_title))
                    }
                },
                navigationIcon = {
                    if (searchActive) {
                        IconButton(
                            onClick = {
                                searchActive = false
                                viewModel.setQuery("")
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (searchActive) {
                        if (state.filter.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.list_search_hint))
                        }
                        IconButton(onClick = { viewModel.setViewMode(state.viewMode.next()) }) {
                            Icon(
                                imageVector = state.viewMode.next().icon(),
                                contentDescription = stringResource(R.string.list_toggle_view),
                            )
                        }
                        IconButton(onClick = onOpenTimers) {
                            BadgedBox(
                                badge = {
                                    if (state.activeTimerCount > 0) {
                                        Badge { Text(state.activeTimerCount.toString()) }
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.Timer, contentDescription = stringResource(R.string.timers_title))
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddChooser = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.list_add_recipe))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Chipy bez wymuszonych 48 dp na dotyk: paski mają zajmować mało miejsca,
            // a szeroki chip i tak trudno chybić.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(modifier = Modifier.collapsingHeader(filtersHeader)) {
                    if (state.categories.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CompactFilterChip(
                                selected = state.filter.categoryIds.isEmpty(),
                                onClick = viewModel::clearCategoryFilter,
                                label = stringResource(R.string.list_all_categories),
                            )
                            state.categories.forEach { category ->
                                val count = state.categoryUsage[category.id] ?: 0
                                CompactFilterChip(
                                    selected = category.id in state.filter.categoryIds,
                                    onClick = { viewModel.toggleCategory(category.id) },
                                    label = if (count > 0) "${category.name} ($count)" else category.name,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CompactFilterChip(
                            selected = state.filter.onlyFavorites,
                            onClick = viewModel::toggleFavoritesOnly,
                            label = stringResource(R.string.list_only_favorites),
                        )
                        Box {
                            CompactFilterChip(
                                selected = false,
                                onClick = { sortMenuOpen = true },
                                label = stringResource(state.filter.sort.labelRes()),
                            )
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                RecipeSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(sort.labelRes())) },
                                        onClick = {
                                            viewModel.setSort(sort)
                                            sortMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        CompactFilterChip(
                            selected = state.filter.hasActiveFilters,
                            onClick = { showFilters = true },
                            label = stringResource(R.string.list_filters),
                            leadingIcon = Icons.Outlined.FilterList,
                        )
                    }
                }
            }

            when {
                state.isLoading -> LoadingState()

                state.recipes.isEmpty() && state.hasAnyFilter -> EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.list_no_results_title),
                    message = stringResource(R.string.list_no_results_message),
                    actionLabel = stringResource(R.string.list_no_results_action),
                    onAction = viewModel::clearFilters,
                )

                state.recipes.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.MenuBook,
                    title = stringResource(R.string.list_empty_title),
                    message = stringResource(R.string.list_empty_message),
                    actionLabel = stringResource(R.string.list_empty_action),
                    onAction = onAddRecipe,
                )

                state.viewMode == RecipeViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(items = state.recipes, key = { it.id }) { item ->
                        RecipeGridCard(item = item, onClick = { onOpenRecipe(item.id) })
                    }
                }

                state.viewMode == RecipeViewMode.COMPACT -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    items(items = state.recipes, key = { it.id }) { item ->
                        RecipeCompactRow(item = item, onClick = { onOpenRecipe(item.id) })
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = state.recipes, key = { it.id }) { item ->
                        RecipeCard(
                            item = item,
                            onClick = { onOpenRecipe(item.id) },
                            trailingAction = {
                                RecipeCardMenu(onDelete = { viewModel.deleteRecipe(item.id) })
                            },
                        )
                    }
                }
            }
        }

        if (showFilters) {
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                FilterSheetContent(
                    state = state,
                    onToggleTag = viewModel::toggleTag,
                    onToggleDifficulty = viewModel::toggleDifficulty,
                    onSetMaxTime = viewModel::setMaxTime,
                    onSetMinRating = viewModel::setMinRating,
                    onClear = viewModel::clearFilters,
                    onApply = { showFilters = false },
                )
            }
        }

        if (showAddChooser) {
            // Bez limitu szerokości: domyślnie arkusz ma najwyżej 640 dp i na szerokim
            // ekranie (poziomo, tablet) stoi na środku — a opcje mają być przy lewej krawędzi.
            ModalBottomSheet(
                onDismissRequest = { showAddChooser = false },
                sheetMaxWidth = Dp.Unspecified,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(
                        stringResource(R.string.list_add_title),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    AddOptionRow(Icons.Outlined.Edit, stringResource(R.string.list_add_manual)) {
                        showAddChooser = false
                        onAddRecipe()
                    }
                    AddOptionRow(Icons.Outlined.ContentPaste, stringResource(R.string.list_add_paste)) {
                        scope.launch {
                            // Czytamy, zanim arkusz zniknie: od Androida 10 schowek jest
                            // dostępny tylko dla aplikacji, której okno ma akurat fokus.
                            val text = clipboard.getClipEntry()?.clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.text?.toString()?.trim()
                            showAddChooser = false
                            if (text.isNullOrEmpty()) {
                                snackbarHostState.showSnackbar(clipboardEmptyMessage)
                            } else {
                                onImportText(text)
                            }
                        }
                    }
                    AddOptionRow(Icons.Outlined.AutoAwesome, stringResource(R.string.list_add_import)) {
                        showAddChooser = false
                        showImportDialog = true
                    }
                }
            }
        }

        if (showImportDialog) {
            TextInputDialog(
                title = stringResource(R.string.import_paste_title),
                label = stringResource(R.string.import_paste_label),
                singleLine = false,
                confirmLabel = stringResource(R.string.import_paste_confirm),
                onConfirm = { text ->
                    showImportDialog = false
                    if (text.isNotBlank()) onImportText(text.trim())
                },
                onDismiss = { showImportDialog = false },
            )
        }
    }
}

/** Niski chip do pasków nad listą: 28 dp zamiast domyślnych 32 dp i mniejsza czcionka. */
@Composable
private fun CompactFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
        },
        modifier = Modifier.height(28.dp),
    )
}

/** Opcja arkusza „Dodaj przepis” — ikona w jednej linii z nagłówkiem (oba 20 dp od lewej). */
@Composable
private fun AddOptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(label)
    }
}

/**
 * Usuwanie schowane w menu, a nie pod przesunięciem karty.
 * W kuchni, mokrą ręką, gest przesunięcia zdarza się przypadkiem — a przypadkowe
 * usunięcie przepisu to najgorsze, co ta aplikacja może zrobić użytkownikowi.
 */
@Composable
private fun RecipeCardMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun FilterSheetContent(
    state: RecipeListUiState,
    onToggleTag: (Long) -> Unit,
    onToggleDifficulty: (Difficulty) -> Unit,
    onSetMaxTime: (Int?) -> Unit,
    onSetMinRating: (Int?) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        item {
            SectionHeader(stringResource(R.string.filter_tags))
            ChipFlowRow {
                state.tags.forEach { tag ->
                    val count = state.tagUsage[tag.id] ?: 0
                    FilterChip(
                        selected = tag.id in state.filter.tagIds,
                        onClick = { onToggleTag(tag.id) },
                        label = { Text(if (count > 0) "${tag.name} ($count)" else tag.name) },
                    )
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.difficulty_label))
            ChipFlowRow {
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = difficulty in state.filter.difficulties,
                        onClick = { onToggleDifficulty(difficulty) },
                        label = { Text(stringResource(difficulty.labelRes())) },
                    )
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.filter_max_time))
            ChipFlowRow {
                FilterChip(
                    selected = state.filter.maxTotalTimeMinutes == null,
                    onClick = { onSetMaxTime(null) },
                    label = { Text(stringResource(R.string.filter_max_time_any)) },
                )
                listOf(15, 30, 45, 60, 90, 120, 180).forEach { minutes ->
                    FilterChip(
                        selected = state.filter.maxTotalTimeMinutes == minutes,
                        onClick = { onSetMaxTime(minutes) },
                        label = { Text("$minutes min") },
                    )
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.filter_min_rating))
            ChipFlowRow {
                FilterChip(
                    selected = state.filter.minRating == null,
                    onClick = { onSetMinRating(null) },
                    label = { Text("—") },
                )
                (3..5).forEach { rating ->
                    FilterChip(
                        selected = state.filter.minRating == rating,
                        onClick = { onSetMinRating(rating) },
                        label = { Text("$rating+") },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = onApply) { Text(stringResource(R.string.filter_apply)) }
            }
        }
    }
}

/**
 * Prosty zawijany rząd chipów.
 *
 * `FlowRow` w Compose 1.8 jest oznaczony jako eksperymentalny; poziomy przewijany
 * rząd jest tu wystarczający i nie wiąże nas z niestabilnym API.
 */
@Composable
private fun ChipFlowRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

private fun RecipeSort.labelRes(): Int = when (this) {
    RecipeSort.RECENT -> R.string.sort_recent
    RecipeSort.TITLE_ASC -> R.string.sort_title
    RecipeSort.TIME_ASC -> R.string.sort_time
    RecipeSort.RATING_DESC -> R.string.sort_rating
}

/** Kolejność cyklu przełącznika widoku: lista → siatka → super kompaktowy → lista. */
private fun RecipeViewMode.next(): RecipeViewMode = when (this) {
    RecipeViewMode.LIST -> RecipeViewMode.GRID
    RecipeViewMode.GRID -> RecipeViewMode.COMPACT
    RecipeViewMode.COMPACT -> RecipeViewMode.LIST
}

/** Ikona pokazuje docelowy widok (na co się przełączysz), nie bieżący. */
private fun RecipeViewMode.icon() = when (this) {
    RecipeViewMode.LIST -> Icons.Outlined.ViewList
    RecipeViewMode.GRID -> Icons.Outlined.GridView
    RecipeViewMode.COMPACT -> Icons.Outlined.DensitySmall
}
