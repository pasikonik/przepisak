package pl.przepisak.feature.cook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.przepisak.R
import pl.przepisak.core.ui.component.ConfirmDialog
import pl.przepisak.core.ui.component.KeepScreenOn
import pl.przepisak.core.ui.component.LoadingState
import pl.przepisak.core.ui.component.RecipePhoto
import pl.przepisak.core.ui.component.SectionHeader
import pl.przepisak.core.ui.theme.KitchenTypography
import pl.przepisak.domain.model.DetectedTimer
import pl.przepisak.domain.model.Step
import pl.przepisak.domain.scaling.QuantityFormatter

/** Najdłuższy wiersz tekstu kroku. Patrz [StepPage]. */
private val MaxStepTextWidth = 560.dp

/** Minimalny cel dotykowy na Androidzie (Material Design). */
private val MinTouchTarget = 56.dp

/**
 * Tryb gotowania (RF-11, RF-12, RF-13).
 *
 * Ekran obsługiwany mokrymi rękami przy blacie: duża czcionka, dwa ogromne
 * przyciski nawigacji na dole, minimum ozdobników.
 *
 * Układ ma trzy strefy, każda o jednym zadaniu:
 *  - górna (stała): wyjście, tytuł, składniki, minutniki, „gdzie jestem”,
 *  - środkowa (przewijana): wyłącznie treść bieżącego kroku,
 *  - dolna (stała): przejście do sąsiedniego kroku.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenModeScreen(
    recipeId: Long,
    initialServings: Int,
    onExit: () -> Unit,
    viewModel: KitchenModeViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId, initialServings) { viewModel.load(recipeId, initialServings) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showIngredients by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    KeepScreenOn(enabled = state.keepScreenOn)

    val tryExit = {
        if (state.timers.isNotEmpty()) showExitDialog = true else onExit()
    }
    BackHandler(enabled = true) { tryExit() }

    val recipe = state.recipe
    if (recipe == null) {
        LoadingState(modifier = Modifier.fillMaxSize())
        return
    }

    val steps = recipe.steps
    val pagerState = rememberPagerState(pageCount = { steps.size.coerceAtLeast(1) })
    // Przepis bez kroków też jest „skończony” — przycisk ma wtedy kończyć tryb,
    // a nie prowadzić donikąd.
    val isLastStep = pagerState.currentPage >= steps.lastIndex

    Scaffold(
        // TopAppBar (w przeciwieństwie do ręcznie złożonego Row) sam nakłada odstęp
        // paska stanu. Bez niego krzyżyk trafiał pod pasek i nie dało się go kliknąć.
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = recipe.recipe.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = tryExit) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                )
                            }
                        },
                        actions = {
                            // TextButton zamiast AssistChipa: ten sam opis, ale pełna
                            // wysokość celu dotykowego zamiast 32 dp.
                            TextButton(onClick = { showIngredients = true }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ViewList,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.cook_show_ingredients))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                    TimerBar(
                        timers = state.timers,
                        nowMs = state.nowMs,
                        onPause = viewModel::pauseTimer,
                        onResume = viewModel::resumeTimer,
                        onAddMinute = viewModel::addMinute,
                        onCancel = viewModel::cancelTimer,
                    )
                    if (steps.isNotEmpty()) {
                        StepProgress(current = pagerState.currentPage, total = steps.size)
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Row(
                    // navigationBarsPadding: bez tego przyciski chowają się pod paskiem gestów.
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Wielkie przyciski: w kuchni celuje się palcem pobieżnie. Wstecz jest
                    // akcją drugorzędną, więc ustępuje wizualnie tej głównej.
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.weight(1f).height(76.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.cook_previous),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Button(
                        onClick = {
                            if (isLastStep) {
                                tryExit()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        modifier = Modifier.weight(1f).height(76.dp),
                    ) {
                        if (isLastStep) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(
                                if (isLastStep) R.string.cook_finish else R.string.cook_next,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (steps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.cook_finished),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            return@Scaffold
        }

        // Suwak z Ustawień powiększa wyłącznie treść przepisu. Rozciągnięty na cały
        // ekran rozsadzał pasek górny i etykiety przycisków — a powiększyć trzeba to,
        // co się czyta z odległości ramienia, nie chrom.
        KitchenTypography(scale = state.fontScale) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { page ->
                val step = steps[page]
                StepPage(
                    step = step,
                    detected = state.detectedTimers[step.position].orEmpty(),
                    onStartTimer = viewModel::startTimer,
                )
            }
        }
    }

    if (showIngredients) {
        ModalBottomSheet(
            onDismissRequest = { showIngredients = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            KitchenTypography(scale = state.fontScale) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    item { SectionHeader(stringResource(R.string.detail_ingredients)) }
                    recipe.groups.forEach { group ->
                        if (!group.isDefault) {
                            item(key = "cook-group-${group.position}") {
                                Text(
                                    text = group.name.orEmpty(),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                        }
                        items(group.ingredients.size) { index ->
                            val ingredient = group.ingredients[index]
                            val isChecked = ingredient.id in state.checkedIngredientIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = MinTouchTarget)
                                    .clickable { viewModel.toggleIngredientChecked(ingredient.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { viewModel.toggleIngredientChecked(ingredient.id) },
                                )
                                val amount = QuantityFormatter.format(
                                    ingredient.quantity,
                                    ingredient.unit,
                                    ingredient.unitLabel,
                                )
                                Text(
                                    text = if (amount.isBlank()) ingredient.name else "$amount ${ingredient.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    // Przekreślenie samo w sobie bywa ledwo widoczne przy
                                    // dużej czcionce — stan niesie też kolor.
                                    textDecoration = if (isChecked) TextDecoration.LineThrough else null,
                                    color = if (isChecked) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        ConfirmDialog(
            title = stringResource(R.string.cook_exit_title),
            text = stringResource(R.string.cook_exit_text),
            // Zostawienie minutników jest częstą i poprawną odpowiedzią — nie
            // wymuszamy ich kasowania przy wyjściu z ekranu.
            confirmLabel = stringResource(R.string.cook_exit_keep),
            dismissLabel = stringResource(R.string.cook_exit_stop),
            onConfirm = {
                showExitDialog = false
                onExit()
            },
            onDismiss = {
                showExitDialog = false
                viewModel.cancelAllTimers()
                onExit()
            },
        )
    }
}

/**
 * „Gdzie jestem” — numer kroku i postęp obok siebie, tuż pod paskiem górnym.
 *
 * Wcześniej ta informacja siedziała najmniejszym tekstem przy dolnej krawędzi,
 * pod przyciskami. Jest to jedyna wskazówka orientacyjna na tym ekranie, więc
 * należy jej miejsce na górze, przy treści, której dotyczy.
 */
