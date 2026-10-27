package pl.przepisak.feature.recipelist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * Paski nad listą, które chowają się jak pasek adresu w przeglądarce.
 *
 * Przy przewijaniu w dół pasek odjeżdża razem z treścią — dokładnie o tyle, o ile
 * przesuwa się palec — a przy pierwszym ruchu w górę wraca, też za palcem. Dopóki
 * pasek się chowa albo wysuwa, zabiera przewinięcie dla siebie, a lista pod nim
 * rośnie lub kurczy się o tyle samo: treść jedzie z palcem i nie podskakuje.
 * Po puszczeniu ekranu pasek domyka się do bliższego końca.
 *
 * Wcześniej było tu `AnimatedVisibility`: osobna animacja odpalana przy pierwszym
 * pikselu przewinięcia, która zmieniała wysokość listy niezależnie od ruchu palca.
 */
@Stable
class CollapsingHeaderState {
    /** Pełna wysokość paska, mierzona przy układaniu. */
    var heightPx by mutableFloatStateOf(0f)
        internal set

    /** 0 — pasek cały widoczny, `-heightPx` — schowany. */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Pasek mógł się skurczyć (np. zniknął rząd kategorii), więc stare
            // przesunięcie może wypadać poza nowy zakres.
            val previous = offsetPx.coerceIn(-heightPx, 0f)
            offsetPx = (previous + available.y).coerceIn(-heightPx, 0f)
            return Offset(0f, offsetPx - previous)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            animateTo(snapTarget(offsetPx, heightPx))
            return Velocity.Zero
        }
    }

    /**
     * Wysuwa pasek. Potrzebne po zmianie filtrów: zamiast listy może pojawić się pusty
     * ekran, którego nie da się przewinąć — a bez przewijania schowany pasek by nie wrócił.
     */
    suspend fun show() = animateTo(0f)

    private suspend fun animateTo(target: Float) {
        if (offsetPx == target) return
        animate(offsetPx, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
            offsetPx = value
        }
    }
}

/** Dokąd domknąć pasek po puszczeniu ekranu: do bliższego końca, a dokładnie w połowie — pokazać. */
internal fun snapTarget(offsetPx: Float, heightPx: Float): Float =
    if (offsetPx >= -heightPx / 2) 0f else -heightPx

/**
 * Pasek zajmuje tylko tyle miejsca, ile z niego widać, a to, co już odjechało, jest
 * przycięte u góry — wygląda, jakby wsuwał się pod górny pasek aplikacji.
 */
fun Modifier.collapsingHeader(state: CollapsingHeaderState): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val visible = (placeable.height + state.offsetPx.roundToInt()).coerceIn(0, placeable.height)
        layout(placeable.width, visible) { placeable.place(0, visible - placeable.height) }
    }
    .onSizeChanged { state.heightPx = it.height.toFloat() }
