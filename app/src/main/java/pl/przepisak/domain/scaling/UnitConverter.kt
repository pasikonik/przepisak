package pl.przepisak.domain.scaling

import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.Quantity
import pl.przepisak.domain.model.UnitDimension
import javax.inject.Inject

/**
 * Przelicza jednostki w obrębie jednego wymiaru i dobiera zapis czytelny dla człowieka.
 *
 * Zamiany masy na objętość tu nie ma i nie będzie — do tego potrzebna jest gęstość
 * konkretnego produktu, czym zajmuje się [VolumeToWeightConverter] razem z bazą wagową.
 */
class UnitConverter @Inject constructor() {

    /**
     * Zwraca `null`, gdy przeliczenie nie ma sensu: różne wymiary (gramy na mililitry)
     * albo dwie różne jednostki liczone na sztuki (ząbek czosnku to nie plaster).
     */
    fun convert(amount: Double, from: MeasureUnit, to: MeasureUnit): Double? {
        if (from == to) return amount
        if (from.dimension != to.dimension) return null
        if (from.dimension == UnitDimension.COUNT && !bothCountable(from, to)) return null
        return from.toBase(amount) / to.baseFactor
    }

    /**
     * Dobiera wygodniejszą jednostkę po przeskalowaniu: 1500 g → 1,5 kg,
     * 0,25 l → 250 ml, 3 łyżeczki → 1 łyżka.
     *
     * Zostajemy w tej samej „rodzinie” jednostek, w jakiej przepis został zapisany.
     * Kto podał 250 ml, chce mililitrów, a nie szklanki — i odwrotnie.
     * Jednostek umownych (szczypta, garść) nie ruszamy, bo ich przeliczanie
     * udawałoby precyzję, której nie mają.
     */
    fun prettify(quantity: Quantity, unit: MeasureUnit?): Pair<Quantity, MeasureUnit?> {
        if (unit == null || unit.isApproximate) return quantity to unit
        if (unit.dimension == UnitDimension.COUNT || unit.dimension == UnitDimension.LENGTH) {
            return quantity to unit
        }

        val family = familyOf(unit) ?: return quantity to unit
        val base = unit.toBase(quantity.amount)

        // Rodziny są uporządkowane od największej jednostki; bierzemy pierwszą,
        // w której wartość nie schodzi poniżej 1 — a gdy żadna nie spełnia
        // warunku (bardzo mała ilość), najmniejszą dostępną.
        val target = family.firstOrNull { base / it.baseFactor >= 1.0 } ?: family.last()
        if (target == unit) return quantity to unit

        val converted = Quantity(
            amount = base / target.baseFactor,
            amountMax = quantity.amountMax?.let { unit.toBase(it) / target.baseFactor },
        )
        return converted to target
    }

    /**
     * Jednostki, na które warto przeliczać w obrębie danej rodziny.
     * Dekagramów i decylitrów świadomie nie proponujemy — są poprawne, ale
     * w przepisach pojawiają się rzadko i mylą.
     */
    private fun familyOf(unit: MeasureUnit): List<MeasureUnit>? = when {
        unit.dimension == UnitDimension.MASS -> MASS_FAMILY
        unit in KITCHEN_VOLUME -> KITCHEN_VOLUME
        unit.dimension == UnitDimension.VOLUME -> METRIC_VOLUME
        else -> null
    }

    private fun bothCountable(from: MeasureUnit, to: MeasureUnit): Boolean {
        val countable = setOf(MeasureUnit.PIECE, MeasureUnit.HALF_PIECE)
        return from in countable && to in countable
    }

    private companion object {
        val MASS_FAMILY = listOf(MeasureUnit.KILOGRAM, MeasureUnit.GRAM)
        val METRIC_VOLUME = listOf(MeasureUnit.LITER, MeasureUnit.MILLILITER)
        val KITCHEN_VOLUME = listOf(MeasureUnit.CUP, MeasureUnit.TABLESPOON, MeasureUnit.TEASPOON)
    }
}
