package pl.przepisak.domain.scaling

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.Quantity

class QuantityFormatterTest {

    // --- liczby -----------------------------------------------------------

    @Test
    fun `duze liczby zapisuje bez czesci dziesietnej`() {
        assertEquals("250", QuantityFormatter.formatAmount(250.0))
        assertEquals("1000", QuantityFormatter.formatAmount(1000.0))
    }

    @Test
    fun `przyciaga wartosci do ulamkow kuchennych`() {
        assertEquals("½", QuantityFormatter.formatAmount(0.5))
        assertEquals("¼", QuantityFormatter.formatAmount(0.25))
        assertEquals("¾", QuantityFormatter.formatAmount(0.75))
        assertEquals("⅓", QuantityFormatter.formatAmount(1.0 / 3))
        assertEquals("⅔", QuantityFormatter.formatAmount(2.0 / 3))
        assertEquals("⅛", QuantityFormatter.formatAmount(0.125))
    }

    @Test
    fun `sklada czesc calkowita z ulamkiem`() {
        assertEquals("1½", QuantityFormatter.formatAmount(1.5))
        assertEquals("2¼", QuantityFormatter.formatAmount(2.25))
    }

    @Test
    fun `zaokragla wartosci bardzo bliskie liczby calkowitej`() {
        assertEquals("2", QuantityFormatter.formatAmount(2.004))
        assertEquals("3", QuantityFormatter.formatAmount(2.996))
    }

    @Test
    fun `dla wartosci dodatniej nigdy nie zwraca zera`() {
        // 0,08 nie pasuje do żadnego ułamka kuchennego, więc ma zostać liczbą.
        assertEquals("0,08", QuantityFormatter.formatAmount(0.08))
    }

    @Test
    fun `zero i wartosci ujemne zapisuje jako zero`() {
        assertEquals("0", QuantityFormatter.formatAmount(0.0))
        assertEquals("0", QuantityFormatter.formatAmount(-1.0))
    }

    @Test
    fun `uzywa przecinka jako separatora dziesietnego`() {
        assertEquals("1,44", QuantityFormatter.decimal(1.44))
        assertEquals("2", QuantityFormatter.decimal(2.0))
        assertEquals("12,5", QuantityFormatter.formatAmount(12.5))
    }

    // --- polska odmiana jednostek ----------------------------------------

    @Test
    fun `odmienia szklanke przez liczbe`() {
        assertEquals("szklanka", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 1.0))
        assertEquals("szklanki", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 2.0))
        assertEquals("szklanki", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 4.0))
        assertEquals("szklanek", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 5.0))
        assertEquals("szklanek", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 11.0))
    }

    @Test
    fun `stosuje wyjatek dla liczb od 12 do 14`() {
        assertEquals("szklanek", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 12.0))
        assertEquals("szklanek", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 13.0))
        assertEquals("szklanek", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 14.0))
    }

    @Test
    fun `dla liczb od 22 do 24 wraca do formy mnogiej`() {
        assertEquals("szklanki", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 22.0))
        assertEquals("szklanki", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 24.0))
        assertEquals("szklanek", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 25.0))
    }

    @Test
    fun `dla ulamka uzywa dopelniacza liczby pojedynczej`() {
        assertEquals("szklanki", QuantityFormatter.unitLabelFor(MeasureUnit.CUP, 1.5))
        assertEquals("łyżki", QuantityFormatter.unitLabelFor(MeasureUnit.TABLESPOON, 2.5))
        assertEquals("łyżeczki", QuantityFormatter.unitLabelFor(MeasureUnit.TEASPOON, 0.5))
    }

    @Test
    fun `odmienia lyzke lyzeczke i zabek`() {
        assertEquals("łyżka", QuantityFormatter.unitLabelFor(MeasureUnit.TABLESPOON, 1.0))
        assertEquals("łyżki", QuantityFormatter.unitLabelFor(MeasureUnit.TABLESPOON, 3.0))
        assertEquals("łyżek", QuantityFormatter.unitLabelFor(MeasureUnit.TABLESPOON, 5.0))
        assertEquals("łyżeczek", QuantityFormatter.unitLabelFor(MeasureUnit.TEASPOON, 6.0))
        assertEquals("ząbki", QuantityFormatter.unitLabelFor(MeasureUnit.CLOVE, 2.0))
        assertEquals("ząbków", QuantityFormatter.unitLabelFor(MeasureUnit.CLOVE, 7.0))
    }

    @Test
    fun `jednostki metryczne sa nieodmienne`() {
        assertEquals("g", QuantityFormatter.unitLabelFor(MeasureUnit.GRAM, 1.0))
        assertEquals("g", QuantityFormatter.unitLabelFor(MeasureUnit.GRAM, 250.0))
        assertEquals("ml", QuantityFormatter.unitLabelFor(MeasureUnit.MILLILITER, 5.0))
        assertEquals("szt.", QuantityFormatter.unitLabelFor(MeasureUnit.PIECE, 3.0))
    }

    // --- zapis pełny ------------------------------------------------------

    @Test
    fun `sklada ilosc z jednostka`() {
        assertEquals("250 g", QuantityFormatter.format(Quantity(250.0), MeasureUnit.GRAM))
        assertEquals("1½ szklanki", QuantityFormatter.format(Quantity(1.5), MeasureUnit.CUP))
        assertEquals("2 łyżki", QuantityFormatter.format(Quantity(2.0), MeasureUnit.TABLESPOON))
    }

    @Test
    fun `zakres odmienia wedlug gornej granicy`() {
        assertEquals(
            "1-2 ząbki",
            QuantityFormatter.format(Quantity(1.0, 2.0), MeasureUnit.CLOVE),
        )
    }

    @Test
    fun `uzywa doslownej etykiety gdy jednostka jest nieznana`() {
        assertEquals("1 kostka", QuantityFormatter.format(Quantity(1.0), null, "kostka"))
    }

    @Test
    fun `brak ilosci i jednostki daje pusty napis`() {
        assertEquals("", QuantityFormatter.format(null, null, null))
    }

    // --- czas -------------------------------------------------------------

    @Test
    fun `formatuje czas trwania w minutach`() {
        assertEquals("45 min", QuantityFormatter.formatMinutes(45))
        assertEquals("1 h 30 min", QuantityFormatter.formatMinutes(90))
        assertEquals("2 h", QuantityFormatter.formatMinutes(120))
        assertEquals("", QuantityFormatter.formatMinutes(null))
        assertEquals("", QuantityFormatter.formatMinutes(0))
    }

    @Test
    fun `formatuje odliczanie minutnika`() {
        assertEquals("05:30", QuantityFormatter.formatSeconds(330))
        assertEquals("00:09", QuantityFormatter.formatSeconds(9))
        assertEquals("1:05:30", QuantityFormatter.formatSeconds(3930))
        assertEquals("00:00", QuantityFormatter.formatSeconds(-5))
    }
}
