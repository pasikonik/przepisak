package pl.przepisak.feature.recipedetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.przepisak.R
import pl.przepisak.core.ui.component.NumberStepper
import pl.przepisak.domain.scaling.QuantityFormatter
import kotlin.math.abs

/**
 * Pasek skalowania porcji (RF-08) — jeden wąski, przewijany w poziomie rząd,
 * bez karty ani nagłówka, żeby nie zabierał miejsca na ekranie szczegółów.
 *
 * Gdy przepis nie podaje liczby porcji, stepper nie ma czego zwiększać — wtedy
 * pokazujemy tylko mnożniki i wyjaśniamy to jednym zdaniem, zamiast wyświetlać
 * kontrolkę, która nic nie robi. Skrót do kalkulatora form nie żyje tutaj —
 * jest w menu „⋮" i w zakładce Narzędzia.
 */
@Composable
fun ServingsScaler(
    originalServings: Int?,
    targetServings: Int?,
    factor: Double,
    onTargetChange: (Int?) -> Unit,
    onMultiplier: (Double) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isScaled = abs(factor - 1.0) > 0.001

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (originalServings != null && originalServings > 0) {
                NumberStepper(
                    value = targetServings ?: originalServings,
                    onValueChange = { onTargetChange(it) },
                    range = 1..200,
                )
            } else {
                Text(
                    text = stringResource(R.string.scaler_no_servings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            listOf(0.5 to "×0,5", 1.0 to "×1", 2.0 to "×2", 3.0 to "×3").forEach { (value, label) ->
                AssistChip(onClick = { onMultiplier(value) }, label = { Text(label) })
            }
        }

        if (isScaled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.scaler_factor, QuantityFormatter.decimal(factor)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset) { Text(stringResource(R.string.scaler_reset)) }
            }
        }
    }
}
