package pl.przepisak.data.remote.web

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wyciąga z HTML-a to, co przypomina treść przepisu, oraz metadane Open Graph.
 *
 * Ma sens tylko jako przygotowanie danych dla Gemini: im mniej menu, reklam
 * i komentarzy trafi do modelu, tym trafniejszy wynik i mniejsze zużycie limitu.
 */
@Singleton
class HtmlTextExtractor @Inject constructor() {

    /** Selektory elementów, które nigdy nie zawierają przepisu. */
    private val noiseSelectors = listOf(
        "script", "style", "noscript", "iframe", "svg", "form", "nav", "header",
        "footer", "aside", "button", "template",
        "[class*=comment]", "[id*=comment]",
        "[class*=advert]", "[class*=reklam]", "[class*=banner]",
        "[class*=menu]", "[class*=nav]", "[class*=cookie]",
        "[class*=sidebar]", "[class*=footer]", "[class*=share]",
        "[class*=newsletter]", "[class*=related]", "[class*=popup]",
    )

    /** Miejsca, w których treść przepisu bywa najczęściej — w kolejności zaufania. */
    private val contentSelectors = listOf(
        "[itemtype*=Recipe]",
        "[class*=recipe]",
        "[class*=przepis]",
        "article",
        "main",
        "[role=main]",
    )

    fun extractMainText(html: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val document = parse(html) ?: return ""
        noiseSelectors.forEach { selector ->
            runCatching { document.select(selector).forEach { it.remove() } }
        }

        val candidate = contentSelectors
            .asSequence()
            .mapNotNull { selector -> runCatching { document.selectFirst(selector) }.getOrNull() }
            .map { it.text() }
            .firstOrNull { it.length > MIN_CONTENT_CHARS }

        val text = candidate ?: document.body()?.text().orEmpty()
        return text.collapseWhitespace().take(maxChars)
    }

    fun extractTitle(html: String): String? {
        val document = parse(html) ?: return null
        return metaContent(document, "og:title")
            ?: document.selectFirst("title")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
    }

    fun extractSiteName(html: String, url: String): String? {
        val document = parse(html)
        val fromMeta = document?.let { metaContent(it, "og:site_name") }
        if (!fromMeta.isNullOrBlank()) return fromMeta
        return hostOf(url)
    }

    fun extractImage(html: String, baseUrl: String): String? {
        val document = parse(html) ?: return null
        val candidate = metaContent(document, "og:image")
            ?: metaContent(document, "twitter:image")
            ?: document.selectFirst("article img[src]")?.attr("src")
            ?: document.selectFirst("img[src]")?.attr("src")
        return candidate?.trim()?.takeIf { it.isNotBlank() }?.let { absoluteUrl(it, baseUrl) }
    }

    /** Rozkodowuje encje HTML („&amp;nbsp;”, „&amp;amp;”) w krótkim fragmencie tekstu. */
    fun decodeEntities(text: String): String =
        if (text.contains('&')) {
            runCatching { Ksoup.parse(html = text).text() }.getOrDefault(text)
        } else {
            text
        }

    private fun parse(html: String): Document? =
        runCatching { Ksoup.parse(html = html) }.getOrNull()

    private fun metaContent(document: Document, property: String): String? =
        (
            document.selectFirst("meta[property=$property]")
                ?: document.selectFirst("meta[name=$property]")
            )
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /** Adresy w atrybutach bywają względne; bez rozwinięcia nie da się pobrać zdjęcia. */
    private fun absoluteUrl(candidate: String, baseUrl: String): String = when {
        candidate.startsWith("http://") || candidate.startsWith("https://") -> candidate
        candidate.startsWith("//") -> "https:$candidate"
        candidate.startsWith("/") -> originOf(baseUrl)?.let { it + candidate } ?: candidate
        else -> baseUrl.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
            ?.let { "$it/$candidate" }
            ?: candidate
    }

    private fun originOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return null
        val pathStart = url.indexOf('/', schemeEnd + 3)
        return if (pathStart < 0) url else url.substring(0, pathStart)
    }

    private fun hostOf(url: String): String? =
        originOf(url)?.substringAfter("://")?.removePrefix("www.")?.takeIf { it.isNotBlank() }

    private fun String.collapseWhitespace(): String =
        replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    private companion object {
        const val DEFAULT_MAX_CHARS = 40_000

        /** Krótszy fragment to zwykle nagłówek, a nie treść przepisu. */
        const val MIN_CONTENT_CHARS = 200
    }
}
