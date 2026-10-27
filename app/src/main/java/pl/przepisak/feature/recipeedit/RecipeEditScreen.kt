package pl.przepisak.feature.recipeedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.przepisak.R
import pl.przepisak.core.ui.component.ConfirmDialog
import pl.przepisak.core.ui.component.DropdownField
import pl.przepisak.core.ui.component.LoadingState
import pl.przepisak.core.ui.component.SectionHeader
import pl.przepisak.core.ui.component.TextInputDialog
import pl.przepisak.core.ui.component.labelRes
import pl.przepisak.domain.model.Difficulty
import pl.przepisak.domain.model.PhotoRef
import pl.przepisak.domain.scaling.QuantityFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    recipeId: Long,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
    viewModel: RecipeEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId) { viewModel.load(recipeId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pasteTargetGroup by remember { mutableStateOf<Int?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }

    val tryCancel = {
        if (state.hasUnsavedChanges) showDiscardDialog = true else onCancel()
    }

    BackHandler(enabled = true) { tryCancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (recipeId == 0L) R.string.edit_title_new else R.string.edit_title_existing,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = tryCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSaved) },
                        enabled = !state.isSaving,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (draft == null) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PhotoPickerButton(
                    photo = draft.recipe.photo,
                    onPicked = { viewModel.attachPhoto(it) },
                    onRemove = { viewModel.setPhoto(PhotoRef.EMPTY) },
                    onRequestCameraUri = { viewModel.prepareCameraUri() },
                )
            }

            item { SectionHeader(stringResource(R.string.edit_section_basics)) }

            item {
                OutlinedTextField(
                    value = draft.recipe.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text(stringResource(R.string.edit_field_title)) },
                    isError = state.titleError,
                    supportingText = if (state.titleError) {
                        { Text(stringResource(R.string.edit_error_title_required)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = draft.recipe.description.orEmpty(),
                    onValueChange = viewModel::setDescription,
                    label = { Text(stringResource(R.string.edit_field_description)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.recipe.prepTimeMinutes?.toString().orEmpty(),
                        onValueChange = viewModel::setPrepTime,
                        label = { Text(stringResource(R.string.edit_field_prep_time)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.recipe.cookTimeMinutes?.toString().orEmpty(),
                        onValueChange = viewModel::setCookTime,
                        label = { Text(stringResource(R.string.edit_field_cook_time)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.recipe.servings?.toString().orEmpty(),
                        onValueChange = viewModel::setServings,
                        label = { Text(stringResource(R.string.edit_field_servings)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.recipe.servingsLabel.orEmpty(),
                        onValueChange = viewModel::setServingsLabel,
                        label = { Text(stringResource(R.string.edit_field_servings_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                DropdownField(
                    value = draft.recipe.difficulty,
                    options = Difficulty.entries,
                    label = stringResource(R.string.difficulty_label),
                    itemLabel = { difficulty -> difficultyLabel(difficulty) },
                    onSelect = viewModel::setDifficulty,
                )
            }

            item {
                DropdownField(
                    value = draft.category,
                    options = state.availableCategories,
                    label = stringResource(R.string.edit_field_category),
                    itemLabel = { it.name },
                    onSelect = viewModel::setCategory,
                )
            }

            item {
                Column {
                    SectionHeader(
                        text = stringResource(R.string.edit_field_tags),
                        action = {
                            TextButton(onClick = { showTagDialog = true }) {
                                Text(stringResource(R.string.edit_add_tag))
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        draft.tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeTag(tag) },
                                label = { Text(tag.name) },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Close, contentDescription = null)
                                },
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = draft.recipe.sourceUrl.orEmpty(),
                    onValueChange = viewModel::setSourceUrl,
                    label = { Text(stringResource(R.string.edit_field_source)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // --- składniki ---
            draft.groups.forEachIndexed { groupIndex, group ->
                item(key = "group-header-$groupIndex") {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = group.name.orEmpty(),
                                onValueChange = { viewModel.setGroupName(groupIndex, it) },
                                label = { Text(stringResource(R.string.edit_group_name)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            if (draft.groups.size > 1) {
                                IconButton(onClick = { viewModel.removeGroup(groupIndex) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.action_delete),
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { viewModel.addIngredient(groupIndex) },
                                label = { Text(stringResource(R.string.edit_add_ingredient)) },
                                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            )
                            AssistChip(
                                onClick = { pasteTargetGroup = groupIndex },
                                label = { Text(stringResource(R.string.edit_paste_ingredients)) },
                            )
                        }
                    }
                }

                items(group.ingredients.size, key = { index -> "ing-$groupIndex-$index" }) { index ->
                    IngredientEditor(
                        ingredient = group.ingredients[index],
                        canMoveUp = index > 0,
                        canMoveDown = index < group.ingredients.lastIndex,
                        onAmountChange = { viewModel.setIngredientAmount(groupIndex, index, it) },
                        onUnitChange = { viewModel.setIngredientUnit(groupIndex, index, it) },
                        onNameChange = { viewModel.setIngredientName(groupIndex, index, it) },
                        onNoteChange = { viewModel.setIngredientNote(groupIndex, index, it) },
                        onToggleOptional = { viewModel.toggleIngredientOptional(groupIndex, index) },
                        onToggleScalable = { viewModel.toggleIngredientScalable(groupIndex, index) },
                        onMove = { delta -> viewModel.moveIngredient(groupIndex, index, delta) },
                        onRemove = { viewModel.removeIngredient(groupIndex, index) },
                    )
                }
            }

            item {
                TextButton(onClick = viewModel::addGroup) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.edit_add_group))
                }
            }

            // --- kroki ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(
                    text = stringResource(R.string.detail_steps),
                    action = {
                        TextButton(onClick = viewModel::addStep) {
                            Text(stringResource(R.string.edit_add_step))
                        }
                    },
                )
            }

            items(draft.steps.size, key = { index -> "step-$index" }) { index ->
                StepEditor(
                    number = index + 1,
                    step = draft.steps[index],
                    canMoveUp = index > 0,
                    canMoveDown = index < draft.steps.lastIndex,
                    onTextChange = { viewModel.setStepText(index, it) },
                    onTimerChange = { viewModel.setStepTimerMinutes(index, it) },
                    onPhotoPicked = { viewModel.attachPhoto(it, stepIndex = index) },
                    onPhotoRemove = { viewModel.setStepPhoto(index, PhotoRef.EMPTY) },
                    onRequestCameraUri = { viewModel.prepareCameraUri() },
                    onMove = { delta -> viewModel.moveStep(index, delta) },
                    onRemove = { viewModel.removeStep(index) },
                )
            }
        }
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = stringResource(R.string.edit_discard_title),
            text = stringResource(R.string.edit_discard_text),
            confirmLabel = stringResource(R.string.edit_discard_confirm),
            isDestructive = true,
            onConfirm = {
                showDiscardDialog = false
                onCancel()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    pasteTargetGroup?.let { groupIndex ->
        TextInputDialog(
            title = stringResource(R.string.edit_paste_ingredients),
            label = stringResource(R.string.edit_paste_hint),
            singleLine = false,
            onConfirm = { text ->
                viewModel.pasteIngredients(groupIndex, text)
                pasteTargetGroup = null
            },
            onDismiss = { pasteTargetGroup = null },
        )
    }

    if (showTagDialog) {
        TextInputDialog(
            title = stringResource(R.string.edit_add_tag),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = {
                viewModel.addTag(it)
                showTagDialog = false
            },
            onDismiss = { showTagDialog = false },
        )
    }
}

@Composable
private fun difficultyLabel(difficulty: Difficulty): String = stringResource(difficulty.labelRes())

/** Wspólne strzałki kolejności i kosz — powtarzają się przy składnikach i krokach. */
@Composable
internal fun ReorderControls(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.action_move_up))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.action_move_down))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
        }
    }
}

internal fun unitPickerLabel(unit: pl.przepisak.domain.model.MeasureUnit): String =
    QuantityFormatter.unitLabelFor(unit, 1.0)
