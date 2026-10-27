package pl.przepisak.domain.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeDetectorTest {

    private fun seconds(text: String): List<Int> = TimeDetector.detect(text).map { it.seconds }

    @Test
    fun `wykrywa minuty`() {
        assertEquals(listOf(25 * 60), seconds("Piecz 25 minut w piekarniku."))
        assertEquals(listOf(30 * 60), seconds("Odstaw na 30 min."))
        assertEquals(listOf(90 * 60), seconds("Gotuj 90 min"))
    }

    @Test
    fun `wykrywa godziny`() {
        assertEquals(listOf(3600), seconds("Piecz 1 h"))
        assertEquals(listOf(2 * 3600), seconds("Duś 2 godziny."))
    }

    @Test
    fun `wykrywa czas z czescia dziesietna`() {
        assertEquals(listOf(5400), seconds("Chłodź 1,5 godziny"))
    }

    @Test
    fun `wykrywa sekundy`() {
        assertEquals(listOf(45), seconds("Blanszuj 45 s"))
        assertEquals(listOf(30), seconds("Miksuj 30 sekund"))
    }

    @Test
    fun `dla zakresu bierze dolna granice`() {
        // Ciasta nie da się odpiec z powrotem — minutnik ma zadzwonić wcześniej,
        // a czas zawsze można dołożyć.
        assertEquals(listOf(20 * 60), seconds("Piecz 20-25 minut"))
        assertEquals(listOf(20 * 60), seconds("Piecz 20–25 min"))
    }

    @Test
    fun `wykrywa zapisy slowne`() {
        assertEquals(listOf(30 * 60), seconds("Odstaw na pół godziny"))
        assertEquals(listOf(15 * 60), seconds("Odczekaj kwadrans"))
        assertEquals(listOf(3600), seconds("Wstaw do lodówki na godzinę"))
        assertEquals(listOf(90 * 60), seconds("Wyrastaj półtorej godziny"))
    }

    @Test
    fun `nie traktuje temperatury jako czasu`() {
        assertTrue(TimeDetector.detect("Rozgrzej piekarnik do 180 stopni").isEmpty())
        assertTrue(TimeDetector.detect("Piecz w 200°C").isEmpty())
    }

    @Test
    fun `nie traktuje ilosci skladnikow jako czasu`() {
        assertTrue(TimeDetector.detect("Dodaj 3 łyżki cukru").isEmpty())
        assertTrue(TimeDetector.detect("Wlej 200 ml mleka").isEmpty())
        assertTrue(TimeDetector.detect("Odważ 250 g mąki").isEmpty())
    }

    @Test
    fun `nie lapie skrotu min ukrytego w innym wyrazie`() {
        assertTrue(TimeDetector.detect("Wlej 300 ml wody mineralnej").isEmpty())
    }

    @Test
    fun `wykrywa kilka czasow w jednym kroku i zachowuje kolejnosc`() {
        val detected = TimeDetector.detect("Podsmaż 5 minut, potem duś 40 minut.")
        assertEquals(listOf(5 * 60, 40 * 60), detected.map { it.seconds })
        assertTrue(detected[0].startIndex < detected[1].startIndex)
    }

    @Test
    fun `podaje poprawny zakres w tekscie`() {
        val text = "Piecz 25 minut."
        val detected = TimeDetector.detect(text).single()
        assertEquals("25 minut", text.substring(detected.startIndex, detected.endIndex))
    }

    @Test
    fun `pomija czasy absurdalnie dlugie i zerowe`() {
        assertTrue(TimeDetector.detect("Leżakuj 100 godzin").isEmpty())
        assertTrue(TimeDetector.detect("Odczekaj 0 minut").isEmpty())
    }

    @Test
    fun `pusty tekst nie daje wynikow`() {
        assertTrue(TimeDetector.detect("").isEmpty())
        assertTrue(TimeDetector.detect("   ").isEmpty())
    }
}
