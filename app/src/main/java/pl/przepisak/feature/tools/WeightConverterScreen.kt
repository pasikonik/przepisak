package pl.przepisak.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.przepisak.R
import pl.przepisak.core.ui.component.DropdownField
import pl.przepisak.core.ui.component.SectionHeader
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.scaling.QuantityFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightConverterScreen(
    onBack: () -> Unit,
    viewModel: WeightConverterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_weight_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleDirection) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(stringResource(R.string.weight_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text(
                    text = stringResource(
                        if (state.direction == ConversionDirection.VOLUME_TO_WEIGHT) {
                            R.string.weight_direction_to_grams
                        } else {
                            R.string.weight_direction_to_volume
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (state.selected == null) {
                item {
                    Text(
                        text = stringResource(
                            if (state.query.isBlank()) R.string.weight_popular else R.string.weight_search_hint,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            items(items = state.results, key = { it.id }) { density ->
                FilterChip(
                    selected = state.selected?.id == density.id,
                    onClick = { viewModel.select(density) },
                    label = { Text(density.name) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.results.isEmpty() && state.query.isNotBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.weight_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.selected?.let { density ->
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(density.name, style = MaterialTheme.typography.titleMedium)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.amountInput,
                            onValueChange = viewModel::setAmount,
                            label = {
                                Text(
                                    stringResource(
                                        if (state.direction == ConversionDirection.WEIGHT_TO_VOLUME) {
                                            R.string.weight_grams_input
                                        } else {
                                            R.string.weight_amount
                                        },
                                    ),
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        DropdownField(
                            value = state.unit,
                            options = MeasureUnit.volumeForWeightConversion,
                            label = stringResource(R.string.weight_unit),
                            itemLabel = { QuantityFormatter.unitLabelFor(it, 1.0) },
                            onSelect = { unit -> unit?.let(viewModel::setUnit) },
                            allowEmpty = false,
                            modifier = Modifier.weight(1.2f),
                        )
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            when (state.direction) {
                                ConversionDirection.VOLUME_TO_WEIGHT -> {
                                    val conversion = state.conversion
                                    Text(
                                        text = if (conversion == null) {
                                            "—"
                                        } else {
                                            stringResource(
                                                R.string.weight_result_grams,
                                                QuantityFormatter.decimal(conversion.grams, maxDecimals = 1),
                                            )
                                        },
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (conversion?.isEstimated == true) {
                                        Text(
                                            text = stringResource(R.string.weight_estimated),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                }

                                ConversionDirection.WEIGHT_TO_VOLUME -> Text(
                                    text = state.volumeResult?.let { volume ->
                                        stringResource(
                                            R.string.weight_result_volume,
                                            QuantityFormatter.formatAmount(volume),
                                            QuantityFormatter.unitLabelFor(state.unit, volume),
                                        )
                                    } ?: "—",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                if (state.quickTable.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.weight_quick_table)) }
                    items(items = state.quickTable, key = { it.label }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(row.label, modifier = Modifier.weight(1f))
                            Text(
                                text = stringResource(
                                    R.string.weight_result_grams,
                                    QuantityFormatter.decimal(row.grams, maxDecimals = 1),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
