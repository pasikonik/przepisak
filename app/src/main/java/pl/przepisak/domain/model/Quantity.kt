package pl.przepisak.domain.model

/**
 * Ilość składnika. Obsługuje zakresy ("1-2 łyżki") przez [amountMax].
 *
 * Wartości trzymamy jako [Double], a nie jako ułamki — formatowanie do postaci
 * "½", "1¼" itd. realizuje `QuantityFormatter`, który przyciąga liczbę do
 * najbliższego sensownego ułamka kuchennego.
 */
data class Quantity(
    val amount: Double,
    val amountMax: Double? = null,
) {
    val isRange: Boolean get() = amountMax != null && amountMax > amount

    /** Wartość reprezentatywna — środek zakresu albo sama wartość. */
    val midpoint: Double get() = amountMax?.let { (amount + it) / 2.0 } ?: amount

    fun scaled(factor: Double): Quantity =
        Quantity(amount * factor, amountMax?.times(factor))

    companion object {
        fun of(value: Double): Quantity = Quantity(value)
        fun range(from: Double, to: Double): Quantity =
            if (to > from) Quantity(from, to) else Quantity(from)
    }
}
