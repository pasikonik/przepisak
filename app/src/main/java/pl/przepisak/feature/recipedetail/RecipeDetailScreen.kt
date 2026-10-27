package pl.przepisak.feature.recipedetail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.przepisak.R
import pl.przepisak.core.ui.component.ConfirmDialog
import pl.przepisak.core.ui.component.DifficultyBadge
import pl.przepisak.core.ui.component.LoadingState
import pl.przepisak.core.ui.component.RatingStars
import pl.przepisak.core.ui.component.RecipePhoto
import pl.przepisak.core.ui.component.SectionHeader
import pl.przepisak.core.ui.component.ServingsBadge
import pl.przepisak.core.ui.component.TagChipRow
import pl.przepisak.core.ui.component.TextInputDialog
import pl.przepisak.core.ui.component.TimeBadge
import pl.przepisak.domain.model.Ingredient
import pl.przepisak.domain.scaling.QuantityFormatter

@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCook: (Long, Int) -> Unit,
    onOpenPanCalculator: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId) { viewModel.load(recipeId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val aggregate = state.recipe
    val timerStartedTemplate = stringResource(R.string.detail_timer_started)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (aggregate != null) {
                Button(
                    onClick = { onCook(aggregate.recipe.id, state.targetServings ?: 0) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.detail_cook))
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))

            aggregate == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.detail_not_found))
            }

            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Box {
                        RecipePhoto(
                            photo = aggregate.recipe.photo,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                        FilledIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(12.dp).size(40.dp),
                        ) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                            FilledIconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_edit)) },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onEdit(aggregate.recipe.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_duplicate)) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.duplicate { }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_pan_calculator)) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenPanCalculator()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_share)) },
                                    onClick = {
                                        menuOpen = false
                                        shareRecipe(context, aggregate.asPlainText())
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = {
                                        menuOpen = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        // Serce przy tytule, a nie na zdjęciu — zdjęcie ma być widać w całości.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = aggregate.recipe.title,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f),
                            )
                            val isFavorite = aggregate.recipe.isFavorite
                            IconButton(
                                onClick = viewModel::toggleFavorite,
                                // O wewnętrzny margines przycisku w prawo: serce równa do krawędzi treści.
                                modifier = Modifier.offset(x = 12.dp),
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = stringResource(
                                        if (isFavorite) R.string.action_unfavorite else R.string.action_favorite,
                                    ),
                                    tint = if (isFavorite) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            TimeBadge(aggregate.recipe.totalTimeMinutes)
                            ServingsBadge(aggregate.recipe.servings, aggregate.recipe.servingsLabel)
                            DifficultyBadge(aggregate.recipe.difficulty)
                            aggregate.category?.let {
                                Text(it.name, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        RatingStars(
                            rating = aggregate.recipe.rating,
                            onRatingChange = viewModel::setRating,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        if (aggregate.tags.isNotEmpty()) {
                            TagChipRow(
                                tags = aggregate.tags.map { it.name },
                                maxVisible = aggregate.tags.size,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        aggregate.recipe.description?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }

                item {
                    ServingsScaler(
                        originalServings = aggregate.recipe.servings,
                        targetServings = state.targetServings,
                        factor = state.factor,
                        onTargetChange = viewModel::setTargetServings,
                        onMultiplier = viewModel::applyMultiplier,
                        onReset = viewModel::resetScale,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                item {
                    SectionHeader(
                        text = stringResource(R.string.detail_ingredients),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                aggregate.groups.forEach { group ->
                    if (!group.isDefault) {
                        item(key = "group-${group.id}-${group.position}") {
                            Text(
                                text = group.name.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(group.ingredients.size) { index ->
                        val ingredient = group.ingredients[index]
                        IngredientRow(
                            ingredient = ingredient,
                            checked = ingredient.id in state.checkedIngredientIds,
                            onToggle = { viewModel.toggleIngredientChecked(ingredient.id) },
                        )
                    }
                }

                item {
                    SectionHeader(
                        text = stringResource(R.string.detail_steps),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                items(aggregate.steps.size) { index ->
                    val step = aggregate.steps[index]
                    StepRow(
                        number = index + 1,
                        text = step.text,
                        timers = state.detectedTimers[step.position].orEmpty(),
                        onStartTimer = { detected ->
                            viewModel.startTimer(detected.label, detected.seconds, step.position)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    timerStartedTemplate.format(detected.label),
                                )
                            }
                        },
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(
                        text = stringResource(R.string.detail_notes),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Text(
                        text = aggregate.recipe.notes ?: stringResource(R.string.detail_notes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (aggregate.recipe.notes == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNotesDialog = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                aggregate.recipe.sourceUrl?.let { url ->
                    item {
                        SectionHeader(
                            text = stringResource(R.string.detail_source),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Text(
                            text = aggregate.recipe.sourceName ?: url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable { openUrl(context, url) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog && aggregate != null) {
        ConfirmDialog(
            title = stringResource(R.string.filter_delete_confirm_title),
            text = stringResource(R.string.filter_delete_confirm_text, aggregate.recipe.title),
            confirmLabel = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete(onBack)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showNotesDialog && aggregate != null) {
        TextInputDialog(
            title = stringResource(R.string.detail_notes),
            initialValue = aggregate.recipe.notes.orEmpty(),
            singleLine = false,
            onConfirm = {
                viewModel.saveNotes(it)
                showNotesDialog = false
            },
            onDismiss = { showNotesDialog = false },
        )
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Bez onCheckedChange pole nie rezerwuje sobie 48 dp na dotyk, więc wiersz może
        // być niski — a odhacza się go dotknięciem w dowolnym miejscu wiersza.
        Checkbox(checked = checked, onCheckedChange = null)
        Column {
            val amount = QuantityFormatter.format(ingredient.quantity, ingredient.unit, ingredient.unitLabel)
            Text(
                text = if (amount.isBlank()) ingredient.name else "$amount ${ingredient.name}",
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                fontStyle = if (ingredient.isOptional) FontStyle.Italic else FontStyle.Normal,
            )
            ingredient.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    number: Int,
    text: String,
    timers: List<pl.przepisak.domain.model.DetectedTimer>,
    onStartTimer: (pl.przepisak.domain.model.DetectedTimer) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.detail_step_number, number),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        if (timers.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                timers.forEach { detected ->
                    AssistChip(
                        onClick = { onStartTimer(detected) },
                        label = { Text(detected.label) },
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                    )
                }
            }
        }
    }
}

/** Otwiera adres w przeglądarce; brak przeglądarki nie może wywalić aplikacji. */
private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, R.string.detail_no_browser, Toast.LENGTH_SHORT).show()
    }
}

private fun shareRecipe(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
    }
}

/** Czytelna wersja tekstowa przepisu do udostępnienia. */
private fun pl.przepisak.domain.model.RecipeAggregate.asPlainText(): String = buildString {
    appendLine(recipe.title)
    recipe.description?.let { appendLine(it) }
    appendLine()
    appendLine("SKŁADNIKI:")
    groups.forEach { group ->
        if (!group.isDefault) appendLine("* ${group.name}")
        group.ingredients.forEach { ingredient ->
            val amount = QuantityFormatter.format(ingredient.quantity, ingredient.unit, ingredient.unitLabel)
            appendLine("- ${if (amount.isBlank()) ingredient.name else "$amount ${ingredient.name}"}")
        }
    }
    appendLine()
    appendLine("PRZYGOTOWANIE:")
    steps.forEachIndexed { index, step -> appendLine("${index + 1}. ${step.text}") }
    recipe.sourceUrl?.let {
        appendLine()
        appendLine("Źródło: $it")
    }
}
