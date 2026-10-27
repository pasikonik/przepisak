package pl.przepisak.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `usuwa polskie znaki diakrytyczne`() {
        assertEquals("Maka", TextNormalizer.stripDiacritics("Mąka"))
        assertEquals("zolc", TextNormalizer.stripDiacritics("żółć"))
        assertEquals("Lodz", TextNormalizer.stripDiacritics("Łódź"))
        assertEquals("gesla jazn", TextNormalizer.stripDiacritics("gęśla jaźń"))
    }

    @Test
    fun `zachowuje wielkosc liter przy usuwaniu diakrytykow`() {
        assertEquals("ZOLC", TextNormalizer.stripDiacritics("ŻÓŁĆ"))
        assertEquals("SwieTy", TextNormalizer.stripDiacritics("ŚwięTy"))
    }

    @Test
    fun `radzi sobie z diakrytykami spoza polskiego`() {
        assertEquals("creme brulee", TextNormalizer.stripDiacritics("crème brûlée"))
    }

    @Test
    fun `normalizacja daje male litery bez diakrytykow`() {
        assertEquals("maka pszenna", TextNormalizer.normalize("Mąka Pszenna"))
        assertEquals("smietana 18", TextNormalizer.normalize("Śmietana 18%"))
    }

    @Test
    fun `normalizacja usuwa interpunkcje i zwija odstepy`() {
        assertEquals("maka pszenna typ 500", TextNormalizer.normalize("mąka pszenna, typ 500!"))
        assertEquals("maslo", TextNormalizer.normalize("   masło   "))
        assertEquals("a b", TextNormalizer.normalize("a\t\nb"))
    }

    @Test
    fun `normalizacja zachowuje cyfry`() {
        assertEquals("1 szklanka", TextNormalizer.normalize("1 szklanka"))
        assertEquals("maka typ 650", TextNormalizer.normalize("mąka typ 650"))
    }

    @Test
    fun `pusty tekst normalizuje sie do pustego`() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   "))
        assertEquals("", TextNormalizer.normalize("!!!"))
    }

    @Test
    fun `buduje zapytanie FTS z dopasowaniem przedrostkowym`() {
        assertEquals("maka*", TextNormalizer.toFtsMatchQuery("mąka"))
        assertEquals("maka* AND pszenna*", TextNormalizer.toFtsMatchQuery("Mąka pszenna"))
    }

    @Test
    fun `zapytanie FTS pomija interpunkcje`() {
        assertEquals("ciasto* AND jablka*", TextNormalizer.toFtsMatchQuery("ciasto z jabłka!"))
    }

    @Test
    fun `puste zapytanie FTS daje null`() {
        assertNull(TextNormalizer.toFtsMatchQuery(""))
        assertNull(TextNormalizer.toFtsMatchQuery("   "))
        assertNull(TextNormalizer.toFtsMatchQuery("???"))
    }

    @Test
    fun `wzorzec LIKE otacza znormalizowany tekst procentami`() {
        assertEquals("%maka pszenna%", TextNormalizer.toLikePattern("Mąka Pszenna"))
    }
}
