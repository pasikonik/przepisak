package pl.przepisak.data.remote.web

import kotlin.math.roundToInt

/**
 * Odczytuje czas trwania z pól Schema.org oraz z zapisów spotykanych w polskich
 * przepisach.
 *
 * Nie opieramy się na `java.time.Duration.parse`: potyka się na wariantach z dniami
 * („P0DT0H25M”, które publikuje część wtyczek WordPressa) i w ogóle nie rozumie
 * zapisów typu „1,5 godziny”. Własny wzorzec jest tu krótszy niż obsługa wyjątków.
 */
object IsoDurationParser {

    private val ISO = Regex(
        "^P(?:(?<days>\\d+)D)?(?:T(?:(?<hours>\\d+(?:[.,]\\d+)?)H)?" +
            "(?:(?<minutes>\\d+(?:[.,]\\d+)?)M)?(?:(?<seconds>\\d+(?:[.,]\\d+)?)S)?)?$",
        RegexOption.IGNORE_CASE,
    )

    private val HUMAN = Regex(
        "(?<value>\\d+(?:[.,]\\d+)?)\\s*(?<unit>godzin\\w*|godz\\.?|h|minut\\w*|min\\.?|sekund\\w*|sek\\.?)",
        RegexOption.IGNORE_CASE,
    )

    /** Zwraca `null`, gdy z tekstu nie da się odczytać sensownego czasu. */
    fun toMinutes(value: String?): Int? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        ISO.find(text)?.let { match ->
            val days = match.number("days") ?: 0.0
            val hours = match.number("hours") ?: 0.0
            val minutes = match.number("minutes") ?: 0.0
            val seconds = match.number("seconds") ?: 0.0
            val total = days * 24 * 60 + hours * 60 + minutes + seconds / 60.0
            return total.roundToInt().takeIf { it > 0 }
        }

        // Zapis „1 h 30 min” składa się z kilku fragmentów, więc sumujemy wszystkie.
        var total = 0.0
        var matched = false
        HUMAN.findAll(text).forEach { match ->
            val amount = match.number("value") ?: return@forEach
            val unit = match.groups["unit"]?.value?.lowercase()?.removeSuffix(".") ?: return@forEach
            total += when {
                unit.startsWith("godz") || unit == "h" -> amount * 60
                unit.startsWith("min") -> amount
                unit.startsWith("sek") -> amount / 60.0
                else -> return@forEach
            }
            matched = true
        }
        if (matched) return total.roundToInt().takeIf { it > 0 }

        // Sama liczba w polu czasu to zwyczajowo minuty.
        return text.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun MatchResult.number(group: String): Double? =
        groups[group]?.value?.replace(',', '.')?.toDoubleOrNull()
}
