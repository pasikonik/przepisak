package pl.przepisak.feature.cook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.przepisak.R
import pl.przepisak.domain.model.CookingTimer
import pl.przepisak.domain.model.TimerState
import pl.przepisak.domain.scaling.QuantityFormatter

/** Pasek aktywnych minutników, widoczny na każdym kroku trybu gotowania (RF-13). */
@Composable
fun TimerBar(
    timers: List<CookingTimer>,
    nowMs: Long,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onAddMinute: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (timers.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = timers, key = { it.id }) { timer ->
            val finished = timer.state == TimerState.FINISHED || timer.remainingSecondsAt(nowMs) == 0
            Card(
                // Sztywna szerokość jest tu wymagana, nie kosmetyczna: w LazyRow element
                // dostaje nieograniczoną szerokość, a `weight(1f)` w Row o nieograniczonej
                // szerokości daje dziecku zero — kolumna z etykietą i czasem znika.
                modifier = Modifier.width(210.dp),
                colors = if (finished) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { timer.progressAt(nowMs) },
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = timer.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (finished) {
                                stringResource(R.string.timers_done)
                            } else {
                                QuantityFormatter.formatSeconds(timer.remainingSecondsAt(nowMs))
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    when {
                        finished -> TextButton(onClick = { onAddMinute(timer.id) }) {
                            Text(stringResource(R.string.timer_add_minute))
                        }

                        timer.state == TimerState.RUNNING -> IconButton(onClick = { onPause(timer.id) }) {
                            Icon(Icons.Outlined.Pause, contentDescription = stringResource(R.string.timer_pause))
                        }

                        else -> IconButton(onClick = { onResume(timer.id) }) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.timer_resume))
                        }
                    }
                    IconButton(onClick = { onCancel(timer.id) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.timer_stop))
                    }
                }
            }
        }
    }
}
