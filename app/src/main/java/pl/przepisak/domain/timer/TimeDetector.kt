package pl.przepisak.domain.timer

import pl.przepisak.domain.model.DetectedTimer

/**
 * Znajduje czas w treści kroku, żeby użytkownik mógł uruchomić minutnik jednym
 * dotknięciem (RF-13).
 *
 * Dla zakresu („piecz 20-25 minut”) bierzemy **dolną** granicę. Ciasta nie da się
 * odpiec z powrotem: lepiej zadzwonić po 20 minutach i sprawdzić, a czas dołożyć
 * przyciskiem „+1 min”, niż wyjąć spalone po 25.
 *
 * Fałszywki („180 stopni”, „3 łyżki”, „200 ml”) odpadają same, bo wzorzec wymaga,
 * by bezpośrednio po liczbie stała jednostka czasu.
 */
object TimeDetector {

    /**
     * Budowa wzorca liczbowego:
     *  - `from`  — pierwsza liczba (całkowita lub z przecinkiem/kropką),
     *  - część po `(?:-|–|—|do)` — opcjonalna druga granica zakresu,
     *  - `unit`  — jednostka czasu; alternatywy uporządkowane od najdłuższej, żeby
     *              „minut” nie zostało skrócone do „min”,
     *  - `\b` po grupie jednostki chroni przed trafieniem w środek innego słowa
     *    („min” w „mineralnej”).
     */
    private val NUMERIC = Regex(
        "(?<from>\\d+(?:[.,]\\d+)?)" +
            "(?:\\s*(?:-|–|—|do)\\s*(?<to>\\d+(?:[.,]\\d+)?))?" +
            "\\s*(?<unit>godzin\\w*|godz\\.?|minut\\w*|min\\.?|sekund\\w*|sek\\.?|h|s)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Zapisy słowne bez liczby — częste w polskich przepisach. */
    private val WORD_PATTERNS: List<Pair<Regex, Int>> = listOf(
        Regex("półtorej\\s+godziny|poltorej\\s+godziny", RegexOption.IGNORE_CASE) to 90 * 60,
        Regex("pół\\s+godziny|pol\\s+godziny", RegexOption.IGNORE_CASE) to 30 * 60,
        Regex("kwadrans\\w*", RegexOption.IGNORE_CASE) to 15 * 60,
        // Po polskiej literze nie da się użyć `\b` (poza zakresem ASCII nie jest ona
        // znakiem słowa), dlatego koniec wyrazu wymuszamy negatywnym wyprzedzeniem.
        Regex("godzin[ęe](?![\\p{L}])", RegexOption.IGNORE_CASE) to 60 * 60,
    )

    /** Nie proponujemy minutników absurdalnych — powyżej doby to już nie krok przepisu. */
    private const val MAX_SECONDS = 24 * 60 * 60

    fun detect(text: String): List<DetectedTimer> {
        if (text.isBlank()) return emptyList()
        val found = mutableListOf<DetectedTimer>()

        NUMERIC.findAll(text).forEach { match ->
            val unitText = match.groups["unit"]?.value ?: return@forEach
            val multiplier = multiplierFor(unitText) ?: return@forEach
            // Dolna granica zakresu — patrz uzasadnienie w opisie obiektu.
            val amount = match.groups["from"]?.value?.replace(',', '.')?.toDoubleOrNull() ?: return@forEach
            val seconds = (amount * multiplier).toInt()
            if (seconds in 1..MAX_SECONDS) {
                found += DetectedTimer(
                    matchedText = match.value.trim(),
                    seconds = seconds,
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                )
            }
        }

        WORD_PATTERNS.forEach { (pattern, seconds) ->
            pattern.findAll(text).forEach { match ->
                val overlaps = found.any { match.range.first < it.endIndex && it.startIndex < match.range.last + 1 }
                if (!overlaps) {
                    found += DetectedTimer(
                        matchedText = match.value.trim(),
                        seconds = seconds,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                    )
                }
            }
        }

        return found
            .sortedBy { it.startIndex }
            .distinctBy { it.startIndex }
    }

    private fun multiplierFor(unitText: String): Int? {
        val unit = unitText.lowercase().removeSuffix(".")
        return when {
            unit.startsWith("godz") || unit == "h" -> 3600
            unit.startsWith("min") -> 60
            unit.startsWith("sek") || unit == "s" -> 1
            else -> null
        }
    }
}
