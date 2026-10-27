package pl.przepisak.domain.model

/**
 * Wpis wbudowanej bazy wagowej (RF-10): ile waży dana objętość produktu.
 *
 * Trzymamy zarówno gęstość [gramsPerMl], jak i zmierzone wartości dla typowych
 * naczyń. Powód: mąka nasypana do szklanki nie waży 250 ml × gęstość — liczy się
 * ubicie produktu. Jeśli wartość dla naczynia jest podana, ma pierwszeństwo.
 */
data class IngredientDensity(
    val id: Long = 0,
    /** Nazwa wyświetlana, np. „mąka pszenna”. */
    val name: String,
    /** Nazwa bez polskich znaków i małymi literami — do wyszukiwania. */
    val normalizedName: String,
    /** Dodatkowe nazwy, po których użytkownik może szukać. */
    val aliases: List<String> = emptyList(),
    val category: String,
    /** Gęstość nasypowa w g/ml. */
    val gramsPerMl: Double,
    /** Masa 1 szklanki (250 ml), jeśli zmierzona. */
    val gramsPerCup: Double? = null,
    /** Masa 1 łyżki (15 ml), jeśli zmierzona. */
    val gramsPerTablespoon: Double? = null,
    /** Masa 1 łyżeczki (5 ml), jeśli zmierzona. */
    val gramsPerTeaspoon: Double? = null,
    /** Masa 1 sztuki (jajko, ząbek czosnku), jeśli ma to sens. */
    val gramsPerPiece: Double? = null,
    /** `false` dla wpisów dodanych przez użytkownika. */
    val isBuiltIn: Boolean = true,
) {
    /** Masa dla podanej jednostki, z preferencją wartości zmierzonych. */
    fun gramsFor(unit: MeasureUnit): Double? = when (unit) {
        MeasureUnit.CUP -> gramsPerCup ?: (gramsPerMl * MeasureUnit.CUP.baseFactor)
        MeasureUnit.TABLESPOON -> gramsPerTablespoon ?: (gramsPerMl * MeasureUnit.TABLESPOON.baseFactor)
        MeasureUnit.TEASPOON -> gramsPerTeaspoon ?: (gramsPerMl * MeasureUnit.TEASPOON.baseFactor)
        MeasureUnit.PIECE -> gramsPerPiece
        else -> if (unit.isVolume) gramsPerMl * unit.baseFactor else null
    }
}

/** Wynik przeliczenia objętości na wagę (RF-10). */
data class WeightConversion(
    val density: IngredientDensity,
    val amount: Double,
    val unit: MeasureUnit,
    val grams: Double,
    /** `true`, gdy oparliśmy się na gęstości, a nie na zmierzonej masie naczynia. */
    val isEstimated: Boolean,
)
