package pl.przepisak.domain.scaling

import pl.przepisak.domain.model.IngredientDensity
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.WeightConversion
import javax.inject.Inject

/**
 * Przelicza objętość na wagę i odwrotnie, korzystając z wbudowanej bazy wagowej (RF-10).
 *
 * Rozróżniamy wartości **zmierzone** i **wyliczone z gęstości**. Szklanka mąki
 * nasypanej waży inaczej niż wynikałoby z gęstości i objętości naczynia, bo produkt
 * się zbija. Tam, gdzie baza ma zmierzoną masę naczynia, ma ona pierwszeństwo, a
 * wynik nie jest oznaczany jako przybliżony.
 */
class VolumeToWeightConverter @Inject constructor() {

    /** `null`, gdy dla danej jednostki nie potrafimy podać masy (np. „plaster mąki”). */
    fun toGrams(amount: Double, unit: MeasureUnit, density: IngredientDensity): WeightConversion? {
        if (amount <= 0.0) return null
        val gramsPerUnit = density.gramsFor(unit) ?: return null

        return WeightConversion(
            density = density,
            amount = amount,
            unit = unit,
            grams = gramsPerUnit * amount,
            isEstimated = measuredValueFor(unit, density) == null || unit.isApproximate,
        )
    }

    /** Ile naczyń danego typu daje wskazaną masę. `null`, gdy nie da się przeliczyć. */
    fun toVolume(grams: Double, unit: MeasureUnit, density: IngredientDensity): Double? {
        if (grams <= 0.0) return null
        val gramsPerUnit = density.gramsFor(unit)?.takeIf { it > 0.0 } ?: return null
        return grams / gramsPerUnit
    }

    /**
     * Masa zmierzona dla konkretnego naczynia — `null` oznacza, że wynik trzeba
     * będzie policzyć z gęstości i oznaczyć jako przybliżony.
     */
    private fun measuredValueFor(unit: MeasureUnit, density: IngredientDensity): Double? = when (unit) {
        MeasureUnit.CUP -> density.gramsPerCup
        MeasureUnit.TABLESPOON -> density.gramsPerTablespoon
        MeasureUnit.TEASPOON -> density.gramsPerTeaspoon
        MeasureUnit.PIECE -> density.gramsPerPiece
        else -> null
    }
}
