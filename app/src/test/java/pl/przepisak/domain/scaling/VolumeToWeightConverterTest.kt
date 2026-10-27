package pl.przepisak.domain.scaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.przepisak.domain.model.IngredientDensity
import pl.przepisak.domain.model.MeasureUnit

class VolumeToWeightConverterTest {

    private val converter = VolumeToWeightConverter()

    /** Punkt odniesienia ze specyfikacji: 1 szklanka mąki pszennej = 160 g. */
    private val flour = IngredientDensity(
        name = "mąka pszenna",
        normalizedName = "maka pszenna",
        category = "Mąki i skrobie",
        gramsPerMl = 0.64,
        gramsPerCup = 160.0,
        gramsPerTablespoon = 9.0,
        gramsPerTeaspoon = 3.0,
    )

    /** Produkt bez zmierzonych naczyń — wyniki mają być oznaczone jako przybliżone. */
    private val water = IngredientDensity(
        name = "woda",
        normalizedName = "woda",
        category = "Płyny",
        gramsPerMl = 1.0,
    )

    private val egg = IngredientDensity(
        name = "jajko",
        normalizedName = "jajko",
        category = "Inne",
        gramsPerMl = 1.03,
        gramsPerPiece = 55.0,
    )

    @Test
    fun `szklanka maki pszennej wazy 160 gramow`() {
        val result = converter.toGrams(1.0, MeasureUnit.CUP, flour)!!
        assertEquals(160.0, result.grams, DELTA)
        assertFalse(result.isEstimated)
    }

    @Test
    fun `mnozy zmierzona mase przez liczbe naczyn`() {
        assertEquals(320.0, converter.toGrams(2.0, MeasureUnit.CUP, flour)!!.grams, DELTA)
        assertEquals(80.0, converter.toGrams(0.5, MeasureUnit.CUP, flour)!!.grams, DELTA)
    }

    @Test
    fun `korzysta ze zmierzonych wartosci dla lyzki i lyzeczki`() {
        assertEquals(9.0, converter.toGrams(1.0, MeasureUnit.TABLESPOON, flour)!!.grams, DELTA)
        assertEquals(3.0, converter.toGrams(1.0, MeasureUnit.TEASPOON, flour)!!.grams, DELTA)
    }

    @Test
    fun `wynik z gestosci jest oznaczony jako przyblizony`() {
        val result = converter.toGrams(100.0, MeasureUnit.MILLILITER, flour)!!
        assertEquals(64.0, result.grams, DELTA)
        assertTrue(result.isEstimated)
    }

    @Test
    fun `dla plynow bez zmierzonych naczyn liczy z gestosci`() {
        val result = converter.toGrams(1.0, MeasureUnit.CUP, water)!!
        assertEquals(250.0, result.grams, DELTA)
        assertTrue(result.isEstimated)
    }

    @Test
    fun `jednostki umowne zawsze daja wynik przyblizony`() {
        assertTrue(converter.toGrams(1.0, MeasureUnit.PINCH, flour)!!.isEstimated)
        assertTrue(converter.toGrams(1.0, MeasureUnit.HANDFUL, flour)!!.isEstimated)
    }

    @Test
    fun `liczy mase sztuki gdy jest podana`() {
        val result = converter.toGrams(3.0, MeasureUnit.PIECE, egg)!!
        assertEquals(165.0, result.grams, DELTA)
        assertFalse(result.isEstimated)
    }

    @Test
    fun `odmawia gdy dla jednostki nie ma masy`() {
        assertNull(converter.toGrams(1.0, MeasureUnit.PIECE, flour))
        assertNull(converter.toGrams(1.0, MeasureUnit.SLICE, flour))
        assertNull(converter.toGrams(1.0, MeasureUnit.GRAM, flour))
    }

    @Test
    fun `odrzuca ilosci niedodatnie`() {
        assertNull(converter.toGrams(0.0, MeasureUnit.CUP, flour))
        assertNull(converter.toGrams(-1.0, MeasureUnit.CUP, flour))
    }

    @Test
    fun `przelicza gramy na liczbe naczyn`() {
        assertEquals(2.0, converter.toVolume(320.0, MeasureUnit.CUP, flour)!!, DELTA)
        assertEquals(1.0, converter.toVolume(160.0, MeasureUnit.CUP, flour)!!, DELTA)
        assertEquals(5.0, converter.toVolume(45.0, MeasureUnit.TABLESPOON, flour)!!, DELTA)
    }

    @Test
    fun `przeliczenie na naczynia odmawia gdy brak danych`() {
        assertNull(converter.toVolume(100.0, MeasureUnit.PIECE, flour))
        assertNull(converter.toVolume(0.0, MeasureUnit.CUP, flour))
    }

    @Test
    fun `zwraca uzyta jednostke i ilosc w wyniku`() {
        val result = converter.toGrams(1.5, MeasureUnit.CUP, flour)!!
        assertEquals(MeasureUnit.CUP, result.unit)
        assertEquals(1.5, result.amount, DELTA)
        assertEquals(flour, result.density)
        assertEquals(240.0, result.grams, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001
    }
}
