package pl.przepisak.domain.scaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.przepisak.domain.model.MeasureUnit

class IngredientLineParserTest {

    private val parser = IngredientLineParser(QuantityParser())

    @Test
    fun `rozklada gramature i nazwe`() {
        val parsed = parser.parse("250 g mąki pszennej")
        assertEquals(250.0, parsed.quantity!!.amount, DELTA)
        assertEquals(MeasureUnit.GRAM, parsed.unit)
        assertEquals("mąki pszennej", parsed.name)
        assertNull(parsed.note)
    }

    @Test
    fun `rozpoznaje szklanki`() {
        val parsed = parser.parse("2 szklanki mleka")
        assertEquals(2.0, parsed.quantity!!.amount, DELTA)
        assertEquals(MeasureUnit.CUP, parsed.unit)
        assertEquals("mleka", parsed.name)
    }

    @Test
    fun `rozpoznaje ulamek i lyzke`() {
        val parsed = parser.parse("1½ łyżki cukru")
        assertEquals(1.5, parsed.quantity!!.amount, DELTA)
        assertEquals(MeasureUnit.TABLESPOON, parsed.unit)
        assertEquals("cukru", parsed.name)
    }

    @Test
    fun `rozpoznaje zakres ilosci`() {
        val parsed = parser.parse("1-2 ząbki czosnku")
        assertEquals(1.0, parsed.quantity!!.amount, DELTA)
        assertEquals(2.0, parsed.quantity!!.amountMax!!, DELTA)
        assertEquals(MeasureUnit.CLOVE, parsed.unit)
        assertEquals("czosnku", parsed.name)
    }

    @Test
    fun `jednostka bez liczby oznacza jedna sztuke`() {
        val parsed = parser.parse("szczypta soli")
        assertEquals(1.0, parsed.quantity!!.amount, DELTA)
        assertEquals(MeasureUnit.PINCH, parsed.unit)
        assertEquals("soli", parsed.name)
    }

    @Test
    fun `sol do smaku nie jest skalowana`() {
        val parsed = parser.parse("sól do smaku")
        assertNull(parsed.quantity)
        assertNull(parsed.unit)
        assertFalse(parsed.isScalable)
        assertEquals("sól do smaku", parsed.name)
    }

    @Test
    fun `rozpoznaje skladnik opcjonalny`() {
        val parsed = parser.parse("opcjonalnie: 50 g rodzynek")
        assertTrue(parsed.isOptional)
        assertEquals(50.0, parsed.quantity!!.amount, DELTA)
        assertEquals(MeasureUnit.GRAM, parsed.unit)
        assertEquals("rodzynek", parsed.name)
    }

    @Test
    fun `nawias na koncu staje sie uwaga`() {
        val parsed = parser.parse("100 g masła (miękkiego)")
        assertEquals("masła", parsed.name)
        assertEquals("miękkiego", parsed.note)
    }

    @Test
    fun `tekst po przecinku opisujacy przygotowanie staje sie uwaga`() {
        val parsed = parser.parse("2 marchewki, starte na tarce")
        assertEquals("marchewki", parsed.name)
        assertEquals("starte na tarce", parsed.note)
    }

    @Test
    fun `drugi skladnik po przecinku nie jest uwaga`() {
        val parsed = parser.parse("sól, pieprz")
        assertEquals("sól, pieprz", parsed.name)
        assertNull(parsed.note)
    }

    @Test
    fun `slowo po liczbie ktore nie jest jednostka zostaje w nazwie`() {
        val parsed = parser.parse("3 jajka")
        assertEquals(3.0, parsed.quantity!!.amount, DELTA)
        assertNull(parsed.unit)
        assertEquals("jajka", parsed.name)
    }

    @Test
    fun `rozpoznaje opakowanie jako jednostke`() {
        val parsed = parser.parse("1 opakowanie proszku do pieczenia")
        assertEquals(MeasureUnit.PACKAGE, parsed.unit)
        assertEquals("proszku do pieczenia", parsed.name)
    }

    @Test
    fun `slowo jednostkopodobne trafia do etykiety doslownej`() {
        val parsed = parser.parse("1 kostka masła")
        assertNull(parsed.unit)
        assertEquals("kostka", parsed.unitLabel)
        assertEquals("masła", parsed.name)
    }

    @Test
    fun `usuwa wypunktowanie i numeracje z poczatku linii`() {
        assertEquals("oliwy", parser.parse("- 2 łyżki oliwy").name)
        assertEquals("oliwy", parser.parse("• 2 łyżki oliwy").name)
        assertEquals("Mąka", parser.parse("1. Mąka").name)
    }

    @Test
    fun `zachowuje oryginalna linie`() {
        val line = "  250 g mąki pszennej (przesianej)  "
        assertEquals(line.trim(), parser.parse(line).rawText)
    }

    @Test
    fun `rozklada wklejona liste pomijajac puste linie`() {
        val parsed = parser.parseMany(
            """
            250 g mąki

            2 jajka
            szczypta soli
            """.trimIndent(),
        )
        assertEquals(3, parsed.size)
        assertEquals(MeasureUnit.GRAM, parsed[0].unit)
        assertEquals("jajka", parsed[1].name)
        assertEquals(MeasureUnit.PINCH, parsed[2].unit)
    }

    @Test
    fun `nadaje kolejne pozycje przy zamianie na skladniki`() {
        val ingredients = parser.toIngredients("250 g mąki\n2 jajka")
        assertEquals(0, ingredients[0].position)
        assertEquals(1, ingredients[1].position)
        assertEquals("mąki", ingredients[0].name)
    }

    private companion object {
        const val DELTA = 0.0001
    }
}
