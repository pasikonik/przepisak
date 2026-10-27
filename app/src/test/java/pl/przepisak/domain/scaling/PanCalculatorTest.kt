package pl.przepisak.domain.scaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.przepisak.domain.model.BakingPan
import pl.przepisak.domain.model.PanShape
import kotlin.math.PI

class PanCalculatorTest {

    @Test
    fun `liczy pole tortownicy`() {
        assertEquals(PI * 100, PanCalculator.areaCm2(BakingPan.round(20.0))!!, DELTA)
        assertEquals(PI * 144, PanCalculator.areaCm2(BakingPan.round(24.0))!!, DELTA)
    }

    @Test
    fun `liczy pole blachy prostokatnej`() {
        assertEquals(840.0, PanCalculator.areaCm2(BakingPan.rectangle(24.0, 35.0))!!, DELTA)
        assertEquals(400.0, PanCalculator.areaCm2(BakingPan.square(20.0))!!, DELTA)
    }

    @Test
    fun `liczy pole formy z kominem jako pierscien`() {
        val expected = PI * (11.0 * 11.0 - 3.5 * 3.5)
        assertEquals(expected, PanCalculator.areaCm2(BakingPan.ring(22.0, 7.0))!!, DELTA)
    }

    @Test
    fun `forma z kominem bez komina to zwykle kolo`() {
        val pan = BakingPan(PanShape.RING, diameterCm = 22.0, innerDiameterCm = 0.0)
        assertEquals(PI * 121, PanCalculator.areaCm2(pan)!!, DELTA)
    }

    @Test
    fun `brak wymiarow daje brak pola`() {
        assertNull(PanCalculator.areaCm2(BakingPan(PanShape.ROUND)))
        assertNull(PanCalculator.areaCm2(BakingPan(PanShape.RECTANGLE, lengthCm = 20.0)))
        assertNull(PanCalculator.areaCm2(BakingPan(PanShape.ROUND, diameterCm = 0.0)))
    }

    @Test
    fun `komin szerszy niz forma jest odrzucany`() {
        assertNull(PanCalculator.areaCm2(BakingPan.ring(10.0, 12.0)))
    }

    @Test
    fun `liczy objetosc tylko gdy podano wysokosc`() {
        assertEquals(PI * 100 * 7, PanCalculator.volumeCm3(BakingPan.round(20.0, 7.0))!!, DELTA)
        assertNull(PanCalculator.volumeCm3(BakingPan.round(20.0)))
    }

    @Test
    fun `tortownica 20 na 24 cm to wspolczynnik 1_44`() {
        val conversion = PanCalculator.convert(BakingPan.round(20.0), BakingPan.round(24.0))
        assertNotNull(conversion)
        assertEquals(1.44, conversion!!.areaFactor, 0.001)
    }

    @Test
    fun `zmniejszenie formy daje wspolczynnik ponizej jednosci`() {
        val conversion = PanCalculator.convert(BakingPan.round(26.0), BakingPan.round(20.0))!!
        assertTrue(conversion.areaFactor < 1.0)
        assertEquals(400.0 / 676.0, conversion.areaFactor, 0.001)
    }

    @Test
    fun `przelicza tortownice na blache`() {
        val conversion = PanCalculator.convert(BakingPan.round(24.0), BakingPan.rectangle(24.0, 35.0))!!
        assertEquals(840.0 / (PI * 144), conversion.areaFactor, 0.001)
    }

    @Test
    fun `wspolczynnik objetosciowy pojawia sie tylko gdy obie formy maja wysokosc`() {
        val withHeights = PanCalculator.convert(BakingPan.round(20.0, 6.0), BakingPan.round(20.0, 9.0))!!
        assertEquals(1.5, withHeights.volumeFactor!!, DELTA)

        val missingHeight = PanCalculator.convert(BakingPan.round(20.0, 6.0), BakingPan.round(20.0))!!
        assertNull(missingHeight.volumeFactor)
    }

    @Test
    fun `brak wymiarow uniemozliwia przeliczenie`() {
        assertNull(PanCalculator.convert(BakingPan(PanShape.ROUND), BakingPan.round(20.0)))
        assertNull(PanCalculator.convert(BakingPan.round(20.0), BakingPan(PanShape.ROUND)))
    }

    @Test
    fun `opisuje formy po polsku`() {
        assertEquals("tortownica ⌀ 24 cm", PanCalculator.describe(BakingPan.round(24.0)))
        assertEquals("blacha 24 × 35 cm", PanCalculator.describe(BakingPan.rectangle(24.0, 35.0)))
        assertEquals("forma z kominem ⌀ 22 cm", PanCalculator.describe(BakingPan.ring(22.0, 7.0)))
    }

    @Test
    fun `wlasna etykieta ma pierwszenstwo nad opisem`() {
        assertEquals("Tortownica 24 cm", PanCalculator.describe(BakingPan.round(24.0, label = "Tortownica 24 cm")))
    }

    @Test
    fun `gotowe rozmiary form maja policzalne pola`() {
        BakingPan.presets.forEach { preset ->
            assertNotNull("Brak pola dla ${preset.label}", PanCalculator.areaCm2(preset))
        }
    }

    private companion object {
        const val DELTA = 0.001
    }
}
