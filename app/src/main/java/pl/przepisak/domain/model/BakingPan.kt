package pl.przepisak.domain.model

/** Kształt formy do pieczenia (RF-09). */
enum class PanShape {
    /** Tortownica / forma okrągła. */
    ROUND,

    /** Blacha lub forma prostokątna (kwadrat = prostokąt o równych bokach). */
    RECTANGLE,

    /** Forma z kominem (babka, wieniec) — liczymy pole pierścienia. */
    RING,
}

/**
 * Forma do pieczenia. Wymiary trzymamy w centymetrach.
 *
 * Dla [PanShape.ROUND] znaczenie ma tylko [diameterCm];
 * dla [PanShape.RECTANGLE] — [lengthCm] i [widthCm];
 * dla [PanShape.RING] — [diameterCm] (zewnętrzna) i [innerDiameterCm] (komin).
 */
data class BakingPan(
    val shape: PanShape,
    val diameterCm: Double? = null,
    val lengthCm: Double? = null,
    val widthCm: Double? = null,
    val innerDiameterCm: Double? = null,
    /** Wysokość ścianki — potrzebna tylko przy przeliczaniu objętościowym. */
    val heightCm: Double? = null,
    val label: String? = null,
) {
    companion object {
        fun round(diameterCm: Double, heightCm: Double? = null, label: String? = null) =
            BakingPan(PanShape.ROUND, diameterCm = diameterCm, heightCm = heightCm, label = label)

        fun rectangle(lengthCm: Double, widthCm: Double, heightCm: Double? = null, label: String? = null) =
            BakingPan(PanShape.RECTANGLE, lengthCm = lengthCm, widthCm = widthCm, heightCm = heightCm, label = label)

        fun square(sideCm: Double, heightCm: Double? = null, label: String? = null) =
            rectangle(sideCm, sideCm, heightCm, label)

        fun ring(outerDiameterCm: Double, innerDiameterCm: Double, heightCm: Double? = null, label: String? = null) =
            BakingPan(
                PanShape.RING,
                diameterCm = outerDiameterCm,
                innerDiameterCm = innerDiameterCm,
                heightCm = heightCm,
                label = label,
            )

        /** Popularne w Polsce rozmiary form — gotowe do wyboru jednym dotknięciem. */
        val presets: List<BakingPan> = listOf(
            round(18.0, label = "Tortownica 18 cm"),
            round(20.0, label = "Tortownica 20 cm"),
            round(22.0, label = "Tortownica 22 cm"),
            round(24.0, label = "Tortownica 24 cm"),
            round(26.0, label = "Tortownica 26 cm"),
            round(28.0, label = "Tortownica 28 cm"),
            square(20.0, label = "Forma kwadratowa 20 × 20 cm"),
            square(24.0, label = "Forma kwadratowa 24 × 24 cm"),
            rectangle(24.0, 35.0, label = "Blacha 24 × 35 cm"),
            rectangle(25.0, 38.0, label = "Blacha 25 × 38 cm"),
            rectangle(30.0, 40.0, label = "Blacha 30 × 40 cm (piekarnikowa)"),
            rectangle(20.0, 30.0, label = "Blacha 20 × 30 cm"),
            rectangle(30.0, 11.0, label = "Keksówka 30 × 11 cm"),
            rectangle(25.0, 10.0, label = "Keksówka 25 × 10 cm"),
            ring(22.0, 7.0, label = "Forma na babkę 22 cm"),
            ring(24.0, 8.0, label = "Forma na babkę 24 cm"),
        )
    }
}

/** Wynik przeliczenia proporcji między formami (RF-09). */
data class PanConversion(
    val source: BakingPan,
    val target: BakingPan,
    val sourceAreaCm2: Double,
    val targetAreaCm2: Double,
    /** Mnożnik dla składników przy zachowaniu tej samej grubości ciasta. */
    val areaFactor: Double,
    /** Mnożnik objętościowy — dostępny, gdy obie formy mają podaną wysokość. */
    val volumeFactor: Double? = null,
    val sourceVolumeCm3: Double? = null,
    val targetVolumeCm3: Double? = null,
)
