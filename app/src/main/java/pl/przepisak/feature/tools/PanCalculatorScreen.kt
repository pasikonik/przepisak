package pl.przepisak.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.przepisak.R
import pl.przepisak.core.ui.component.SectionHeader
import pl.przepisak.domain.model.BakingPan
import pl.przepisak.domain.model.PanShape
import pl.przepisak.domain.scaling.PanCalculator
import pl.przepisak.domain.scaling.QuantityFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanCalculatorScreen(
    onBack: () -> Unit,
    viewModel: PanCalculatorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_pan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                PanPicker(
                    pan = state.source,
                    onPanChange = viewModel::setSource,
                    label = stringResource(R.string.pan_source),
                )
            }
            item {
                PanPicker(
                    pan = state.target,
                    onPanChange = viewModel::setTarget,
                    label = stringResource(R.string.pan_target),
                )
            }
            item {
                val conversion = state.conversion
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (conversion == null) {
                            Text(
                                text = stringResource(R.string.pan_incomplete),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val factorText = QuantityFormatter.decimal(conversion.areaFactor)
                            Text(
                                text = stringResource(R.string.pan_result_factor, factorText),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.pan_result_hint, factorText),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                text = stringResource(
                                    R.string.pan_result_areas,
                                    QuantityFormatter.decimal(conversion.sourceAreaCm2, maxDecimals = 0),
                                    QuantityFormatter.decimal(conversion.targetAreaCm2, maxDecimals = 0),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            conversion.volumeFactor?.let { volumeFactor ->
                                Text(
                                    text = stringResource(
                                        R.string.pan_result_volume,
                                        QuantityFormatter.decimal(volumeFactor),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Text(
                                    text = stringResource(R.string.pan_result_volume_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Wybór kształtu i wymiarów jednej formy (RF-09). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanPicker(
    pan: BakingPan,
    onPanChange: (BakingPan) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(label)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PanShape.entries.forEachIndexed { index, shape ->
                SegmentedButton(
                    selected = pan.shape == shape,
                    onClick = { onPanChange(pan.copy(shape = shape, label = null)) },
                    shape = SegmentedButtonDefaults.itemShape(index, PanShape.entries.size),
                ) {
                    Text(stringResource(shape.labelRes()))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (pan.shape) {
                PanShape.ROUND -> DimensionField(
                    value = pan.diameterCm,
                    label = stringResource(R.string.pan_diameter),
                    onChange = { onPanChange(pan.copy(diameterCm = it, label = null)) },
                    modifier = Modifier.weight(1f),
                )

                PanShape.RECTANGLE -> {
                    DimensionField(
                        value = pan.lengthCm,
                        label = stringResource(R.string.pan_length),
                        onChange = { onPanChange(pan.copy(lengthCm = it, label = null)) },
                        modifier = Modifier.weight(1f),
                    )
                    DimensionField(
                        value = pan.widthCm,
                        label = stringResource(R.string.pan_width),
                        onChange = { onPanChange(pan.copy(widthCm = it, label = null)) },
                        modifier = Modifier.weight(1f),
                    )
                }

                PanShape.RING -> {
                    DimensionField(
                        value = pan.diameterCm,
                        label = stringResource(R.string.pan_diameter),
                        onChange = { onPanChange(pan.copy(diameterCm = it, label = null)) },
                        modifier = Modifier.weight(1f),
                    )
                    DimensionField(
                        value = pan.innerDiameterCm,
                        label = stringResource(R.string.pan_inner_diameter),
                        onChange = { onPanChange(pan.copy(innerDiameterCm = it, label = null)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        DimensionField(
            value = pan.heightCm,
            label = stringResource(R.string.pan_height),
            onChange = { onPanChange(pan.copy(heightCm = it)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Text(
            text = stringResource(R.string.pan_presets),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BakingPan.presets.filter { it.shape == pan.shape }.forEach { preset ->
                FilterChip(
                    selected = pan.label == preset.label,
                    onClick = { onPanChange(preset) },
                    label = { Text(preset.label ?: PanCalculator.describe(preset)) },
                )
            }
        }
    }
}

/**
 * Pole wymiaru przyjmujące przecinek i kropkę.
 *
 * Polska klawiatura podaje przecinek, a `toDoubleOrNull` rozumie tylko kropkę —
 * bez tej zamiany kalkulator milczałby przy poprawnie wpisanej liczbie.
 */
@Composable
private fun DimensionField(
    value: Double?,
    label: String,
    onChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value?.let { QuantityFormatter.decimal(it) }.orEmpty(),
        onValueChange = { text ->
            val cleaned = text.replace(',', '.').filter { it.isDigit() || it == '.' }
            onChange(cleaned.toDoubleOrNull())
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}

private fun PanShape.labelRes(): Int = when (this) {
    PanShape.ROUND -> R.string.pan_shape_round
    PanShape.RECTANGLE -> R.string.pan_shape_rectangle
    PanShape.RING -> R.string.pan_shape_ring
}
