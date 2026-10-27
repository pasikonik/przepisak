package pl.przepisak.domain.importer

import pl.przepisak.domain.model.ImportSource

/**
 * Rozpoznaje, co dostaliśmy z systemowego „Udostępnij” (RF-04), i wybiera ścieżkę
 * importu: strona z przepisem, film na YouTube (jest opis i zwykle napisy) albo
 * krótki film z serwisu, z którego dostaniemy tylko opis.
 */
object UrlClassifier {

    /** Identyfikator filmu YouTube ma zawsze 11 znaków z tego alfabetu. */
    private const val VIDEO_ID = "[A-Za-z0-9_-]{11}"

    private val YOUTUBE_PATTERNS = listOf(
        Regex("[?&]v=($VIDEO_ID)"),
        Regex("youtu\\.be/($VIDEO_ID)"),
        Regex("/shorts/($VIDEO_ID)"),
        Regex("/embed/($VIDEO_ID)"),
        Regex("/live/($VIDEO_ID)"),
        Regex("/v/($VIDEO_ID)"),
    )

    private val YOUTUBE_HOSTS = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    private val SHORT_VIDEO_HOSTS = mapOf(
        "tiktok.com" to "TikTok",
        "instagram.com" to "Instagram",
        "facebook.com" to "Facebook",
        "fb.watch" to "Facebook",
        "vm.tiktok.com" to "TikTok",
    )

    /** Parametry służące wyłącznie śledzeniu — w polu „źródło” tylko zaśmiecają adres. */
    private val TRACKING_PARAMS = setOf(
        "si", "feature", "fbclid", "igshid", "igsh", "gclid", "ref", "ref_src",
        "spm", "_r", "_t", "is_from_webapp", "sender_device", "web_id",
    )

    private val URL_IN_TEXT = Regex("(?:https?://|www\\.)[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    /**
     * Ile znaków poza adresem może mieć udostępniony link. Tytuł filmu czy hasztagi
     * z TikToka mieszczą się w tym z zapasem, przepis z listą składników — nie.
     */
    private const val MAX_LINK_CAPTION_CHARS = 200

    fun classify(url: String): ImportSource? {
        val normalized = normalizeScheme(url.trim()) ?: return null
        val host = hostOf(normalized) ?: return null

        if (YOUTUBE_HOSTS.any { host == it || host.endsWith(".$it") }) {
            val videoId = YOUTUBE_PATTERNS.firstNotNullOfOrNull { pattern ->
                pattern.find(normalized)?.groupValues?.getOrNull(1)
            }
            // Adres z YouTube'a bez identyfikatora filmu (kanał, lista) nie jest
            // przepisem — traktujemy go jak zwykłą stronę.
            return if (videoId != null) {
                ImportSource.YouTubeVideo(normalized, videoId)
            } else {
                ImportSource.WebPage(normalized)
            }
        }

        SHORT_VIDEO_HOSTS.entries
            .firstOrNull { (candidate, _) -> host == candidate || host.endsWith(".$candidate") }
            ?.let { return ImportSource.ShortVideo(normalized, it.value) }

        return ImportSource.WebPage(normalized)
    }

    /**
     * Wyłuskuje pierwszy adres z udostępnionego tekstu. YouTube i TikTok udostępniają
     * „Tytuł filmu + link”, więc sam tekst nie jest adresem.
     */
    fun extractFirstUrl(text: String): String? {
        val match = URL_IN_TEXT.find(text) ?: return null
        // Kropka albo nawias na końcu zdania nie należą do adresu.
        val trimmed = match.value.trimEnd('.', ',', ';', ':', ')', ']', '}', '!', '?', '"', '\'')
        return normalizeScheme(trimmed)
    }

    /**
     * Adres do zaimportowania, jeśli wklejony tekst jest w praktyce samym linkiem —
     * ewentualnie z krótkim podpisem, jak przy udostępnianiu z YouTube'a czy TikToka.
     *
     * Dłuższy tekst daje `null`, nawet gdy zawiera adres: przepis skopiowany z czatu
     * często kończy się linkiem do źródła, a zapisać trzeba wklejony przepis, a nie
     * pobierać tamtą stronę.
     */
    fun importableLink(text: String): String? {
        val url = extractFirstUrl(text) ?: return null
        val caption = URL_IN_TEXT.replace(text, "").trim()
        return url.takeIf { caption.length <= MAX_LINK_CAPTION_CHARS }
    }

    /** Usuwa parametry śledzące, zachowując te, które identyfikują treść (np. `v`). */
    fun cleanTrackingParams(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url

        val base = url.substring(0, queryStart)
        val fragmentStart = url.indexOf('#', queryStart)
        val query = if (fragmentStart >= 0) url.substring(queryStart + 1, fragmentStart) else url.substring(queryStart + 1)
        val fragment = if (fragmentStart >= 0) url.substring(fragmentStart) else ""

        val kept = query.split('&')
            .filter { it.isNotBlank() }
            .filterNot { param ->
                val name = param.substringBefore('=').lowercase()
                name in TRACKING_PARAMS || name.startsWith("utm_")
            }

        return buildString {
            append(base)
            if (kept.isNotEmpty()) {
                append('?')
                append(kept.joinToString("&"))
            }
            append(fragment)
        }
    }

    /** Dopisuje `https://` adresom zaczynającym się od „www.”; odrzuca inne schematy. */
    private fun normalizeScheme(url: String): String? = when {
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> url
        url.startsWith("www.", ignoreCase = true) -> "https://$url"
        else -> null
    }

    private fun hostOf(url: String): String? {
        val withoutScheme = url.substringAfter("://", "")
        if (withoutScheme.isEmpty()) return null
        val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfter('@', authority).substringBefore(':').lowercase()
        return host.removePrefix("www.").ifBlank { null }
    }
}
