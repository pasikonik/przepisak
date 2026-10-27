package pl.przepisak.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.przepisak.domain.model.ImportSource

class UrlClassifierTest {

    private fun youTubeId(url: String): String? =
        (UrlClassifier.classify(url) as? ImportSource.YouTubeVideo)?.videoId

    @Test
    fun `rozpoznaje klasyczny adres YouTube`() {
        assertEquals("dQw4w9WgXcQ", youTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `rozpoznaje skrocony adres youtu_be`() {
        assertEquals("dQw4w9WgXcQ", youTubeId("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `rozpoznaje adres Shorts`() {
        assertEquals("dQw4w9WgXcQ", youTubeId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun `rozpoznaje adres mobilny i osadzony`() {
        assertEquals("dQw4w9WgXcQ", youTubeId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", youTubeId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun `rozpoznaje adres z dodatkowymi parametrami`() {
        assertEquals(
            "dQw4w9WgXcQ",
            youTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&list=PLabc"),
        )
    }

    @Test
    fun `adres YouTube bez identyfikatora filmu traktuje jak zwykla strone`() {
        val source = UrlClassifier.classify("https://www.youtube.com/@jakiskanal")
        assertTrue(source is ImportSource.WebPage)
    }

    @Test
    fun `rozpoznaje krotkie filmy z innych serwisow`() {
        val tiktok = UrlClassifier.classify("https://www.tiktok.com/@user/video/123456789")
        assertEquals("TikTok", (tiktok as ImportSource.ShortVideo).platform)

        val instagram = UrlClassifier.classify("https://www.instagram.com/reel/Cabc123/")
        assertEquals("Instagram", (instagram as ImportSource.ShortVideo).platform)
    }

    @Test
    fun `zwykla strona z przepisem to WebPage`() {
        val source = UrlClassifier.classify("https://kwestiasmaku.com/przepis/nalesniki")
        assertTrue(source is ImportSource.WebPage)
        assertEquals("https://kwestiasmaku.com/przepis/nalesniki", source!!.url)
    }

    @Test
    fun `odrzuca adresy ktore nie sa http`() {
        assertNull(UrlClassifier.classify("ftp://example.com/plik"))
        assertNull(UrlClassifier.classify("mailto:kto@example.com"))
        assertNull(UrlClassifier.classify("po prostu tekst"))
    }

    @Test
    fun `dopisuje schemat adresom zaczynajacym sie od www`() {
        val source = UrlClassifier.classify("www.kwestiasmaku.com/przepis")
        assertEquals("https://www.kwestiasmaku.com/przepis", source!!.url)
    }

    @Test
    fun `wyluskuje adres z tekstu udostepnienia`() {
        // YouTube udostępnia „tytuł + link”.
        val shared = "Najlepsze naleśniki\nhttps://youtu.be/dQw4w9WgXcQ"
        assertEquals("https://youtu.be/dQw4w9WgXcQ", UrlClassifier.extractFirstUrl(shared))
    }

    @Test
    fun `obcina znaki interpunkcyjne na koncu adresu`() {
        assertEquals(
            "https://example.com/przepis",
            UrlClassifier.extractFirstUrl("Zobacz to: https://example.com/przepis."),
        )
    }

    @Test
    fun `wyluskuje pierwszy z kilku adresow`() {
        val shared = "https://a.example.com/jeden i https://b.example.com/dwa"
        assertEquals("https://a.example.com/jeden", UrlClassifier.extractFirstUrl(shared))
    }

    @Test
    fun `brak adresu w tekscie daje null`() {
        assertNull(UrlClassifier.extractFirstUrl("zwykły tekst bez linku"))
        assertNull(UrlClassifier.extractFirstUrl(""))
    }

    @Test
    fun `wklejony sam link importujemy ze strony`() {
        assertEquals(
            "https://kwestiasmaku.com/przepis/nalesniki",
            UrlClassifier.importableLink("https://kwestiasmaku.com/przepis/nalesniki"),
        )
    }

    @Test
    fun `link z krotkim podpisem jak z TikToka importujemy ze strony`() {
        val shared = "Zobacz film użytkownika @kuchniamarty na TikToku! #nalesniki #przepis " +
            "https://vm.tiktok.com/ZMh3kLp9x/"
        assertEquals("https://vm.tiktok.com/ZMh3kLp9x/", UrlClassifier.importableLink(shared))
    }

    @Test
    fun `przepis skopiowany z czatu z linkiem do zrodla to tekst a nie link`() {
        // Gdyby link wygrał, zamiast wklejonego przepisu pobralibyśmy stronę źródła.
        val chatAnswer = """
            ## Naleśniki na mleku

            **Składniki:**
            - 1 szklanka mąki pszennej
            - 2 jajka
            - 1 szklanka mleka
            - 1/2 szklanki wody gazowanej
            - szczypta soli
            - 2 łyżki oleju

            **Przygotowanie:**
            1. Zmiksuj mąkę z jajkami, mlekiem i wodą na gładkie ciasto.
            2. Dodaj olej i sól, odstaw na 15 minut.
            3. Smaż na rozgrzanej patelni z obu stron na złoty kolor.

            Źródło: https://www.kwestiasmaku.com/przepis/nalesniki
        """.trimIndent()

        assertNull(UrlClassifier.importableLink(chatAnswer))
    }

    @Test
    fun `tekst bez linku nie jest linkiem do importu`() {
        assertNull(UrlClassifier.importableLink("Naleśniki: mąka, mleko, jajka. Usmaż."))
    }

    @Test
    fun `usuwa parametry sledzace zachowujac identyfikator filmu`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlClassifier.cleanTrackingParams(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc&utm_source=share&feature=shared",
            ),
        )
    }

    @Test
    fun `adres bez parametrow zostaje nietkniety`() {
        val url = "https://example.com/przepis"
        assertEquals(url, UrlClassifier.cleanTrackingParams(url))
    }

    @Test
    fun `usuwa wszystkie parametry gdy wszystkie sluza sledzeniu`() {
        assertEquals(
            "https://example.com/przepis",
            UrlClassifier.cleanTrackingParams("https://example.com/przepis?utm_source=fb&fbclid=123"),
        )
    }
}
