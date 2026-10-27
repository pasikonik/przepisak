package pl.przepisak.feature.recipeedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.przepisak.R
import pl.przepisak.core.ui.component.DropdownField
import pl.przepisak.domain.model.Ingredient
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.scaling.QuantityFormatter

/** Jeden wiersz edytora składników (RF-02). */
@Composable
fun IngredientEditor(
    ingredient: Ingredient,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAmountChange: (String) -> Unit,
    onUnitChange: (MeasureUnit?) -> Unit,
    onNameChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onToggleOptional: () -> Unit,
    onToggleScalable: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ingredient.quantity?.let { QuantityFormatter.decimal(it.amount) }.orEmpty(),
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.edit_ingredient_amount)) },
                // Klawiatura dziesiętna: w polskich przepisach ilości bywają ułamkowe,
                // a użytkownik i tak wpisze przecinek — parser to obsłuży.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(0.9f),
            )
            DropdownField(
                value = ingredient.unit,
                options = MeasureUnit.pickerOrder,
                label = stringResource(R.string.edit_ingredient_unit),
                itemLabel = { unit -> unitPickerLabel(unit) },
                onSelect = onUnitChange,
                modifier = Modifier.weight(1.3f),
            )
        }

        OutlinedTextField(
            value = ingredient.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.edit_ingredient_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        OutlinedTextField(
            value = ingredient.note.orEmpty(),
            onValueChange = onNoteChange,
            label = { Text(stringResource(R.string.edit_ingredient_note)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = ingredient.isOptional,
                onClick = onToggleOptional,
                label = { Text(stringResource(R.string.edit_ingredient_optional)) },
            )
            FilterChip(
                selected = !ingredient.isScalable,
                onClick = onToggleScalable,
                label = { Text(stringResource(R.string.edit_ingredient_not_scalable)) },
            )
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                ReorderControls(canMoveUp, canMoveDown, onMove, onRemove)
            }
        }
    }
}
