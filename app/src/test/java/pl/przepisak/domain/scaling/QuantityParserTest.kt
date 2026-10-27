package pl.przepisak.domain.scaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityParserTest {

    private val parser = QuantityParser()

    private fun amount(text: String): Double? = parser.parse(text)?.amount

    @Test
    fun `czyta liczbe calkowita`() {
        assertEquals(2.0, amount("2")!!, DELTA)
    }

    @Test
    fun `czyta liczbe z przecinkiem i z kropka`() {
        assertEquals(2.5, amount("2,5")!!, DELTA)
        assertEquals(2.5, amount("2.5")!!, DELTA)
    }

    @Test
    fun `czyta ulamek zwykly`() {
        assertEquals(0.75, amount("3/4")!!, DELTA)
        assertEquals(0.5, amount("1/2")!!, DELTA)
    }

    @Test
    fun `czyta liczbe mieszana z ulamkiem zwyklym`() {
        assertEquals(1.5, amount("1 1/2")!!, DELTA)
    }

    @Test
    fun `nie myli liczby mieszanej z ulamkiem niewlasciwym`() {
        // „11/2” to jedenaście drugich, a nie 1 i 1/2 — część całkowita wymaga odstępu.
        assertEquals(5.5, amount("11/2")!!, DELTA)
    }

    @Test
    fun `czyta ulamki zapisane znakiem Unicode`() {
        assertEquals(0.5, amount("½")!!, DELTA)
        assertEquals(0.25, amount("¼")!!, DELTA)
        assertEquals(0.75, amount("¾")!!, DELTA)
        assertEquals(1.0 / 3, amount("⅓")!!, DELTA)
    }

    @Test
    fun `czyta liczbe calkowita ze znakiem ulamka`() {
        assertEquals(1.5, amount("1½")!!, DELTA)
        assertEquals(2.25, amount("2 ¼")!!, DELTA)
    }

    @Test
    fun `czyta zakres z myslnikiem i polpauza`() {
        val dash = parser.parse("1-2")
        assertEquals(1.0, dash!!.amount, DELTA)
        assertEquals(2.0, dash.amountMax!!, DELTA)
        assertTrue(dash.isRange)

        val enDash = parser.parse("1–2")
        assertEquals(2.0, enDash!!.amountMax!!, DELTA)
    }

    @Test
    fun `czyta zakres zapisany slowem do`() {
        val range = parser.parse("2 do 3")
        assertEquals(2.0, range!!.amount, DELTA)
        assertEquals(3.0, range.amountMax!!, DELTA)
    }

    @Test
    fun `pomija okreslenia przyblizenia`() {
        assertEquals(2.0, amount("ok. 2")!!, DELTA)
        assertEquals(2.0, amount("około 2")!!, DELTA)
        assertEquals(3.0, amount("~3")!!, DELTA)
    }

    @Test
    fun `czyta polskie slowa okreslajace ilosc`() {
        assertEquals(0.5, amount("pół")!!, DELTA)
        assertEquals(1.5, amount("półtora")!!, DELTA)
        assertEquals(0.25, amount("ćwierć")!!, DELTA)
    }

    @Test
    fun `zwraca null gdy nie ma liczby`() {
        assertNull(parser.parse("mąka pszenna"))
        assertNull(parser.parse(""))
        assertNull(parser.parse("do smaku"))
    }

    @Test
    fun `odrzuca dzielenie przez zero`() {
        assertNull(parser.parse("1/0"))
    }

    @Test
    fun `mierzy dlugosc zapisu liczbowego na poczatku tekstu`() {
        assertEquals(3, parser.matchLengthAtStart("250 g mąki"))
        assertEquals(3, parser.matchLengthAtStart("1-2 ząbki czosnku"))
        assertEquals(5, parser.matchLengthAtStart("1 1/2 łyżki cukru"))
        assertEquals(0, parser.matchLengthAtStart("sól do smaku"))
    }

    private companion object {
        const val DELTA = 0.0001
    }
}
