package pl.przepisak.domain.model

/** Wymiar jednostki — decyduje o tym, co da się na co przeliczyć. */
enum class UnitDimension { MASS, VOLUME, COUNT, LENGTH }

/**
 * Jednostki miary używane w polskich przepisach.
 *
 * [baseFactor] przelicza jednostkę na jednostkę bazową swojego wymiaru:
 *  - MASS   → gram
 *  - VOLUME → mililitr
 *  - COUNT  → sztuka (przeliczanie między różnymi jednostkami COUNT jest zabronione)
 *  - LENGTH → centymetr
 *
 * [aliases] zawiera formy odmienione i skróty spotykane w przepisach — używa ich
 * `QuantityParser`. Wszystkie aliasy podajemy małymi literami, bez kropki na końcu;
 * parser normalizuje wejście (usuwa diakrytyki i kropki) przed dopasowaniem.
 */
enum class MeasureUnit(
    val canonical: String,
    val dimension: UnitDimension,
    val baseFactor: Double,
    val aliases: List<String>,
    /** Jednostka umowna (garść, szczypta) — przeliczenia są tylko przybliżone. */
    val isApproximate: Boolean = false,
) {
    // --- masa ---
    MILLIGRAM("mg", UnitDimension.MASS, 0.001, listOf("mg", "miligram", "miligramy", "miligramow")),
    GRAM("g", UnitDimension.MASS, 1.0, listOf("g", "gr", "gram", "gramy", "gramow", "grama")),
    DEKAGRAM("dag", UnitDimension.MASS, 10.0, listOf("dag", "dkg", "deka", "dekagram", "dekagramy", "dekagramow")),
    KILOGRAM("kg", UnitDimension.MASS, 1000.0, listOf("kg", "kilo", "kilogram", "kilogramy", "kilogramow", "kilograma")),

    // --- objętość ---
    MILLILITER("ml", UnitDimension.VOLUME, 1.0, listOf("ml", "mililitr", "mililitry", "mililitrow")),
    CENTILITER("cl", UnitDimension.VOLUME, 10.0, listOf("cl", "centylitr", "centylitry")),
    DECILITER("dl", UnitDimension.VOLUME, 100.0, listOf("dl", "decylitr", "decylitry")),
    LITER("l", UnitDimension.VOLUME, 1000.0, listOf("l", "litr", "litry", "litrow", "litra")),
    CUP("szklanka", UnitDimension.VOLUME, 250.0, listOf("szklanka", "szklanki", "szklanek", "szkl", "szklance", "szklanke")),
    MUG("kubek", UnitDimension.VOLUME, 300.0, listOf("kubek", "kubki", "kubka", "kubkow"), isApproximate = true),
    TEACUP("filizanka", UnitDimension.VOLUME, 150.0, listOf("filizanka", "filizanki", "filizanek"), isApproximate = true),
    TABLESPOON("łyżka", UnitDimension.VOLUME, 15.0, listOf("lyzka", "lyzki", "lyzek", "lyzke", "l stolowa", "lyzka stolowa", "lyzki stolowe")),
    TEASPOON("łyżeczka", UnitDimension.VOLUME, 5.0, listOf("lyzeczka", "lyzeczki", "lyzeczek", "lyzeczke", "lyz")),
    PINCH("szczypta", UnitDimension.VOLUME, 0.5, listOf("szczypta", "szczypty", "szczypt", "szczyptę", "szczypte"), isApproximate = true),
    HANDFUL("garść", UnitDimension.VOLUME, 40.0, listOf("garsc", "garsci", "garscie", "garstka"), isApproximate = true),
    DROP("kropla", UnitDimension.VOLUME, 0.05, listOf("kropla", "krople", "kropli", "kropel"), isApproximate = true),

    // --- sztuki i opakowania ---
    PIECE("szt.", UnitDimension.COUNT, 1.0, listOf("szt", "sztuka", "sztuki", "sztuk", "sztuke")),
    CLOVE("ząbek", UnitDimension.COUNT, 1.0, listOf("zabek", "zabki", "zabkow", "zabka")),
    BUNCH("pęczek", UnitDimension.COUNT, 1.0, listOf("peczek", "peczki", "peczkow", "peczka")),
    SPRIG("gałązka", UnitDimension.COUNT, 1.0, listOf("galazka", "galazki", "galazek")),
    LEAF("listek", UnitDimension.COUNT, 1.0, listOf("listek", "listki", "listkow", "lisc", "liscie", "lisci")),
    SLICE("plaster", UnitDimension.COUNT, 1.0, listOf("plaster", "plastry", "plastrow", "plasterek", "plasterki", "kromka", "kromki", "kromek")),
    PACKAGE("opakowanie", UnitDimension.COUNT, 1.0, listOf("opakowanie", "opakowania", "opak", "paczka", "paczki", "paczek", "saszetka", "saszetki", "saszetek", "torebka", "torebki", "torebek")),
    CAN("puszka", UnitDimension.COUNT, 1.0, listOf("puszka", "puszki", "puszek")),
    JAR("słoik", UnitDimension.COUNT, 1.0, listOf("sloik", "sloiki", "sloika", "sloikow")),
    BAR("tabliczka", UnitDimension.COUNT, 1.0, listOf("tabliczka", "tabliczki", "tabliczek")),
    HALF_PIECE("połówka", UnitDimension.COUNT, 0.5, listOf("polowka", "polowki", "polowek", "pol")),

    // --- długość (formy, wałkowanie ciasta) ---
    MILLIMETER("mm", UnitDimension.LENGTH, 0.1, listOf("mm", "milimetr", "milimetry")),
    CENTIMETER("cm", UnitDimension.LENGTH, 1.0, listOf("cm", "centymetr", "centymetry", "centymetrow")),
    ;

    val isMass: Boolean get() = dimension == UnitDimension.MASS
    val isVolume: Boolean get() = dimension == UnitDimension.VOLUME

    /** Wartość w jednostce bazowej wymiaru (g / ml / szt. / cm). */
    fun toBase(amount: Double): Double = amount * baseFactor

    /** Wartość bazową (g / ml / szt. / cm) przelicza na tę jednostkę. */
    fun fromBase(baseAmount: Double): Double = baseAmount / baseFactor

    companion object {
        /** Jednostki oferowane w formularzu edycji, w wygodnej kolejności. */
        val pickerOrder: List<MeasureUnit> = listOf(
            GRAM, DEKAGRAM, KILOGRAM, MILLILITER, DECILITER, LITER,
            CUP, TABLESPOON, TEASPOON, PINCH, HANDFUL, DROP, MUG, TEACUP,
            PIECE, HALF_PIECE, CLOVE, BUNCH, SPRIG, LEAF, SLICE,
            PACKAGE, CAN, JAR, BAR, MILLIGRAM, CENTILITER, MILLIMETER, CENTIMETER,
        )

        /** Jednostki objętościowe, które sensownie przelicza się na wagę (RF-10). */
        val volumeForWeightConversion: List<MeasureUnit> = listOf(
            CUP, TABLESPOON, TEASPOON, MILLILITER, DECILITER, LITER, MUG, TEACUP, PINCH, HANDFUL,
        )

        private val byAlias: Map<String, MeasureUnit> = buildMap {
            MeasureUnit.entries.forEach { unit ->
                (unit.aliases + unit.canonical).forEach { alias ->
                    put(normalizeToken(alias), unit)
                }
            }
        }

        /** Największa liczba słów, z jakiej może się składać nazwa jednostki. */
        val maxAliasWords: Int = byAlias.keys.maxOf { it.split(' ').size }

        /**
         * Dopasowuje tekst do jednostki. Wejście może zawierać wielkie litery,
         * polskie znaki i kropki — wszystko jest normalizowane.
         */
        fun fromText(text: String): MeasureUnit? = byAlias[normalizeToken(text)]

        internal fun normalizeToken(raw: String): String =
            raw.lowercase()
                .replace('ą', 'a').replace('ć', 'c').replace('ę', 'e')
                .replace('ł', 'l').replace('ń', 'n').replace('ó', 'o')
                .replace('ś', 's').replace('ź', 'z').replace('ż', 'z')
                .replace(".", "")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}
