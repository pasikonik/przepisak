package pl.przepisak.feature.timers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.przepisak.R
import pl.przepisak.core.ui.component.EmptyState
import pl.przepisak.feature.cook.TimerBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimersScreen(
    onBack: () -> Unit,
    viewModel: TimersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.timers.isNotEmpty()) {
                        TextButton(onClick = viewModel::cancelAll) {
                            Text(stringResource(R.string.timers_stop_all))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.timers_add))
            }
        },
    ) { padding ->
        if (state.timers.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Timer,
                title = stringResource(R.string.timers_empty_title),
                message = stringResource(R.string.timers_empty_message),
                actionLabel = stringResource(R.string.timers_add),
                onAction = { showAddDialog = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = state.timers, key = { it.id }) { timer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        TimerBar(
                            timers = listOf(timer),
                            nowMs = state.nowMs,
                            onPause = viewModel::pause,
                            onResume = viewModel::resume,
                            onAddMinute = viewModel::addMinute,
                            onCancel = viewModel::cancel,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTimerDialog(
            onConfirm = { label, seconds ->
                viewModel.start(label, seconds)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun AddTimerDialog(onConfirm: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }

    val defaultLabel = stringResource(R.string.timer_default_label)
    val totalSeconds = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timers_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.timer_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { char -> char.isDigit() } },
                        label = { Text(stringResource(R.string.timer_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter { char -> char.isDigit() } },
                        label = { Text(stringResource(R.string.timer_seconds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Najczęstsze czasy jednym dotknięciem — szybciej niż wpisywanie.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(1, 3, 5, 10, 15, 30).forEach { preset ->
                        AssistChip(
                            onClick = { minutes = preset.toString() },
                            label = { Text("$preset") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.ifBlank { defaultLabel }, totalSeconds) },
                enabled = totalSeconds > 0,
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
