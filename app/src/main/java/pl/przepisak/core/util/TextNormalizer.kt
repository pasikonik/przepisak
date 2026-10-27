package pl.przepisak.core.util

import java.text.Normalizer

/**
 * Normalizacja tekstu pod wyszukiwanie (RF-03).
 *
 * Nie polegamy na opcji `remove_diacritics` tokenizera SQLite, bo jej wersja
 * zależy od wersji systemu. Zamiast tego zapisujemy do indeksu FTS tekst już
 * pozbawiony diakrytyków i tak samo normalizujemy zapytanie — wynik jest
 * identyczny na każdym Androidzie.
 */
object TextNormalizer {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_SEARCHABLE = Regex("[^\\p{L}\\p{Nd} ]+")
    private val WHITESPACE = Regex("\\s+")

    /** Polskie znaki, których dekompozycja Unicode nie rozbija poprawnie. */
    private val EXPLICIT_MAP = mapOf(
        'ł' to 'l', 'Ł' to 'L',
        'ą' to 'a', 'Ą' to 'A',
        'ę' to 'e', 'Ę' to 'E',
        'ć' to 'c', 'Ć' to 'C',
        'ń' to 'n', 'Ń' to 'N',
        'ó' to 'o', 'Ó' to 'O',
        'ś' to 's', 'Ś' to 'S',
        'ź' to 'z', 'Ź' to 'Z',
        'ż' to 'z', 'Ż' to 'Z',
    )

    /** Usuwa diakrytyki, zachowując resztę tekstu i wielkość liter. */
    fun stripDiacritics(input: String): String {
        val pre = buildString(input.length) {
            input.forEach { append(EXPLICIT_MAP[it] ?: it) }
        }
        return COMBINING_MARKS.replace(Normalizer.normalize(pre, Normalizer.Form.NFD), "")
    }

    /**
     * Postać kanoniczna do indeksu i porównań: małe litery, bez diakrytyków,
     * bez znaków interpunkcyjnych, pojedyncze odstępy.
     */
    fun normalize(input: String): String =
        WHITESPACE.replace(NON_SEARCHABLE.replace(stripDiacritics(input).lowercase(), " "), " ").trim()

    /**
     * Buduje zapytanie MATCH dla FTS4: każdy token dostaje `*` (dopasowanie
     * przedrostkowe), tokeny łączymy iloczynem. Zwraca `null`, gdy nie ma czego szukać.
     */
    fun toFtsMatchQuery(rawQuery: String): String? {
        // Jednoliterowe tokeny pomijamy. „ciasto z jabłkami” nie może wymagać, by
        // przepis zawierał słowo zaczynające się na „z” — wyrzuciłoby to trafienia
        // w rodzaju „ciasto jabłkowe”, o które użytkownikowi właśnie chodzi.
        val tokens = normalize(rawQuery).split(' ').filter { it.length > 1 }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { "$it*" }
    }

    /** Escapuje wzorzec do użycia w `LIKE` (zapasowa ścieżka wyszukiwania). */
    fun toLikePattern(rawQuery: String): String = "%" + normalize(rawQuery).replace("%", "\\%") + "%"
}
