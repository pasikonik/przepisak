package pl.przepisak.data.remote.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.Outcome
import pl.przepisak.data.remote.web.HtmlFetcher
import javax.inject.Inject
import javax.inject.Singleton

/** Dane filmu potrzebne do odtworzenia przepisu. */
data class VideoDetails(
    val title: String? = null,
    val description: String? = null,
    val transcript: String? = null,
    val thumbnailUrl: String? = null,
    val channelName: String? = null,
) {
    val hasUsableText: Boolean
        get() = !transcript.isNullOrBlank() || !description.isNullOrBlank()
}

/**
 * Pobiera opis i napisy filmu z YouTube’a (RF-06).
 *
 * Świadomie nie korzystamy z YouTube Data API: wymagałoby klucza i rejestracji
 * projektu, a specyfikacja stawia warunek zerowego kosztu (RNF-01). Zamiast tego
 * czytamy dane, które YouTube umieszcza w kodzie strony odtwarzacza.
 *
 * To rozwiązanie jest z natury kruche — zmiana struktury strony po stronie Google’a
 * je zepsuje. Dlatego każdy etap jest osobno zabezpieczony, a brak napisów nie jest
 * traktowany jako błąd: zostaje jeszcze opis filmu.
 */
@Singleton
class YouTubeTranscriptFetcher @Inject constructor(
    private val fetcher: HtmlFetcher,
    private val json: Json,
) {

    suspend fun fetch(videoId: String): Outcome<VideoDetails> {
        val page = when (val result = fetcher.fetch("https://www.youtube.com/watch?v=$videoId&hl=pl")) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }

        val playerResponse = extractPlayerResponse(page.html)
            ?: return Outcome.Failure(AppError.ParsingFailed("nie znalazłem danych odtwarzacza"))

        val videoDetails = playerResponse["videoDetails"] as? JsonObject
        val title = videoDetails?.stringOrNull("title")
        val description = videoDetails?.stringOrNull("shortDescription")
        val channel = videoDetails?.stringOrNull("author")
        val thumbnail = videoDetails
            ?.let { it["thumbnail"] as? JsonObject }
            ?.let { it["thumbnails"] as? JsonArray }
            // Ostatnia miniatura na liście ma najwyższą rozdzielczość.
            ?.lastOrNull()
            ?.let { (it as? JsonObject)?.stringOrNull("url") }

        val transcript = findCaptionTrackUrl(playerResponse)?.let { downloadTranscript(it) }

        return Outcome.Success(
            VideoDetails(
                title = title,
                description = description,
                transcript = transcript,
                thumbnailUrl = thumbnail,
                channelName = channel,
            ),
        )
    }

    /**
     * Wyłuskuje obiekt `ytInitialPlayerResponse` ze strony.
     *
     * Zliczamy nawiasy klamrowe, zamiast dopasowywać wyrażeniem regularnym: JSON
     * jest strukturą zagnieżdżoną, a w opisach filmów regularnie występują klamry
     * i cudzysłowy, więc „weź wszystko do ostatniego `}`” albo „do pierwszego `}`”
     * dałoby śmieci. Trzeba też pomijać klamry wewnątrz napisów oraz sekwencje `\"`.
     */
    private fun extractPlayerResponse(html: String): JsonObject? {
        val marker = "ytInitialPlayerResponse"
        val markerIndex = html.indexOf(marker)
        if (markerIndex < 0) return null
        val start = html.indexOf('{', markerIndex)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        var index = start

        while (index < html.length) {
            val character = html[index]
            when {
                escaped -> escaped = false
                character == '\\' && inString -> escaped = true
                character == '"' -> inString = !inString
                !inString && character == '{' -> depth++
                !inString && character == '}' -> {
                    depth--
                    if (depth == 0) {
                        val raw = html.substring(start, index + 1)
                        return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                    }
                }
            }
            index++
        }
        return null
    }

    /**
     * Wybiera ścieżkę napisów: najpierw polska, potem angielska, na końcu pierwsza
     * dostępna. W obrębie języka wolimy napisy przygotowane przez człowieka niż
     * automatyczne rozpoznanie mowy (`kind == "asr"`) — te drugie mylą ilości.
     */
    private fun findCaptionTrackUrl(playerResponse: JsonObject): String? {
        val tracks = runCatching {
            playerResponse["captions"]
                ?.jsonObject
                ?.get("playerCaptionsTracklistRenderer")
                ?.jsonObject
                ?.get("captionTracks")
                ?.jsonArray
        }.getOrNull() ?: return null

        val candidates = tracks.mapNotNull { it as? JsonObject }
        if (candidates.isEmpty()) return null

        val preferredLanguages = listOf("pl", "en")
        for (language in preferredLanguages) {
            val manual = candidates.firstOrNull {
                it.stringOrNull("languageCode")?.startsWith(language) == true &&
                    it.stringOrNull("kind") != "asr"
            }
            if (manual != null) return manual.stringOrNull("baseUrl")

            val automatic = candidates.firstOrNull {
                it.stringOrNull("languageCode")?.startsWith(language) == true
            }
            if (automatic != null) return automatic.stringOrNull("baseUrl")
        }
        return candidates.first().stringOrNull("baseUrl")
    }

    /** Format `json3` daje tekst w `events[].segs[].utf8`. */
    private suspend fun downloadTranscript(baseUrl: String): String? {
        val body = when (val result = fetcher.fetchText("$baseUrl&fmt=json3")) {
            is Outcome.Failure -> return null
            is Outcome.Success -> result.value
        }

        return runCatching {
            val events = json.parseToJsonElement(body).jsonObject["events"]?.jsonArray ?: return null
            events
                .mapNotNull { event ->
                    (event as? JsonObject)
                        ?.get("segs")
                        ?.jsonArray
                        ?.mapNotNull { (it as? JsonObject)?.stringOrNull("utf8") }
                        ?.joinToString("")
                }
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
}
