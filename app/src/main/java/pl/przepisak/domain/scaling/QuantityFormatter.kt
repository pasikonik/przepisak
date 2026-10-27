package pl.przepisak.domain.scaling

import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.Quantity
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Zamienia liczby na zapis, jaki widzi się w przepisach.
 *
 * Dwie rzeczy, które ten obiekt robi świadomie:
 *
 *  1. **Przyciąga ilości do ułamków kuchennych.** Po przeskalowaniu przepisu
 *     wychodzą liczby w rodzaju 0,3333 — a w kuchni odmierza się „⅓ szklanki”,
 *     nie „0,33 szklanki”.
 *  2. **Odmienia jednostki przez liczbę.** „1 szklanka”, „2 szklanki”,
 *     „5 szklanek”, „1½ szklanki” — cztery różne formy tego samego słowa.
 *     Bez tego aplikacja wygląda jak automatyczny tłumacz.
 */
object QuantityFormatter {

    /** Formy jednostki potrzebne do poprawnej polskiej odmiany przez liczebnik. */
    private data class Forms(
        /** 1 szklanka */
        val one: String,
        /** 2, 3, 4 szklanki */
        val few: String,
        /** 5 i więcej szklanek */
        val many: String,
        /** ułamek: 1½ szklanki — dopełniacz liczby pojedynczej */
        val fractional: String,
    )

    private val FORMS: Map<MeasureUnit, Forms> = mapOf(
        MeasureUnit.CUP to Forms("szklanka", "szklanki", "szklanek", "szklanki"),
        MeasureUnit.MUG to Forms("kubek", "kubki", "kubków", "kubka"),
        MeasureUnit.TEACUP to Forms("filiżanka", "filiżanki", "filiżanek", "filiżanki"),
        MeasureUnit.TABLESPOON to Forms("łyżka", "łyżki", "łyżek", "łyżki"),
        MeasureUnit.TEASPOON to Forms("łyżeczka", "łyżeczki", "łyżeczek", "łyżeczki"),
        MeasureUnit.PINCH to Forms("szczypta", "szczypty", "szczypt", "szczypty"),
        MeasureUnit.HANDFUL to Forms("garść", "garście", "garści", "garści"),
        MeasureUnit.DROP to Forms("kropla", "krople", "kropli", "kropli"),
        MeasureUnit.CLOVE to Forms("ząbek", "ząbki", "ząbków", "ząbka"),
        MeasureUnit.BUNCH to Forms("pęczek", "pęczki", "pęczków", "pęczka"),
        MeasureUnit.SPRIG to Forms("gałązka", "gałązki", "gałązek", "gałązki"),
        MeasureUnit.LEAF to Forms("listek", "listki", "listków", "listka"),
        MeasureUnit.SLICE to Forms("plaster", "plastry", "plastrów", "plastra"),
        MeasureUnit.PACKAGE to Forms("opakowanie", "opakowania", "opakowań", "opakowania"),
        MeasureUnit.CAN to Forms("puszka", "puszki", "puszek", "puszki"),
        MeasureUnit.JAR to Forms("słoik", "słoiki", "słoików", "słoika"),
        MeasureUnit.BAR to Forms("tabliczka", "tabliczki", "tabliczek", "tabliczki"),
        MeasureUnit.HALF_PIECE to Forms("połówka", "połówki", "połówek", "połówki"),
    )

    /** Ułamki, które da się zapisać jednym znakiem. */
    private val FRACTION_GLYPHS = mapOf(
        (1 to 2) to "½",
        (1 to 3) to "⅓", (2 to 3) to "⅔",
        (1 to 4) to "¼", (3 to 4) to "¾",
        (1 to 8) to "⅛", (3 to 8) to "⅜", (5 to 8) to "⅝", (7 to 8) to "⅞",
    )

    /** Mianowniki, do których wolno przyciągać. Kolejność = pierwszeństwo. */
    private val DENOMINATORS = intArrayOf(2, 4, 3, 8)

    /** Powyżej tej wartości ułamki przestają mieć sens — nikt nie odmierza 12⅜ g. */
    private const val FRACTION_LIMIT = 10.0

    /** Tolerancja przyciągania do ułamka. */
    private const val SNAP_TOLERANCE = 0.02

    // -----------------------------------------------------------------------
    // Liczby
    // -----------------------------------------------------------------------

