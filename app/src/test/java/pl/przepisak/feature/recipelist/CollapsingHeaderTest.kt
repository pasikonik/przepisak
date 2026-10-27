package pl.przepisak.feature.recipelist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsingHeaderTest {

    private fun headerOfHeight(height: Float) = CollapsingHeaderState().apply { heightPx = height }

    /** Ujemne `dy` to przewijanie treści w dół (palec jedzie w górę). */
    private fun CollapsingHeaderState.scrollBy(dy: Float): Float =
        nestedScrollConnection.onPreScroll(Offset(0f, dy), NestedScrollSource.UserInput).y

    @Test
    fun `przewijanie w dol chowa pasek o tyle samo co ruch palca`() {
        val header = headerOfHeight(100f)

        val consumed = header.scrollBy(-30f)

        assertEquals(-30f, consumed, 0f)
        assertEquals(-30f, header.offsetPx, 0f)
    }

    @Test
    fun `gdy pasek jest juz schowany reszta przewiniecia zostaje dla listy`() {
        val header = headerOfHeight(100f)
        header.scrollBy(-90f)

        val consumed = header.scrollBy(-30f)

        assertEquals(-10f, consumed, 0f)
        assertEquals(-100f, header.offsetPx, 0f)
    }

    @Test
    fun `ruch w gore od razu wysuwa schowany pasek`() {
        val header = headerOfHeight(100f)
        header.scrollBy(-100f)

        val consumed = header.scrollBy(20f)

        assertEquals(20f, consumed, 0f)
        assertEquals(-80f, header.offsetPx, 0f)
    }

    @Test
    fun `pasek schowany mniej niz do polowy wraca na miejsce`() {
        assertEquals(0f, snapTarget(offsetPx = -40f, heightPx = 100f), 0f)
    }

    @Test
    fun `pasek schowany ponad polowe chowa sie do konca`() {
        assertEquals(-100f, snapTarget(offsetPx = -60f, heightPx = 100f), 0f)
    }
}
