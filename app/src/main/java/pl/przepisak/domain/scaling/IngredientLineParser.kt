package pl.przepisak.domain.scaling

import pl.przepisak.core.util.TextNormalizer
import pl.przepisak.domain.model.Ingredient
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.Quantity
import javax.inject.Inject

/** Rozłożona linia składnika. [rawText] zawsze trzyma oryginał. */
data class ParsedIngredientLine(
    val quantity: Quantity? = null,
    val unit: MeasureUnit? = null,
    val unitLabel: String? = null,
    val name: String = "",
    val note: String? = null,
    val isOptional: Boolean = false,
    val isScalable: Boolean = true,
    val rawText: String = "",
)

/**
 * Rozkłada linię przepisu w rodzaju „250 g mąki pszennej (przesianej)” na ilość,
 * jednostkę, nazwę i uwagę.
 *
 * Świadomie **nie** sprowadzamy nazwy do mianownika — „mąki pszennej” zostaje jak
 * jest. Odmiana polskich nazw produktów wymagałaby słownika, a błędna normalizacja
 * („mąki” → „mąk”) byłaby gorsza od zostawienia formy ze źródła. Do wyszukiwania
 * i dopasowania do bazy wagowej służy osobno znormalizowana nazwa.
 */
class IngredientLineParser @Inject constructor(
    private val quantityParser: QuantityParser,
) {

    fun parse(line: String): ParsedIngredientLine {
        val raw = line.trim()
        if (raw.isEmpty()) return ParsedIngredientLine(rawText = line)

        var work = raw.replace(BULLET_PREFIX, "").trim()

        val isOptional = OPTIONAL_MARKERS.any { work.contains(it, ignoreCase = true) }
        if (isOptional) {
            work = work.replace(OPTIONAL_CLEANUP, " ").trim().trim(':', '-', '–').trim()
        }

        // „do smaku”, „wg uznania” — ilości tu nie ma i skalować nie ma czego.
        val isScalable = TO_TASTE_MARKERS.none { work.contains(it, ignoreCase = true) }

        var note: String? = null
        PARENTHESISED_NOTE.find(work)?.let { match ->
            note = match.groupValues[1].trim().ifBlank { null }
            work = work.removeRange(match.range).trim()
        }

        val amountLength = quantityParser.matchLengthAtStart(work)
        var quantity: Quantity? = null
        if (amountLength > 0) {
            quantity = quantityParser.parse(work.substring(0, amountLength))
            if (quantity != null) work = work.substring(amountLength).trim()
        }

        var unit: MeasureUnit? = null
        var unitLabel: String? = null
        val consumed = matchUnit(work)
        if (consumed != null) {
            unit = consumed.first
            unitLabel = consumed.second
            work = work.substring(consumed.third).trim()
            // „szczypta soli”, „szklanka mleka” — jednostka bez liczby oznacza jedną sztukę.
            if (quantity == null && unit != null) quantity = Quantity(1.0)
        }

        // Uwaga po przecinku tylko wtedy, gdy wygląda na opis przygotowania
        // („, posiekany”), a nie na drugi składnik („sól, pieprz”).
        if (note == null) {
            val commaIndex = work.indexOf(',')
            if (commaIndex > 0) {
                val candidate = work.substring(commaIndex + 1).trim()
                if (candidate.isNotEmpty() && looksLikePreparationNote(candidate)) {
                    note = candidate
                    work = work.substring(0, commaIndex).trim()
                }
            }
        }

        var name = work.trim().trim('-', '–', '—', ':', ';', '.').trim()
        if (name.isEmpty() && note != null) {
            name = note.orEmpty()
            note = null
        }

        return ParsedIngredientLine(
            quantity = quantity,
            unit = unit,
            unitLabel = unitLabel,
            name = name,
            note = note,
            isOptional = isOptional,
            isScalable = isScalable && !(quantity == null && name.isEmpty()),
            rawText = raw,
        )
    }

    /** Rozkłada wklejony blok tekstu — po jednym składniku w linii. */
    fun parseMany(text: String): List<ParsedIngredientLine> =
        text.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { parse(it) }

    fun toIngredient(line: String, position: Int): Ingredient = parse(line).toIngredient(position)

    fun toIngredients(text: String): List<Ingredient> =
        parseMany(text).mapIndexed { index, parsed -> parsed.toIngredient(index) }

    /**
     * Dopasowuje jednostkę na początku tekstu, próbując od najdłuższej możliwej
     * nazwy („łyżka stołowa”) do jednego słowa. Zwraca trójkę
     * (jednostka, dosłowna etykieta, liczba znaków do usunięcia).
     */
    private fun matchUnit(text: String): Triple<MeasureUnit?, String?, Int>? {
        if (text.isEmpty()) return null
        val words = WORD_SPLIT.findAll(text).take(MeasureUnit.maxAliasWords).toList()
        if (words.isEmpty()) return null

        for (count in words.size downTo 1) {
            val end = words[count - 1].range.last + 1
            val candidate = text.substring(0, end)
            MeasureUnit.fromText(candidate)?.let { return Triple(it, null, end) }
        }

        // Słowo jednostkopodobne, którego nie mamy w modelu — zapisujemy dosłownie,
        // żeby nie zgubić informacji („1 kostka masła”).
        val first = words.first()
        val firstWord = text.substring(0, first.range.last + 1)
        if (TextNormalizer.normalize(firstWord) in UNIT_LIKE_WORDS) {
            return Triple(null, firstWord.trim(), first.range.last + 1)
        }
        return null
    }

    private fun looksLikePreparationNote(candidate: String): Boolean {
        val normalized = TextNormalizer.normalize(candidate)
        if (normalized.isEmpty()) return false
        if (PREPARATION_KEYWORDS.any { normalized.contains(it) }) return true
        val firstWord = normalized.substringBefore(' ')
        return PARTICIPLE_SUFFIXES.any { firstWord.length > 4 && firstWord.endsWith(it) }
    }

    private companion object {
        val BULLET_PREFIX = Regex("^\\s*(?:[-*•–—▪·]+\\s*|\\d+[.)]\\s+)")
        val PARENTHESISED_NOTE = Regex("\\(([^)]*)\\)")
        val WORD_SPLIT = Regex("[\\p{L}.]+")

        val OPTIONAL_MARKERS = listOf("opcjonalnie", "opcjonalny", "opcjonalna", "opcjonalne", "do wyboru")
        val OPTIONAL_CLEANUP = Regex("\\(?\\s*opcjonaln\\w*\\s*\\)?|\\(?\\s*do wyboru\\s*\\)?", RegexOption.IGNORE_CASE)

        val TO_TASTE_MARKERS = listOf(
            "do smaku", "wg uznania", "według uznania", "wedlug uznania",
            "wg gustu", "według gustu", "do posypania", "ile potrzeba",
        )

        /** Rdzenie słów, które jednoznacznie opisują sposób przygotowania. */
        val PREPARATION_KEYWORDS = listOf(
            "posiekan", "pokrojon", "startym", "starte", "starta", "starty",
            "roztopion", "rozpuszczon", "schlodzon", "ogrzan", "podgrzan",
            "przesian", "obran", "umyt", "drobno", "grubo", "swiez", "suszon",
            "mielon", "mieszan", "temperatur", "pokojow", "miekk", "twardy",
            "w kostce", "w plastrach", "bez skorki", "ze skorka", "opcjonalnie",
        )

        /**
         * Końcówki imiesłowów i przymiotników — „przesiana”, „roztopione”, „ciepłego”.
         * Sprawdzamy je wyłącznie na tekście PO PRZECINKU, więc „mleko kokosowe”
         * (bez przecinka) nie zostanie potraktowane jako uwaga.
         */
        val PARTICIPLE_SUFFIXES = listOf(
            "ny", "na", "ne", "ty", "ta", "te", "ony", "ona", "one",
            "ego", "ej", "ych", "ymi", "any", "ana", "ane",
        )

        /**
         * Słowa, które w przepisach pełnią rolę jednostki, ale nie mają
         * sensownego przelicznika, więc nie są modelowane jako [MeasureUnit].
         */
        val UNIT_LIKE_WORDS = setOf(
            "kostka", "kostki", "kostek",
            "lodyga", "lodygi", "lodyg",
            "glowka", "glowki", "glowek",
            "korzen", "korzenie",
            "strak", "straki", "strakow",
            "galka", "galki", "galek",
            "arkusz", "arkusze", "arkuszy",
            "porcja", "porcje", "porcji",
            "butelka", "butelki", "butelek",
            "karton", "kartony",
            "kubeczek", "kubeczki",
        )
    }
}

/** Mapuje rozłożoną linię na składnik gotowy do zapisu. */
fun ParsedIngredientLine.toIngredient(position: Int): Ingredient = Ingredient(
    position = position,
    name = name.ifBlank { rawText },
    quantity = quantity,
    unit = unit,
    unitLabel = unitLabel,
    note = note,
    isOptional = isOptional,
    isScalable = isScalable,
    rawText = rawText,
)
