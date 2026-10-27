package pl.przepisak.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.przepisak.R
import pl.przepisak.domain.model.RecipeListItem

/**
 * Kafelek przepisu na liście. Zdjęcie u góry, pod nim nazwa, odznaki i tagi —
 * układ, który pozwala rozpoznać przepis jednym spojrzeniem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCard(
    item: RecipeListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        RecipePhoto(
            photo = item.photo,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                trailingAction?.invoke()
            }

            if (item.categoryName != null || item.rating != null) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.categoryName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.rating != null) {
                        RatingStars(rating = item.rating, starSize = 14.dp)
                    }
                }
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeBadge(minutes = item.totalTimeMinutes)
                ServingsBadge(servings = item.servings)
                DifficultyBadge(difficulty = item.difficulty)
                if (item.isFavorite) FavoriteMark()
            }

            if (item.tagNames.isNotEmpty()) {
                TagChipRow(tags = item.tagNames, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/**
 * Kafelek przepisu w widoku siatki (2 kolumny). Świadomie uboższy niż [RecipeCard] —
 * przy dwóch kolumnach kategoria, ocena, odznaki i tagi zawijałyby się do wielu
 * linii i karty wyszłyby nierówne; siatka ma sens tylko wtedy, gdy karty są krótkie.
 */
@Composable
fun RecipeGridCard(
    item: RecipeListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        RecipePhoto(
            photo = item.photo,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Pusty wiersz dołożyłby karcie odstęp, a karty w siatce mają być równe.
            if ((item.totalTimeMinutes ?: 0) > 0 || item.isFavorite) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeBadge(minutes = item.totalTimeMinutes)
                    if (item.isFavorite) FavoriteMark()
                }
            }
        }
    }
}

/**
 * Super kompaktowy wiersz przepisu — bez karty, małe zdjęcie, sam tytuł. Do
 * szybkiego przeglądania długiej listy, gdy szczegóły z [RecipeCard] nie są potrzebne.
 */
@Composable
fun RecipeCompactRow(
    item: RecipeListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecipePhoto(
            photo = item.photo,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.isFavorite) FavoriteMark()
    }
}

/**
 * Ciche oznaczenie ulubionego przepisu, w stylu ikonek czasu i porcji. Nie da się go
 * kliknąć — do ulubionych dodaje się z ekranu przepisu, żeby nic nie zasłaniało zdjęć.
 */
@Composable
private fun FavoriteMark() {
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = stringResource(R.string.favorite_mark),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(14.dp),
    )
}
