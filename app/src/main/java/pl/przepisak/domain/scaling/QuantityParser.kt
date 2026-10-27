package pl.przepisak.domain.scaling

import pl.przepisak.domain.model.Quantity
import javax.inject.Inject

/**
 * Rozpoznaje ilość zapisaną tak, jak piszą ją ludzie w przepisach.
 *
 * Obsługiwane zapisy: liczby całkowite i dziesiętne (z przecinkiem albo kropką),
 * ułamki zwykłe („1/2”, „1 1/2”), ułamki jako znaki Unicode („½”, „1¾”),
 * polskie słowa („pół”, „półtora”, „ćwierć”), zakresy („1-2”, „1–2”, „1 do 2”)
 * oraz przybliżenia („ok. 2”, „około 2”).
 */
class QuantityParser @Inject constructor() {

    /** Zwraca `null`, gdy w tekście nie ma rozpoznawalnej liczby. */
    fun parse(text: String): Quantity? {
        val cleaned = stripApproximation(text.trim().lowercase())
        if (cleaned.isEmpty()) return null

        RANGE.find(cleaned)?.let { match ->
            val from = parseSingle(match.groupValues[1])
            val to = parseSingle(match.groupValues[2])
            if (from != null && to != null) return Quantity.range(from, to)
        }

        return parseSingle(cleaned)?.let { Quantity(it) }
    }

    /**
     * Długość rozpoznanego zapisu liczbowego na początku tekstu — potrzebna
     * [IngredientLineParser], żeby wiedział, gdzie kończy się ilość, a zaczyna
     * jednostka. Zwraca 0, gdy tekst nie zaczyna się od liczby.
     */
    fun matchLengthAtStart(text: String): Int = LEADING_AMOUNT.find(text.lowercase())?.value?.length ?: 0

    private fun stripApproximation(text: String): String =
        text.replace(APPROXIMATION, "").trim()

    private fun parseSingle(raw: String): Double? {
        val text = stripApproximation(raw.trim())
        if (text.isEmpty()) return null

        WORD_AMOUNTS.entries.firstOrNull { text.startsWith(it.key) }?.let { return it.value }

        // Ułamek Unicode, opcjonalnie po liczbie całkowitej: „½”, „1½”, „1 ½”.
        UNICODE_FRACTION.find(text)?.let { match ->
            val whole = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val fraction = UNICODE_FRACTIONS[match.groupValues[2].first()] ?: return@let
            return whole + fraction
        }

        // Ułamek zwykły, opcjonalnie po liczbie całkowitej: „3/4”, „1 1/2”.
        // Część całkowita musi być oddzielona odstępem, inaczej „11/2” czytalibyśmy
        // jako 1 i 1/2 zamiast jedenastu drugich.
        SIMPLE_FRACTION.find(text)?.let { match ->
            val whole = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val numerator = match.groupValues[2].toDoubleOrNull() ?: return@let
            val denominator = match.groupValues[3].toDoubleOrNull() ?: return@let
            if (denominator == 0.0) return null
            return whole + numerator / denominator
        }

        DECIMAL.find(text)?.let { match ->
            return match.groupValues[1].replace(',', '.').toDoubleOrNull()
        }

        return null
    }

    private companion object {
        val UNICODE_FRACTIONS = mapOf(
            '¼' to 0.25, '½' to 0.5, '¾' to 0.75,
            '⅐' to 1.0 / 7, '⅑' to 1.0 / 9, '⅒' to 0.1,
            '⅓' to 1.0 / 3, '⅔' to 2.0 / 3,
            '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6, '⅘' to 0.8,
            '⅙' to 1.0 / 6, '⅚' to 5.0 / 6,
            '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
        )

        /** Najdłuższe formy pierwsze — inaczej „półtora” zostałoby zjedzone przez „pół”. */
        val WORD_AMOUNTS = linkedMapOf(
            "półtorej" to 1.5,
            "poltorej" to 1.5,
            "półtora" to 1.5,
            "poltora" to 1.5,
            "ćwierć" to 0.25,
            "cwierc" to 0.25,
            "pół" to 0.5,
            "pol" to 0.5,
        )

        const val FRACTION_CHARS = "¼½¾\\u2150-\\u215E"
        // Ułamek Unicode sprawdzamy przed samą liczbą dziesiętną — inaczej alternatywa
        // „\d+(?:[.,]\d+)?” dopasowałaby się już do samej części całkowitej „1” w „1½”
        // i nigdy nie doszłoby do gałęzi z ułamkiem (alternacja bierze pierwszy pasujący wariant).
        const val NUMBER = "(?:\\d+\\s+\\d+\\s*/\\s*\\d+|\\d+\\s*/\\s*\\d+|\\d*\\s*[$FRACTION_CHARS]|\\d+(?:[.,]\\d+)?)"

        // Dłuższe/pełne słowa muszą iść przed „ok\.?” — inaczej „około” zostałoby
        // ucięte do samego „ok”, bo to też pasujący (krótszy) wariant alternacji.
        val APPROXIMATION = Regex("^(?:około|okolo|circa|ca\\.?|ok\\.?|~|\\+/-|±)\\s*")
        val RANGE = Regex("^\\s*($NUMBER)\\s*(?:-|–|—|÷|\\bdo\\b)\\s*($NUMBER)\\s*$")
        val UNICODE_FRACTION = Regex("^(\\d+)?\\s*([$FRACTION_CHARS])")
        val SIMPLE_FRACTION = Regex("^(?:(\\d+)\\s+)?(\\d+)\\s*/\\s*(\\d+)")
        val DECIMAL = Regex("^(\\d+(?:[.,]\\d+)?)")
        val LEADING_AMOUNT = Regex(
            "^\\s*(?:około|okolo|circa|ca\\.?|ok\\.?|~)?\\s*" +
                "(?:$NUMBER(?:\\s*(?:-|–|—|÷|\\bdo\\b)\\s*$NUMBER)?|" +
                "półtorej|poltorej|półtora|poltora|ćwierć|cwierc|pół|pol)",
        )
    }
}
