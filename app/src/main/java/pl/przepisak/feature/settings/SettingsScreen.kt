package pl.przepisak.feature.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.przepisak.BuildConfig
import pl.przepisak.R
import pl.przepisak.core.ui.component.DropdownField
import pl.przepisak.core.ui.component.SectionHeader
import pl.przepisak.core.ui.component.appErrorMessage
import pl.przepisak.data.prefs.GeminiModel
import pl.przepisak.data.prefs.ThemeMode
import pl.przepisak.data.repository.SyncStatus
import pl.przepisak.domain.scaling.QuantityFormatter
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var email by remember(state.settings.supabaseEmail) { mutableStateOf(state.settings.supabaseEmail) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var showAnonKey by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val geminiOkTemplate = stringResource(R.string.settings_gemini_ok)
    val syncSuccessTemplate = stringResource(R.string.settings_sync_success)
    val cookmateResultTemplate = stringResource(R.string.settings_cookmate_import_result)
    val genericError = stringResource(R.string.error_unknown)

    val cookmateImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importFromCookmate(uri, cookmateResultTemplate, genericError)
    }

    val resolvedError = state.error?.let { appErrorMessage(it) }
    LaunchedEffect(state.message, resolvedError) {
        val text = state.message ?: resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            // --- wygląd ---
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }

            item {
                DropdownField(
                    value = state.settings.themeMode,
                    options = ThemeMode.entries,
                    label = stringResource(R.string.settings_theme),
                    itemLabel = { mode -> themeLabel(mode) },
                    onSelect = { mode -> mode?.let(viewModel::setThemeMode) },
                    allowEmpty = false,
                )
            }

            // Kolory z tapety istnieją dopiero od Androida 12 — na starszych
            // systemach przełącznik byłby martwy, więc go nie pokazujemy.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(
                        label = stringResource(R.string.settings_dynamic_color),
                        checked = state.settings.useDynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }

            item {
                Column {
                    Text(stringResource(R.string.settings_kitchen_scale), style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = state.settings.kitchenFontScale,
                        onValueChange = viewModel::setKitchenFontScale,
                        valueRange = 1.0f..2.0f,
                        steps = 9,
                    )
                    Text(
                        text = stringResource(R.string.settings_kitchen_scale_preview),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * state.settings.kitchenFontScale,
                        ),
                    )
                }
            }

            item {
                SwitchRow(
                    label = stringResource(R.string.settings_keep_screen_on),
                    checked = state.settings.keepScreenOnInKitchenMode,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
            }

            // --- import AI ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(stringResource(R.string.settings_section_ai))
            }

            item {
                SecretField(
                    value = state.geminiKey,
                    onValueChange = viewModel::setGeminiKey,
                    label = stringResource(R.string.settings_gemini_key),
                    visible = showGeminiKey,
                    onToggleVisibility = { showGeminiKey = !showGeminiKey },
                )
            }

            item {
                DropdownField(
                    value = state.settings.geminiModel,
                    options = GeminiModel.entries,
                    label = stringResource(R.string.settings_gemini_model),
                    itemLabel = { it.displayName },
                    onSelect = { model -> model?.let(viewModel::setGeminiModel) },
                    allowEmpty = false,
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { viewModel.testGemini(geminiOkTemplate) },
                        enabled = state.geminiKey.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.settings_gemini_test))
                    }
                }
                Text(
                    text = stringResource(R.string.settings_gemini_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- kopia zapasowa ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(stringResource(R.string.settings_section_backup))
            }

            item {
                OutlinedTextField(
                    value = state.settings.supabaseUrl,
                    onValueChange = viewModel::setSupabaseUrl,
                    label = { Text(stringResource(R.string.settings_supabase_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                SecretField(
                    value = state.supabaseAnonKey,
                    onValueChange = viewModel::setSupabaseAnonKey,
                    label = stringResource(R.string.settings_supabase_key),
                    visible = showAnonKey,
                    onToggleVisibility = { showAnonKey = !showAnonKey },
                )
            }

            item {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        viewModel.setEmail(it)
                    },
                    label = { Text(stringResource(R.string.settings_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                SecretField(
                    value = state.password,
                    onValueChange = viewModel::setPassword,
                    label = stringResource(R.string.settings_password),
                    visible = showPassword,
                    onToggleVisibility = { showPassword = !showPassword },
                )
            }

            item {
                Text(
                    text = state.signedInEmail
                        ?.let { stringResource(R.string.settings_signed_in_as, it) }
                        ?: stringResource(R.string.settings_signed_out),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.signedInEmail == null) {
                        TextButton(onClick = { viewModel.signIn(email) }) {
                            Text(stringResource(R.string.settings_sign_in))
                        }
                        TextButton(onClick = { viewModel.signUp(email) }) {
                            Text(stringResource(R.string.settings_sign_up))
                        }
                    } else {
                        TextButton(onClick = viewModel::signOut) {
                            Text(stringResource(R.string.settings_sign_out))
                        }
                    }
                }
            }

            item {
                SwitchRow(
                    label = stringResource(R.string.settings_auto_sync),
                    checked = state.settings.autoSyncEnabled,
                    onCheckedChange = viewModel::setAutoSync,
                )
            }

            item {
                SwitchRow(
                    label = stringResource(R.string.settings_wifi_only),
                    checked = state.settings.syncOnWifiOnly,
                    onCheckedChange = viewModel::setWifiOnly,
                )
            }

            item {
                Column {
                    if (state.syncStatus is SyncStatus.Running) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Button(
                        onClick = { viewModel.syncNow(syncSuccessTemplate) },
                        enabled = state.settings.isSupabaseConfigured && state.syncStatus !is SyncStatus.Running,
                    ) {
                        Text(stringResource(R.string.settings_sync_now))
                    }
                    Text(
                        text = stringResource(R.string.settings_pending_changes, state.pendingChanges),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_last_sync,
                            state.settings.lastSyncAtEpochMs
                                .takeIf { it > 0 }
                                ?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
                                ?: stringResource(R.string.settings_last_sync_never),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    (state.syncStatus as? SyncStatus.Failed)?.let { failed ->
                        Text(
                            text = appErrorMessage(failed.error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_backup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- zdjęcia ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(stringResource(R.string.settings_section_photos))
            }

            item {
                ChoiceRow(
                    label = stringResource(R.string.settings_photo_dimension),
                    options = listOf(1200, 1600, 2048),
                    selected = state.settings.photoMaxDimensionPx,
                    optionLabel = { stringResource(R.string.settings_photo_dimension_value, it) },
                    onSelect = viewModel::setPhotoMaxDimension,
                )
            }

            item {
                ChoiceRow(
                    label = stringResource(R.string.settings_photo_size),
                    options = listOf(150, 300, 500),
                    selected = state.settings.photoTargetKb,
                    optionLabel = { stringResource(R.string.settings_photo_size_value, it) },
                    onSelect = viewModel::setPhotoTargetKb,
                )
            }

            // --- minutniki ---
            if (!state.canScheduleExactAlarms) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(stringResource(R.string.settings_section_timers))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_exact_alarm_warning),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(
                                onClick = {
                                    runCatching { context.startActivity(viewModel.exactAlarmSettingsIntent()) }
                                },
                            ) {
                                Text(stringResource(R.string.settings_exact_alarm_action))
                            }
                        }
                    }
                }
            }

            // --- o aplikacji ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(stringResource(R.string.settings_section_about))
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_recipe_count, state.recipeCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Narzędzie jednorazowej migracji z Cookmate — tylko w buildzie debug,
            // do usunięcia po zakończeniu importu własnej kolekcji przepisów.
            if (BuildConfig.DEBUG) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(stringResource(R.string.settings_section_debug))
                    TextButton(onClick = { cookmateImportLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.settings_cookmate_import_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Pole na klucz albo hasło — maskowane, z możliwością podejrzenia. */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.settings_hide_secret else R.string.settings_show_secret,
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)