@Composable
private fun StepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.cook_step_progress, current + 1, total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 1f else (current + 1f) / total },
            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
        )
    }
}

/** Treść pojedynczego kroku: zdjęcie, tekst, minutniki. */
@Composable
private fun StepPage(
    step: Step,
    detected: List<DetectedTimer>,
    onStartTimer: (DetectedTimer, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            // Ograniczenie długości wiersza. Przy powiększonej czcionce i na szerokim
            // ekranie akapit rozciągnięty od krawędzi do krawędzi jest nieczytelny —
            // oko gubi początek następnego wiersza.
            modifier = Modifier
                .widthIn(max = MaxStepTextWidth)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            if (!step.photo.isEmpty) {
                RecipePhoto(
                    photo = step.photo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.large),
                )
                Spacer(Modifier.height(24.dp))
            }

            val base = MaterialTheme.typography.headlineSmall
            Text(
                text = step.text,
                // `headlineSmall` to styl nagłówkowy: SemiBold, interlinia 1,33. Całym
                // akapitem czyta się go jak ścianę tekstu. Rozmiar zostaje (czyta się
                // z odległości ramienia), ale waga wraca do zwykłej, a interlinia rośnie
                // do 1,5 — to te dwa parametry decydują o czytelności długiego tekstu.
                style = base.copy(
                    fontWeight = FontWeight.Normal,
                    lineHeight = base.fontSize * 1.5f,
                ),
            )

            val manualSeconds = step.timerSeconds
            if (detected.isNotEmpty() || manualSeconds != null) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.cook_start_timer),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    manualSeconds?.let { seconds ->
                        val label = QuantityFormatter.formatSeconds(seconds)
                        TimerButton(
                            label = label,
                            onClick = {
                                onStartTimer(
                                    DetectedTimer(
                                        matchedText = label,
                                        seconds = seconds,
                                        startIndex = 0,
                                        endIndex = 0,
                                    ),
                                    step.position,
                                )
                            },
                        )
                    }
                    detected.forEach { timer ->
                        TimerButton(
                            label = timer.label,
                            onClick = { onStartTimer(timer, step.position) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Przycisk uruchamiający minutnik.
 *
 * Wcześniej był to `AssistChip` o wysokości 32 dp — w tym trybie celuje się w niego
 * mokrym palcem, nie patrząc, więc rośnie do pełnego celu dotykowego.
 */
@Composable
private fun TimerButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = MinTouchTarget),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label)
    }
}
