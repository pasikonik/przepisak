package pl.przepisak.data.remote.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.przepisak.core.util.Outcome
import pl.przepisak.core.util.map
import pl.przepisak.data.remote.web.HtmlFetcher
import pl.przepisak.data.remote.web.HtmlTextExtractor
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dane o filmie z TikToka, Instagrama i Facebooka (RF-06).
 *
 * Z tych serwisów nie da się legalnie i bez tokenu pobrać transkrypcji, a często
 * nawet pełnego opisu. Bierzemy to, co jest publicznie dostępne: oEmbed TikToka
 * oraz znaczniki Open Graph na stronie. Import z takiego materiału jest z natury
 * niepełny i UI musi o tym uprzedzić.
 */
@Singleton
class OEmbedFetcher @Inject constructor(
    private val fetcher: HtmlFetcher,
    private val json: Json,
    private val textExtractor: HtmlTextExtractor,
) {

    suspend fun fetch(url: String, platform: String): Outcome<VideoDetails> =
        if (platform.equals("TikTok", ignoreCase = true)) {
            fetchTikTok(url)
        } else {
            fetchOpenGraph(url)
        }

    private suspend fun fetchTikTok(url: String): Outcome<VideoDetails> {
        val encoded = URLEncoder.encode(url, "UTF-8")
        return when (val result = fetcher.fetchText("https://www.tiktok.com/oembed?url=$encoded")) {
            is Outcome.Failure -> fetchOpenGraph(url)
            is Outcome.Success -> {
                val details = runCatching {
                    val root = json.parseToJsonElement(result.value).jsonObject
                    VideoDetails(
                        title = root.text("title"),
                        // oEmbed TikToka nie ma osobnego opisu — tytuł to cały podpis
                        // filmu, w którym autorzy zwykle wypisują składniki.
                        description = root.text("title"),
                        thumbnailUrl = root.text("thumbnail_url"),
                        channelName = root.text("author_name"),
                    )
                }.getOrNull()

                if (details?.hasUsableText == true) Outcome.Success(details) else fetchOpenGraph(url)
            }
        }
    }

    private suspend fun fetchOpenGraph(url: String): Outcome<VideoDetails> =
        fetcher.fetch(url).map { page ->
            VideoDetails(
                title = textExtractor.extractTitle(page.html),
                description = textExtractor.extractMainText(page.html, maxChars = 4_000)
                    .takeIf { it.isNotBlank() },
                thumbnailUrl = textExtractor.extractImage(page.html, page.finalUrl),
                channelName = textExtractor.extractSiteName(page.html, page.finalUrl),
            )
        }

    private fun kotlinx.serialization.json.JsonObject.text(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
}
