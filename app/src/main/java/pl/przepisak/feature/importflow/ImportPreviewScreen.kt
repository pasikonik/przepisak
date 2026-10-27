package pl.przepisak.feature.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.przepisak.R
import pl.przepisak.core.ui.component.ErrorState
import pl.przepisak.core.ui.component.LoadingState
import pl.przepisak.core.ui.component.RecipePhoto
import pl.przepisak.core.ui.component.SectionHeader
import pl.przepisak.domain.model.ImportStrategy
import pl.przepisak.domain.model.PhotoRef

/** Podgląd zaimportowanego przepisu przed zapisem (RF-04, RF-05, RF-06). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    url: String,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
    viewModel: ImportPreviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(url) { viewModel.start(url) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
            )
        },
        bottomBar = {
            val imported = state.imported
            if (imported != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = { viewModel.save(onSaved) },
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.import_save))
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading || state.isSaving -> LoadingState(
                message = stringResource(state.stage.messageRes()),
                modifier = Modifier.padding(padding),
            )

            state.error != null -> Column(modifier = Modifier.padding(padding)) {
                ErrorState(error = state.error!!, onRetry = viewModel::retry)
            }

            state.imported != null -> {
                val imported = state.imported!!
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        RecipePhoto(
                            photo = PhotoRef.fromUrl(imported.imageUrl),
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                    }

                    item {
                        Text(imported.title, style = MaterialTheme.typography.headlineSmall)
                        imported.sourceName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = stringResource(imported.strategy.messageRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (imported.warnings.isNotEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.import_warnings),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    imported.warnings.forEach { warning ->
                                        Text("• $warning", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader(
                            stringResource(R.string.import_ingredients_count, imported.ingredientCount),
                        )
                    }

                    imported.ingredientGroups.forEach { group ->
                        group.name?.let { name ->
                            item(key = "import-group-$name") {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        items(group.lines.size) { index ->
                            Text("• ${group.lines[index]}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    item { SectionHeader(stringResource(R.string.import_steps_count, imported.steps.size)) }

                    items(imported.steps.size) { index ->
                        Text(
                            text = "${index + 1}. ${imported.steps[index]}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun ImportStage.messageRes(): Int = when (this) {
    ImportStage.FETCHING -> R.string.import_stage_fetching
    ImportStage.PARSING -> R.string.import_stage_parsing
    ImportStage.ASKING_MODEL -> R.string.import_stage_gemini
    ImportStage.SAVING -> R.string.import_stage_saving
}

private fun ImportStrategy.messageRes(): Int = when (this) {
    ImportStrategy.SCHEMA_ORG_JSONLD -> R.string.import_strategy_jsonld
    ImportStrategy.SCHEMA_ORG_MICRODATA -> R.string.import_strategy_microdata
    ImportStrategy.GEMINI_HTML -> R.string.import_strategy_gemini_html
    ImportStrategy.GEMINI_VIDEO_TRANSCRIPT -> R.string.import_strategy_gemini_transcript
    ImportStrategy.GEMINI_VIDEO_DESCRIPTION -> R.string.import_strategy_gemini_description
    ImportStrategy.GEMINI_TEXT -> R.string.import_strategy_gemini_text
    ImportStrategy.COOKMATE_FILE -> R.string.import_strategy_cookmate
}
