package pl.przepisak.data.remote.web

import pl.przepisak.core.util.Outcome

/** Pobrana strona. [finalUrl] uwzględnia przekierowania — po nim budujemy adresy względne. */
data class FetchedPage(
    val requestedUrl: String,
    val finalUrl: String,
    val html: String,
    val contentType: String? = null,
)

/**
 * Pobieranie surowego HTML-a. Wydzielone za interfejs, żeby testy parserów mogły
 * podstawić zapisane strony bez ruszania sieci.
 */
interface HtmlFetcher {
    suspend fun fetch(url: String): Outcome<FetchedPage>

    /** Pobiera dowolny zasób tekstowy (np. plik z napisami). */
    suspend fun fetchText(url: String): Outcome<String>

    /** Pobiera plik binarny — używane do zdjęć z importu. */
    suspend fun fetchBytes(url: String): Outcome<ByteArray>
}
