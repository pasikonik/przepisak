package pl.przepisak.feature.recipeedit

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.przepisak.R
import pl.przepisak.domain.model.Step

/** Jeden krok w edytorze przepisu (RF-02). */
@Composable
fun StepEditor(
    number: Int,
    step: Step,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTextChange: (String) -> Unit,
    onTimerChange: (String) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onPhotoRemove: () -> Unit,
    onRequestCameraUri: suspend () -> Uri,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.detail_step_number, number),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            ReorderControls(canMoveUp, canMoveDown, onMove, onRemove)
        }

        OutlinedTextField(
            value = step.text,
            onValueChange = onTextChange,
            label = { Text(stringResource(R.string.edit_step_text)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = step.timerSeconds?.let { (it / 60).toString() }.orEmpty(),
                onValueChange = onTimerChange,
                label = { Text(stringResource(R.string.edit_step_timer)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PhotoPickerButton(
                photo = step.photo,
                onPicked = onPhotoPicked,
                onRemove = onPhotoRemove,
                onRequestCameraUri = onRequestCameraUri,
                compact = true,
                modifier = Modifier.size(96.dp),
            )
        }
    }
}
