package pl.przepisak.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pl.przepisak.R
import pl.przepisak.core.util.AppError
import pl.przepisak.data.media.PhotoPaths
import pl.przepisak.domain.model.Difficulty
import pl.przepisak.domain.model.PhotoRef
import pl.przepisak.domain.scaling.QuantityFormatter
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

// ---------------------------------------------------------------------------
// Zdjęcia
// ---------------------------------------------------------------------------

@Composable
fun RecipePhoto(
    photo: PhotoRef,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = Icons.Outlined.Restaurant,
) {
    val context = LocalContext.current
    val model = remember(photo, context) { PhotoPaths.coilModel(context, photo) }

    if (model == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/** Zasób z nazwą poziomu trudności — trzymamy go blisko UI, nie w modelu domenowym. */
fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
}

// ---------------------------------------------------------------------------
// Stany ekranu
// ---------------------------------------------------------------------------

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(message: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ErrorState(error: AppError, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = appErrorMessage(error),
        actionLabel = if (onRetry != null) stringResource(R.string.action_retry) else null,
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Tłumaczy błąd domenowy na komunikat dla użytkownika. */
@Composable
fun appErrorMessage(error: AppError): String = when (error) {
    is AppError.NoNetwork -> stringResource(R.string.error_no_network)
    is AppError.Http -> stringResource(R.string.error_http, error.code.toString())
    is AppError.ParsingFailed -> error.messageArg
        ?.let { stringResource(R.string.error_parsing_failed_detail, it) }
        ?: stringResource(R.string.error_parsing_failed)
    AppError.MissingGeminiKey -> stringResource(R.string.error_missing_gemini_key)
    is AppError.GeminiInvalidResponse -> stringResource(R.string.error_gemini_invalid)
    AppError.GeminiQuotaExceeded -> stringResource(R.string.error_gemini_quota)
    AppError.SupabaseNotConfigured -> stringResource(R.string.error_supabase_not_configured)
    AppError.NotAuthenticated -> stringResource(R.string.error_not_authenticated)
    AppError.SupabaseSchemaMissing -> stringResource(R.string.error_supabase_schema_missing)
    is AppError.UnsupportedUrl -> stringResource(R.string.error_unsupported_url)
    AppError.NoTranscriptAvailable -> stringResource(R.string.error_no_transcript)
    is AppError.ImageProcessingFailed -> stringResource(R.string.error_image_processing)
    is AppError.Storage -> stringResource(R.string.error_storage)
    is AppError.Unknown -> stringResource(R.string.error_unknown)
}

// ---------------------------------------------------------------------------
// Odznaki
// ---------------------------------------------------------------------------

@Composable
fun TimeBadge(minutes: Int?, modifier: Modifier = Modifier) {
    if (minutes == null || minutes <= 0) return
    InfoBadge(
        icon = Icons.Outlined.Schedule,
        text = QuantityFormatter.formatMinutes(minutes),
        modifier = modifier,
    )
}

@Composable
fun DifficultyBadge(difficulty: Difficulty?, modifier: Modifier = Modifier) {
    if (difficulty == null) return
    InfoBadge(
        icon = Icons.Outlined.Speed,
        text = stringResource(difficulty.labelRes()),
        modifier = modifier,
    )
}

@Composable
fun ServingsBadge(servings: Int?, label: String? = null, modifier: Modifier = Modifier) {
    if (servings == null || servings <= 0) return
    InfoBadge(
        icon = Icons.Outlined.Restaurant,
        text = label?.takeIf { it.isNotBlank() }?.let { "$servings $it" }
            ?: stringResource(R.string.servings_format, servings),
        modifier = modifier,
    )
}

@Composable
private fun InfoBadge(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TagChipRow(tags: List<String>, modifier: Modifier = Modifier, maxVisible: Int = 3) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.take(maxVisible).forEach { tag ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        if (tags.size > maxVisible) {
            Text(
                text = "+${tags.size - maxVisible}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RatingStars(
    rating: Int?,
    onRatingChange: ((Int?) -> Unit)? = null,
    starSize: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { star ->
            val filled = rating != null && star <= rating
            val icon = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder
            if (onRatingChange == null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(starSize),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            } else {
                IconButton(
                    // Dotknięcie tej samej gwiazdki po raz drugi czyści ocenę —
                    // inaczej nie dałoby się jej wycofać.
                    onClick = { onRatingChange(if (rating == star) null else star) },
                    modifier = Modifier.size(starSize + 16.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.rating_label),
                        modifier = Modifier.size(starSize),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogi
// ---------------------------------------------------------------------------

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String = stringResource(R.string.action_cancel),
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    confirmLabel: String = stringResource(R.string.action_save),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = label?.let { { Text(it) } },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmLabel) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ---------------------------------------------------------------------------
// Pola formularza
// ---------------------------------------------------------------------------

@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange = 1..99,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
        }
        IconButton(
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "−")
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        IconButton(
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "+")
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    value: T?,
    options: List<T>,
    label: String,
    itemLabel: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
    allowEmpty: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value?.let { itemLabel(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(
                androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
            ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowEmpty) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("—") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
            }
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(itemLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Podtrzymanie ekranu (RF-12)
// ---------------------------------------------------------------------------

/**
 * Blokuje wygaszanie ekranu na czas obecności komponentu w kompozycji.
 *
 * Ustawiamy flagę na widoku, a nie na oknie aktywności: komponent nie musi wtedy
 * wiedzieć nic o `Activity`, a poprzedni stan wraca sam przy opuszczeniu ekranu —
 * nie da się zapomnieć o „odkręceniu” flagi.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = previous }
    }
}
