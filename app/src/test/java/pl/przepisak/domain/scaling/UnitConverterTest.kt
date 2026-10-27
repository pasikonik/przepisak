package pl.przepisak.domain.scaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.Quantity

class UnitConverterTest {

    private val converter = UnitConverter()

    @Test
    fun `przelicza masy`() {
        assertEquals(1000.0, converter.convert(1.0, MeasureUnit.KILOGRAM, MeasureUnit.GRAM)!!, DELTA)
        assertEquals(0.25, converter.convert(250.0, MeasureUnit.GRAM, MeasureUnit.KILOGRAM)!!, DELTA)
        assertEquals(100.0, converter.convert(10.0, MeasureUnit.DEKAGRAM, MeasureUnit.GRAM)!!, DELTA)
    }

    @Test
    fun `przelicza objetosci wraz z miarami kuchennymi`() {
        assertEquals(250.0, converter.convert(1.0, MeasureUnit.CUP, MeasureUnit.MILLILITER)!!, DELTA)
        assertEquals(3.0, converter.convert(1.0, MeasureUnit.TABLESPOON, MeasureUnit.TEASPOON)!!, DELTA)
        assertEquals(1000.0, converter.convert(1.0, MeasureUnit.LITER, MeasureUnit.MILLILITER)!!, DELTA)
    }

    @Test
    fun `odmawia przeliczenia miedzy roznymi wymiarami`() {
        assertNull(converter.convert(1.0, MeasureUnit.GRAM, MeasureUnit.MILLILITER))
        assertNull(converter.convert(1.0, MeasureUnit.CUP, MeasureUnit.KILOGRAM))
        assertNull(converter.convert(1.0, MeasureUnit.PIECE, MeasureUnit.GRAM))
    }

    @Test
    fun `odmawia przeliczenia miedzy roznymi jednostkami liczonymi na sztuki`() {
        assertNull(converter.convert(1.0, MeasureUnit.CLOVE, MeasureUnit.SLICE))
        assertNull(converter.convert(1.0, MeasureUnit.BUNCH, MeasureUnit.PIECE))
    }

    @Test
    fun `pozwala przeliczyc polowke na sztuke`() {
        assertEquals(1.0, converter.convert(2.0, MeasureUnit.HALF_PIECE, MeasureUnit.PIECE)!!, DELTA)
    }

    @Test
    fun `ta sama jednostka zwraca te sama wartosc`() {
        assertEquals(7.0, converter.convert(7.0, MeasureUnit.GRAM, MeasureUnit.GRAM)!!, DELTA)
    }

    @Test
    fun `podnosi gramy do kilogramow gdy wartosc jest duza`() {
        val (quantity, unit) = converter.prettify(Quantity(1500.0), MeasureUnit.GRAM)
        assertEquals(MeasureUnit.KILOGRAM, unit)
        assertEquals(1.5, quantity.amount, DELTA)
    }

    @Test
    fun `zostawia gramy gdy kilogramy dalyby wartosc mniejsza od jednosci`() {
        val (quantity, unit) = converter.prettify(Quantity(500.0), MeasureUnit.GRAM)
        assertEquals(MeasureUnit.GRAM, unit)
        assertEquals(500.0, quantity.amount, DELTA)
    }

    @Test
    fun `schodzi z litrow na mililitry`() {
        val (quantity, unit) = converter.prettify(Quantity(0.25), MeasureUnit.LITER)
        assertEquals(MeasureUnit.MILLILITER, unit)
        assertEquals(250.0, quantity.amount, DELTA)
    }

    @Test
    fun `zamienia trzy lyzeczki na lyzke`() {
        val (quantity, unit) = converter.prettify(Quantity(3.0), MeasureUnit.TEASPOON)
        assertEquals(MeasureUnit.TABLESPOON, unit)
        assertEquals(1.0, quantity.amount, DELTA)
    }

    @Test
    fun `zamienia czesc lyzki na lyzeczke`() {
        val (quantity, unit) = converter.prettify(Quantity(1.0 / 3), MeasureUnit.TABLESPOON)
        assertEquals(MeasureUnit.TEASPOON, unit)
        assertEquals(1.0, quantity.amount, DELTA)
    }

    @Test
    fun `nie wychodzi z rodziny miar kuchennych na metryczne`() {
        // Kto podał szklanki, chce szklanek — nie 500 ml.
        val (_, unit) = converter.prettify(Quantity(2.0), MeasureUnit.CUP)
        assertEquals(MeasureUnit.CUP, unit)
    }

    @Test
    fun `nie rusza jednostek umownych ani liczonych na sztuki`() {
        assertEquals(MeasureUnit.PINCH, converter.prettify(Quantity(4.0), MeasureUnit.PINCH).second)
        assertEquals(MeasureUnit.PIECE, converter.prettify(Quantity(12.0), MeasureUnit.PIECE).second)
        assertEquals(MeasureUnit.HANDFUL, converter.prettify(Quantity(2.0), MeasureUnit.HANDFUL).second)
    }

    @Test
    fun `bez jednostki nic nie zmienia`() {
        val (quantity, unit) = converter.prettify(Quantity(3.0), null)
        assertNull(unit)
        assertEquals(3.0, quantity.amount, DELTA)
    }

    @Test
    fun `przelicza takze gorna granice zakresu`() {
        val (quantity, unit) = converter.prettify(Quantity(1000.0, 2000.0), MeasureUnit.GRAM)
        assertEquals(MeasureUnit.KILOGRAM, unit)
        assertEquals(1.0, quantity.amount, DELTA)
        assertEquals(2.0, quantity.amountMax!!, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001
    }
}
