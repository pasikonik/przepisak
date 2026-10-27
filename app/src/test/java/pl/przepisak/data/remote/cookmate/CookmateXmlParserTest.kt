package pl.przepisak.data.remote.cookmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.przepisak.domain.model.ImportStrategy

class CookmateXmlParserTest {

    private val parser = CookmateXmlParser()

    private fun cookbook(vararg recipes: String): String =
        "<?xml version='1.0' encoding='UTF-8'?><cookbook>${recipes.joinToString("")}</cookbook>"

    private fun singleRecipe(xml: String) = parser.parse(cookbook(xml)).single()

    @Test
    fun `parsuje tytul skladniki i kroki`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Cynamonki</title>
                <ingredient><li>150 g mąki ziemniaczanej</li><li>100 g mąki ryżowej</li></ingredient>
                <recipetext><li>Wymieszaj składniki.</li><li>Uformuj kulki.</li></recipetext>
            </recipe>
            """.trimIndent(),
        )

        assertEquals("Cynamonki", recipe.imported.title)
        assertEquals(
            listOf("150 g mąki ziemniaczanej", "100 g mąki ryżowej"),
            recipe.imported.ingredientGroups.single().lines,
        )
        assertEquals(listOf("Wymieszaj składniki.", "Uformuj kulki."), recipe.imported.steps)
        assertEquals(ImportStrategy.COOKMATE_FILE, recipe.imported.strategy)
    }

    @Test
    fun `pomija puste elementy listy krokow uzywane jako odstepy`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Sos</title>
                <recipetext><li>Krok pierwszy.</li><li /><li>Krok drugi.</li></recipetext>
            </recipe>
            """.trimIndent(),
        )

        assertEquals(listOf("Krok pierwszy.", "Krok drugi."), recipe.imported.steps)
    }

    @Test
    fun `czyta opis z wielu elementow li`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Deser</title>
                <description><li>Pierwsze zdanie.</li><li>Drugie zdanie.</li></description>
            </recipe>
            """.trimIndent(),
        )

        assertEquals("Pierwsze zdanie.\nDrugie zdanie.", recipe.imported.description)
    }

    @Test
    fun `brakujacy opis to null`() {
        val recipe = singleRecipe("<recipe><title>Bez opisu</title><description /></recipe>")
        assertNull(recipe.imported.description)
    }

    @Test
    fun `rozpoznaje czas w minutach i godzinach`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Czasy</title>
                <preptime>1h00m</preptime>
                <cooktime>45min</cooktime>
                <totaltime>24M</totaltime>
            </recipe>
            """.trimIndent(),
        )

        assertEquals(60, recipe.imported.prepTimeMinutes)
        assertEquals(45, recipe.imported.cookTimeMinutes)
        assertEquals(24, recipe.imported.totalTimeMinutes)
    }

    @Test
    fun `brak czasu to null a nie zero`() {
        val recipe = singleRecipe("<recipe><title>Bez czasu</title><preptime /><cooktime></cooktime></recipe>")
        assertNull(recipe.imported.prepTimeMinutes)
        assertNull(recipe.imported.cookTimeMinutes)
    }

    @Test
    fun `czysta liczba porcji bez etykiety`() {
        val recipe = singleRecipe("<recipe><title>Porcje</title><quantity>4</quantity></recipe>")
        assertEquals(4, recipe.imported.servings)
        assertNull(recipe.imported.servingsLabel)
    }

    @Test
    fun `liczba porcji ze slowna etykieta`() {
        val recipe = singleRecipe("<recipe><title>Porcje</title><quantity>2 porcje</quantity></recipe>")
        assertEquals(2, recipe.imported.servings)
        assertEquals("porcje", recipe.imported.servingsLabel)
    }

    @Test
    fun `zakres porcji bierze dolna granice`() {
        val recipe = singleRecipe("<recipe><title>Porcje</title><quantity>2-3</quantity></recipe>")
        assertEquals(2, recipe.imported.servings)
    }

    @Test
    fun `brak liczby porcji to null`() {
        val recipe = singleRecipe("<recipe><title>Bez porcji</title><quantity /></recipe>")
        assertNull(recipe.imported.servings)
        assertNull(recipe.imported.servingsLabel)
    }

    @Test
    fun `mapuje kategorie zrodlo adres i komentarze`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Metadane</title>
                <url>https://przyklad.pl/przepis</url>
                <source><li>Jan Kowalski</li></source>
                <category>Desery</category>
                <comments><li>23 cm tortownica</li></comments>
            </recipe>
            """.trimIndent(),
        )

        assertEquals("https://przyklad.pl/przepis", recipe.imported.sourceUrl)
        assertEquals("Jan Kowalski", recipe.imported.sourceName)
        assertEquals("Desery", recipe.imported.categoryName)
        assertTrue(recipe.imported.notes!!.contains("23 cm tortownica"))
    }

    @Test
    fun `dolacza wartosci odzywcze do notatek`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Odzywcze</title>
                <nutrition><li>calories : 159 calories</li><li>proteinContent : 2.7 g</li></nutrition>
            </recipe>
            """.trimIndent(),
        )

        assertTrue(recipe.imported.notes!!.contains("calories : 159 calories"))
        assertTrue(recipe.imported.notes!!.contains("proteinContent : 2.7 g"))
    }

    @Test
    fun `rozpoznaje ocene ale zero traktuje jako brak`() {
        val oceniony = singleRecipe("<recipe><title>A</title><rating>5</rating></recipe>")
        assertEquals(5, oceniony.rating)

        val nieoceniony = singleRecipe("<recipe><title>B</title><rating>0</rating></recipe>")
        assertNull(nieoceniony.rating)
    }

    @Test
    fun `wylapuje nazwe pliku lokalnego zdjecia ze sciezki`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Ze zdjeciem</title>
                <image><path>/storage/emulated/0/Android/data/fr.cookbook/files/Pictures/Cynamonki.jpg</path></image>
            </recipe>
            """.trimIndent(),
        )

        assertEquals("Cynamonki.jpg", recipe.localImageFileName)
    }

    @Test
    fun `brak lokalnej sciezki to null a zdalny adres trafia do imageUrl`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Zdalne zdjecie</title>
                <imageurl>https://przyklad.pl/zdjecie.jpg</imageurl>
                <image><description /></image>
            </recipe>
            """.trimIndent(),
        )

        assertNull(recipe.localImageFileName)
        assertEquals("https://przyklad.pl/zdjecie.jpg", recipe.imported.imageUrl)
    }

    @Test
    fun `usuwa znaczniki html osadzone w tekscie skladnika`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Sernik</title>
                <ingredient><li>&lt;b&gt;Spód&lt;/b&gt;:</li><li>200 g herbatnikow</li></ingredient>
            </recipe>
            """.trimIndent(),
        )

        assertEquals(
            listOf("Spód:", "200 g herbatnikow"),
            recipe.imported.ingredientGroups.single().lines,
        )
    }

    @Test
    fun `rozbija zlepiona przez html liste na osobne linie`() {
        val recipe = singleRecipe(
            """
            <recipe>
                <title>Tort</title>
                <ingredient><li>&lt;h2&gt;&lt;strong&gt;Skladniki&lt;/strong&gt;&lt;/h2&gt;Spod:&lt;ul&gt;&lt;li&gt;200 g daktyli&lt;/li&gt;&lt;li&gt;50 g agaru&lt;/li&gt;&lt;/ul&gt;</li></ingredient>
            </recipe>
            """.trimIndent(),
        )

        assertEquals(
            listOf("Skladniki", "Spod:", "200 g daktyli", "50 g agaru"),
            recipe.imported.ingredientGroups.single().lines,
        )
    }

    @Test
    fun `parsuje wiele przepisow z jednego pliku w kolejnosci`() {
        val recipes = parser.parse(
            cookbook(
                "<recipe><title>Pierwszy</title></recipe>",
                "<recipe><title>Drugi</title></recipe>",
            ),
        )

        assertEquals(listOf("Pierwszy", "Drugi"), recipes.map { it.imported.title })
    }
}
