package pl.przepisak.domain.scaling

import pl.przepisak.domain.model.BakingPan
import pl.przepisak.domain.model.PanConversion
import pl.przepisak.domain.model.PanShape
import kotlin.math.PI

/**
 * Przelicza proporcje ciasta między formami o różnych kształtach (RF-09).
 *
 * Podstawą jest **pole** formy, nie objętość. Przy tej samej grubości ciasta —
 * a tego zwykle chcemy, bo grubość decyduje o czasie pieczenia — ilość składników
 * skaluje się wraz z polem. Współczynnik objętościowy podajemy dodatkowo, gdy obie
 * formy mają podaną wysokość, ale to inne pytanie: „ile ciasta zmieści się do formy”.
 */
object PanCalculator {

    /** Pole dna formy w cm². `null`, gdy brakuje wymiarów potrzebnych dla kształtu. */
    fun areaCm2(pan: BakingPan): Double? = when (pan.shape) {
        PanShape.ROUND -> pan.diameterCm
            ?.takeIf { it > 0 }
            ?.let { PI * (it / 2.0) * (it / 2.0) }

        PanShape.RECTANGLE -> {
            val length = pan.lengthCm
            val width = pan.widthCm
            if (length != null && width != null && length > 0 && width > 0) length * width else null
        }

        PanShape.RING -> {
            val outer = pan.diameterCm
            val inner = pan.innerDiameterCm ?: 0.0
            if (outer != null && outer > 0 && inner >= 0 && inner < outer) {
                PI * ((outer / 2.0) * (outer / 2.0) - (inner / 2.0) * (inner / 2.0))
            } else {
                null
            }
        }
    }

    /** Objętość formy w cm³ — tylko gdy znamy wysokość ścianki. */
    fun volumeCm3(pan: BakingPan): Double? {
        val height = pan.heightCm?.takeIf { it > 0 } ?: return null
        return areaCm2(pan)?.times(height)
    }

    /** `null`, gdy którejkolwiek z form brakuje wymiarów. */
    fun convert(source: BakingPan, target: BakingPan): PanConversion? {
        val sourceArea = areaCm2(source) ?: return null
        val targetArea = areaCm2(target) ?: return null
        if (sourceArea <= 0.0) return null

        val sourceVolume = volumeCm3(source)
        val targetVolume = volumeCm3(target)

        return PanConversion(
            source = source,
            target = target,
            sourceAreaCm2 = sourceArea,
            targetAreaCm2 = targetArea,
            areaFactor = targetArea / sourceArea,
            volumeFactor = if (sourceVolume != null && targetVolume != null && sourceVolume > 0.0) {
                targetVolume / sourceVolume
            } else {
                null
            },
            sourceVolumeCm3 = sourceVolume,
            targetVolumeCm3 = targetVolume,
        )
    }

    /** Czytelny opis formy — używany w podsumowaniach i na etykietach. */
    fun describe(pan: BakingPan): String {
        pan.label?.takeIf { it.isNotBlank() }?.let { return it }
        return when (pan.shape) {
            PanShape.ROUND -> pan.diameterCm
                ?.let { "tortownica ⌀ ${QuantityFormatter.decimal(it)} cm" }
                ?: "forma okrągła"

            PanShape.RECTANGLE -> {
                val length = pan.lengthCm
                val width = pan.widthCm
                if (length != null && width != null) {
                    "blacha ${QuantityFormatter.decimal(length)} × ${QuantityFormatter.decimal(width)} cm"
                } else {
                    "forma prostokątna"
                }
            }

            PanShape.RING -> pan.diameterCm
                ?.let { "forma z kominem ⌀ ${QuantityFormatter.decimal(it)} cm" }
                ?: "forma z kominem"
        }
    }
}