    /** Formatuje samą liczbę: „250”, „1½”, „⅓”, „0,7”. */
    fun formatAmount(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return ""
        if (value <= 0.0) return "0"

        if (value >= FRACTION_LIMIT) {
            val rounded = value.roundToLong()
            // Powyżej 10 zaokrąglamy do całości, chyba że to zgubiłoby istotną część
            // (np. 10,5 kg → „10,5”, ale 250,04 g → „250”).
            return if (abs(value - rounded) < 0.05) rounded.toString() else decimal(value)
        }

        val whole = floor(value).toInt()
        val remainder = value - whole

        if (remainder < SNAP_TOLERANCE) return whole.toString()
        if (remainder > 1.0 - SNAP_TOLERANCE) return (whole + 1).toString()

        val snapped = snapToFraction(remainder)
        if (snapped != null) {
            val glyph = FRACTION_GLYPHS[snapped] ?: "${snapped.first}/${snapped.second}"
            return if (whole == 0) glyph else "$whole$glyph"
        }
        return decimal(value)
    }

    /**
     * Pełny zapis ilości z jednostką: „250 g”, „1½ szklanki”, „1-2 ząbki”.
     *
     * Gdy jednostka jest nieznana, używamy [unitLabel] — dosłownego napisu ze źródła.
     */
    fun format(quantity: Quantity?, unit: MeasureUnit?, unitLabel: String? = null): String {
        if (quantity == null) {
            return unit?.let { unitLabelFor(it, 1.0) } ?: unitLabel.orEmpty()
        }

        val amountText = if (quantity.isRange) {
            "${formatAmount(quantity.amount)}-${formatAmount(quantity.amountMax ?: quantity.amount)}"
        } else {
            formatAmount(quantity.amount)
        }

        // Przy zakresie odmieniamy według górnej granicy — „1-2 łyżki”, nie „1-2 łyżka”.
        val declensionBase = quantity.amountMax ?: quantity.amount
        val label = when {
            unit != null -> unitLabelFor(unit, declensionBase)
            !unitLabel.isNullOrBlank() -> unitLabel
            else -> ""
        }
        return if (label.isEmpty()) amountText else "$amountText $label"
    }

    /**
     * Nazwa jednostki odmieniona przez podaną liczbę.
     *
     * Jednostki metryczne (g, kg, ml, l, cm) są nieodmienne, więc zwracamy je
     * bez zmian. Dla pozostałych stosujemy polskie reguły liczebnika.
     */
    fun unitLabelFor(unit: MeasureUnit, amount: Double): String {
        val forms = FORMS[unit] ?: return unit.canonical
        val rounded = amount.roundToInt()
        val isWhole = abs(amount - rounded) < SNAP_TOLERANCE

        if (!isWhole) return forms.fractional

        return when {
            rounded == 1 -> forms.one
            // 12, 13, 14 (oraz 112, 213…) idą z formą „wielu”, mimo końcówki 2-4.
            rounded % 100 in 12..14 -> forms.many
            rounded % 10 in 2..4 -> forms.few
            else -> forms.many
        }
    }

    // -----------------------------------------------------------------------
    // Czas
    // -----------------------------------------------------------------------

    /** Czas trwania w minutach: „45 min”, „1 h 30 min”, „2 h”. */
    fun formatMinutes(minutes: Int?): String {
        if (minutes == null || minutes <= 0) return ""
        if (minutes < 60) return "$minutes min"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "$hours h" else "$hours h $rest min"
    }

    /** Odliczanie minutnika: „05:30”, „1:05:30”. */
    fun formatSeconds(seconds: Int): String {
        val total = seconds.coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            "$hours:${minutes.pad()}:${secs.pad()}"
        } else {
            "${minutes.pad()}:${secs.pad()}"
        }
    }

    // -----------------------------------------------------------------------
    // Pomocnicze
    // -----------------------------------------------------------------------

    /** Liczba z przecinkiem jako separatorem i bez zbędnych zer na końcu. */
    fun decimal(value: Double, maxDecimals: Int = 2): String {
        var text = String.format(java.util.Locale.US, "%.${maxDecimals}f", value)
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        return text.replace('.', ',')
    }

    /**
     * Znajduje ułamek najbliższy podanej części dziesiętnej.
     * Zwraca parę (licznik, mianownik) po skróceniu albo `null`, gdy żaden
     * z dopuszczalnych mianowników nie mieści się w tolerancji.
     */
    private fun snapToFraction(remainder: Double): Pair<Int, Int>? {
        for (denominator in DENOMINATORS) {
            val numerator = (remainder * denominator).roundToInt()
            if (numerator <= 0 || numerator >= denominator) continue
            if (abs(remainder - numerator.toDouble() / denominator) <= SNAP_TOLERANCE) {
                val divisor = gcd(numerator, denominator)
                return (numerator / divisor) to (denominator / divisor)
            }
        }
        return null
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun Int.pad(): String = if (this < 10) "0$this" else toString()
}
