package pl.przepisak.data.local.seed

import pl.przepisak.core.util.TextNormalizer
import pl.przepisak.data.local.entity.IngredientDensityEntity

/**
 * Wbudowana baza wagowa produktów (RF-10) — dane startowe wstawiane przez
 * `PrzepisakDatabase.seedCallback` przy pierwszym uruchomieniu.
 *
 * Wartości to gęstość nasypowa (`gramsPerMl`, g/ml) oraz — tam gdzie ma to sens —
 * zmierzone masy dla szklanki (250 ml), łyżki (15 ml), łyżeczki (5 ml) i sztuki.
 * Masa szklanki nie zawsze równa się `gramsPerMl * 250`: produkty sypkie, które się
 * ubijają (np. cukier brązowy) albo napowietrzają przy nasypywaniu (np. mąka,
 * kakao, cukier puder), mają wartość zmierzoną — ona ma pierwszeństwo w
 * [pl.przepisak.domain.model.IngredientDensity.gramsFor].
 */
object DensitySeed {

    fun entries(): List<IngredientDensityEntity> = listOf(
        // --- Mąki i skrobie ---
        entry("Mąka pszenna uniwersalna (typ 500)", "Mąki i skrobie", 0.64, cup = 160.0, tbsp = 10.0, tsp = 3.0, aliases = "maka pszenna|maka pszenna typ 500|maka zwykla"),
        entry("Mąka pszenna pełnoziarnista (graham)", "Mąki i skrobie", 0.48, cup = 120.0, tbsp = 7.0, tsp = 2.0, aliases = "maka graham|maka pelnoziarnista"),
        entry("Mąka żytnia typ 720", "Mąki i skrobie", 0.56, cup = 140.0, tbsp = 8.0, tsp = 3.0, aliases = "maka zytnia"),
        entry("Mąka orkiszowa", "Mąki i skrobie", 0.52, cup = 130.0, tbsp = 8.0, tsp = 3.0, aliases = "maka ze spelty"),
        entry("Mąka ziemniaczana (skrobia ziemniaczana)", "Mąki i skrobie", 0.7, cup = 175.0, tbsp = 10.0, tsp = 3.0, aliases = "skrobia ziemniaczana|maka kartoflana"),
        entry("Mąka kukurydziana (grys kukurydziany)", "Mąki i skrobie", 0.6, cup = 150.0, tbsp = 9.0, tsp = 3.0, aliases = "grys kukurydziany|kasza kukurydziana mielona"),
        entry("Skrobia kukurydziana (maizena)", "Mąki i skrobie", 0.52, cup = 130.0, tbsp = 8.0, tsp = 3.0, aliases = "maizena|skrobia kukurydziana"),
        entry("Mąka owsiana", "Mąki i skrobie", 0.36, cup = 90.0, tbsp = 5.0, tsp = 2.0),
        entry("Mąka gryczana", "Mąki i skrobie", 0.48, cup = 120.0, tbsp = 7.0, tsp = 2.0),
        entry("Mąka jaglana", "Mąki i skrobie", 0.56, cup = 140.0, tbsp = 8.0, tsp = 3.0),
        entry("Mąka kokosowa", "Mąki i skrobie", 0.448, cup = 112.0, tbsp = 7.0, tsp = 2.0),
        entry("Mąka migdałowa", "Mąki i skrobie", 0.384, cup = 96.0, tbsp = 6.0, tsp = 2.0, aliases = "migdaly mielone"),
        entry("Mąka ryżowa", "Mąki i skrobie", 0.6, cup = 150.0, tbsp = 9.0, tsp = 3.0),
        entry("Mąka tortowa (typ 400)", "Mąki i skrobie", 0.52, cup = 130.0, tbsp = 8.0, tsp = 3.0, aliases = "maka typ 400"),
        entry("Otręby pszenne", "Mąki i skrobie", 0.24, cup = 60.0, tbsp = 4.0, tsp = 1.0),

        // --- Cukry i słodziki ---
        entry("Cukier biały (kryształ)", "Cukry i słodziki", 0.8, cup = 200.0, tbsp = 12.0, tsp = 4.0, aliases = "cukier krysztal|cukier bialy|cukier"),
        entry("Cukier puder", "Cukry i słodziki", 0.48, cup = 120.0, tbsp = 8.0, tsp = 2.0, aliases = "cukier pudrowany"),
        entry("Cukier trzcinowy", "Cukry i słodziki", 0.76, cup = 190.0, tbsp = 11.0, tsp = 4.0, aliases = "cukier trzcinowy jasny"),
        entry("Cukier brązowy", "Cukry i słodziki", 0.88, cup = 220.0, tbsp = 13.0, tsp = 4.0, aliases = "cukier brazowy ciemny|muscovado"),
        entry("Cukier waniliowy", "Cukry i słodziki", 0.8, cup = 200.0, tbsp = 12.0, tsp = 4.0, piece = 16.0, aliases = "cukier wanilinowy"),
        entry("Ksylitol", "Cukry i słodziki", 0.76, cup = 190.0, tbsp = 11.0, tsp = 4.0, aliases = "cukier brzozowy"),
        entry("Erytrytol", "Cukry i słodziki", 0.6, cup = 150.0, tbsp = 9.0, tsp = 3.0),
        entry("Stewia (zamiennik cukru 1:1)", "Cukry i słodziki", 0.8, cup = 200.0, tbsp = 12.0, tsp = 4.0, aliases = "slodzik stewia"),
        entry("Fruktoza", "Cukry i słodziki", 0.76, cup = 190.0, tbsp = 11.0, tsp = 4.0, aliases = "cukier owocowy"),
        entry("Glukoza w proszku (dekstroza)", "Cukry i słodziki", 0.64, cup = 160.0, tbsp = 10.0, tsp = 3.0, aliases = "dekstroza"),
        entry("Miód", "Cukry i słodziki", 1.42, cup = 355.0, tbsp = 21.0, tsp = 7.0, aliases = "miod pszczeli|miod naturalny"),
        entry("Syrop klonowy", "Cukry i słodziki", 1.32, cup = 330.0, tbsp = 20.0, tsp = 7.0),
        entry("Syrop glukozowo-fruktozowy", "Cukry i słodziki", 1.38, cup = 345.0, tbsp = 21.0, tsp = 7.0, aliases = "syrop kukurydziany"),
        entry("Syrop z agawy", "Cukry i słodziki", 1.36, cup = 340.0, tbsp = 20.0, tsp = 7.0),
        entry("Melasa", "Cukry i słodziki", 1.4, cup = 350.0, tbsp = 21.0, tsp = 7.0),

        // --- Produkty mleczne (w tym jaja — w polskich sklepach z nabiałem) ---
        entry("Mleko 3,2%", "Produkty mleczne", 1.03, cup = 258.0, tbsp = 15.0, tsp = 5.0, aliases = "mleko pelnotluste|mleko 3 2 procent"),
        entry("Mleko 2%", "Produkty mleczne", 1.03, cup = 258.0, tbsp = 15.0, tsp = 5.0, aliases = "mleko poltluste"),
        entry("Mleko odtłuszczone (0,5%)", "Produkty mleczne", 1.035, cup = 259.0, tbsp = 16.0, tsp = 5.0, aliases = "mleko chude"),
        entry("Śmietana 18%", "Produkty mleczne", 0.99, cup = 248.0, tbsp = 15.0, tsp = 5.0, aliases = "smietana kremowka lekka"),
        entry("Śmietana 30%", "Produkty mleczne", 0.98, cup = 245.0, tbsp = 15.0, tsp = 5.0, aliases = "smietana kremowka"),
        entry("Śmietana 36%", "Produkty mleczne", 0.96, cup = 240.0, tbsp = 14.0, tsp = 5.0, aliases = "smietana kremowka do ubijania"),
        entry("Śmietana kwaśna 12%", "Produkty mleczne", 1.02, cup = 255.0, tbsp = 15.0, tsp = 5.0, aliases = "smietana gesta 12"),
        entry("Jogurt naturalny", "Produkty mleczne", 1.03, cup = 258.0, tbsp = 16.0, tsp = 5.0, aliases = "jogurt grecki"),
        entry("Kefir", "Produkty mleczne", 1.03, cup = 258.0, tbsp = 15.0, tsp = 5.0),
        entry("Maślanka", "Produkty mleczne", 1.03, cup = 258.0, tbsp = 15.0, tsp = 5.0),
        entry("Mleko skondensowane słodzone", "Produkty mleczne", 1.3, cup = 325.0, tbsp = 20.0, tsp = 6.0, aliases = "mleko skondensowane|slodzone mleko zageszczone"),
        entry("Mleko skondensowane niesłodzone", "Produkty mleczne", 1.06, cup = 265.0, tbsp = 16.0, tsp = 5.0, aliases = "mleko ewaporowane"),
        entry("Twaróg mielony (półtłusty)", "Produkty mleczne", 1.02, cup = 255.0, tbsp = 15.0, tsp = 5.0, aliases = "twarog mielony|ser twarogowy mielony"),
        entry("Mascarpone", "Produkty mleczne", 1.0, cup = 250.0, tbsp = 15.0, tsp = 5.0, aliases = "serek mascarpone"),
        entry("Serek homogenizowany", "Produkty mleczne", 1.05, cup = 262.0, tbsp = 16.0, tsp = 5.0),
        entry("Mleko w proszku", "Produkty mleczne", 0.45, cup = 113.0, tbsp = 7.0, tsp = 2.0, aliases = "mleko sypkie"),
        entry("Ser żółty (starty)", "Produkty mleczne", 0.4, cup = 100.0, tbsp = 6.0, tsp = 2.0, aliases = "ser gouda starty|ser cheddar starty"),
        entry("Jajko kurze (rozmiar M)", "Produkty mleczne", 1.03, cup = 258.0, tbsp = 15.0, tsp = 5.0, piece = 55.0, aliases = "jajo m|jajko srednie"),
        entry("Jajko kurze (rozmiar L)", "Produkty mleczne", 1.03, cup = 258.0, tbsp = 15.0, tsp = 5.0, piece = 63.0, aliases = "jajo l|jajko duze"),
        entry("Żółtko jaja", "Produkty mleczne", 1.03, cup = 258.0, tbsp = 15.0, tsp = 5.0, piece = 18.0),
        entry("Białko jaja", "Produkty mleczne", 1.03, cup = 258.0, tbsp = 15.0, tsp = 5.0, piece = 33.0),

        // --- Tłuszcze ---
        entry("Masło", "Tłuszcze", 0.908, cup = 227.0, tbsp = 14.0, tsp = 5.0, aliases = "maslo slodkie|maslo extra"),
        entry("Margaryna", "Tłuszcze", 0.94, cup = 235.0, tbsp = 15.0, tsp = 5.0, aliases = "margaryna do pieczenia"),
        entry("Olej rzepakowy", "Tłuszcze", 0.92, cup = 230.0, tbsp = 14.0, tsp = 5.0),
        entry("Oliwa z oliwek", "Tłuszcze", 0.91, cup = 228.0, tbsp = 14.0, tsp = 5.0),
        entry("Olej słonecznikowy", "Tłuszcze", 0.92, cup = 230.0, tbsp = 14.0, tsp = 5.0),
        entry("Olej kokosowy", "Tłuszcze", 0.92, cup = 230.0, tbsp = 14.0, tsp = 5.0, aliases = "tluszcz kokosowy"),
        entry("Smalec", "Tłuszcze", 0.92, cup = 230.0, tbsp = 14.0, tsp = 5.0),
        entry("Masło klarowane (ghee)", "Tłuszcze", 0.908, cup = 227.0, tbsp = 14.0, tsp = 5.0, aliases = "ghee"),

        // --- Kasze, ryże i makarony ---
        entry("Ryż biały długoziarnisty (surowy)", "Kasze, ryże i makarony", 0.8, cup = 200.0, tbsp = 12.0, tsp = 4.0, aliases = "ryz bialy|ryz dlugoziarnisty"),
        entry("Ryż basmati (surowy)", "Kasze, ryże i makarony", 0.78, cup = 195.0, tbsp = 12.0, tsp = 4.0),
        entry("Ryż jaśminowy (surowy)", "Kasze, ryże i makarony", 0.8, cup = 200.0, tbsp = 12.0, tsp = 4.0),
        entry("Ryż brązowy pełnoziarnisty (surowy)", "Kasze, ryże i makarony", 0.8, cup = 200.0, tbsp = 12.0, tsp = 4.0, aliases = "ryz brazowy"),
        entry("Kasza gryczana (surowa)", "Kasze, ryże i makarony", 0.76, cup = 190.0, tbsp = 11.0, tsp = 4.0, aliases = "kasza gryczana palona"),
        entry("Kasza jęczmienna pęczak (surowa)", "Kasze, ryże i makarony", 0.8, cup = 200.0, tbsp = 12.0, tsp = 4.0, aliases = "peczak"),
        entry("Kasza jęczmienna perłowa (surowa)", "Kasze, ryże i makarony", 0.8, cup = 200.0, tbsp = 12.0, tsp = 4.0, aliases = "kasza perlowa"),
        entry("Kasza jaglana (surowa)", "Kasze, ryże i makarony", 0.8, cup = 200.0, tbsp = 12.0, tsp = 4.0),
        entry("Kasza manna", "Kasze, ryże i makarony", 0.65, cup = 163.0, tbsp = 10.0, tsp = 3.0, aliases = "grysik"),
        entry("Kasza kuskus (surowa)", "Kasze, ryże i makarony", 0.72, cup = 180.0, tbsp = 11.0, tsp = 4.0, aliases = "kuskus"),
        entry("Kasza bulgur (surowa)", "Kasze, ryże i makarony", 0.75, cup = 188.0, tbsp = 11.0, tsp = 4.0, aliases = "bulgur"),
        entry("Płatki owsiane", "Kasze, ryże i makarony", 0.34, cup = 85.0, tbsp = 5.0, tsp = 2.0, aliases = "platki owsiane gorskie"),
        entry("Makaron świderki (surowy)", "Kasze, ryże i makarony", 0.45, cup = 113.0, tbsp = 7.0, tsp = 2.0, aliases = "makaron fusilli"),
        entry("Makaron nitki (surowy)", "Kasze, ryże i makarony", 0.4, cup = 100.0, tbsp = 6.0, tsp = 2.0, aliases = "makaron vermicelli"),
        entry("Komosa ryżowa (quinoa, surowa)", "Kasze, ryże i makarony", 0.7, cup = 175.0, tbsp = 11.0, tsp = 4.0, aliases = "quinoa"),

        // --- Orzechy i nasiona ---
        entry("Orzechy włoskie (połówki)", "Orzechy i nasiona", 0.42, cup = 105.0, tbsp = 6.0, tsp = 2.0, aliases = "orzech wloski"),
        entry("Orzechy włoskie (posiekane)", "Orzechy i nasiona", 0.5, cup = 125.0, tbsp = 8.0, tsp = 3.0),
        entry("Orzechy laskowe (całe, obrane)", "Orzechy i nasiona", 0.52, cup = 130.0, tbsp = 8.0, tsp = 3.0, aliases = "orzech laskowy"),
        entry("Migdały (całe)", "Orzechy i nasiona", 0.55, cup = 137.0, tbsp = 8.0, tsp = 3.0, aliases = "migdal"),
        entry("Migdały (płatki)", "Orzechy i nasiona", 0.3, cup = 75.0, tbsp = 5.0, tsp = 2.0, aliases = "platki migdalowe"),
        entry("Orzechy nerkowca (całe)", "Orzechy i nasiona", 0.58, cup = 145.0, tbsp = 9.0, tsp = 3.0, aliases = "orzechy nerkowca|cashew"),
        entry("Orzechy pistacjowe (łuskane)", "Orzechy i nasiona", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, aliases = "pistacje"),
        entry("Orzeszki ziemne łuskane", "Orzechy i nasiona", 0.6, cup = 150.0, tbsp = 9.0, tsp = 3.0, aliases = "orzeszki arachidowe"),
        entry("Masło orzechowe", "Orzechy i nasiona", 1.0, cup = 250.0, tbsp = 15.0, tsp = 5.0, aliases = "peanut butter"),
        entry("Nasiona słonecznika łuskane", "Orzechy i nasiona", 0.6, cup = 150.0, tbsp = 9.0, tsp = 3.0, aliases = "pestki slonecznika"),
        entry("Nasiona dyni łuskane", "Orzechy i nasiona", 0.58, cup = 145.0, tbsp = 9.0, tsp = 3.0, aliases = "pestki dyni"),
        entry("Siemię lniane", "Orzechy i nasiona", 0.65, cup = 163.0, tbsp = 10.0, tsp = 3.0, aliases = "len siemie"),
        entry("Nasiona chia", "Orzechy i nasiona", 0.68, cup = 170.0, tbsp = 10.0, tsp = 3.0, aliases = "chia"),
        entry("Sezam (nasiona)", "Orzechy i nasiona", 0.58, cup = 145.0, tbsp = 9.0, tsp = 3.0, aliases = "ziarna sezamu"),
        entry("Orzechy makadamia", "Orzechy i nasiona", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, aliases = "makadamia"),
        entry("Orzechy pekan (połówki)", "Orzechy i nasiona", 0.4, cup = 100.0, tbsp = 6.0, tsp = 2.0, aliases = "pekan"),

        // --- Bakalie i owoce suszone ---
        entry("Rodzynki", "Bakalie i owoce suszone", 0.6, cup = 150.0, tbsp = 9.0, tsp = 3.0, aliases = "rodzynki sultanki"),
        entry("Żurawina suszona", "Bakalie i owoce suszone", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, aliases = "suszona zurawina"),
        entry("Morele suszone", "Bakalie i owoce suszone", 0.5, cup = 125.0, tbsp = 8.0, tsp = 3.0, piece = 7.0, aliases = "suszone morele"),
        entry("Śliwki suszone (bez pestki)", "Bakalie i owoce suszone", 0.62, cup = 155.0, tbsp = 9.0, tsp = 3.0, piece = 8.0, aliases = "suszone sliwki|suszone sliwki wegierki"),
        entry("Daktyle suszone (bez pestki)", "Bakalie i owoce suszone", 0.7, cup = 175.0, tbsp = 11.0, tsp = 4.0, piece = 8.0, aliases = "daktyle"),
        entry("Figi suszone", "Bakalie i owoce suszone", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, piece = 10.0, aliases = "suszone figi"),
        entry("Wiśnie suszone", "Bakalie i owoce suszone", 0.58, cup = 145.0, tbsp = 9.0, tsp = 3.0, aliases = "suszone wisnie"),
        entry("Skórka pomarańczowa kandyzowana", "Bakalie i owoce suszone", 0.65, cup = 163.0, tbsp = 10.0, tsp = 3.0, aliases = "skorka pomaranczowa"),
        entry("Skórka cytrynowa kandyzowana", "Bakalie i owoce suszone", 0.65, cup = 163.0, tbsp = 10.0, tsp = 3.0, aliases = "skorka cytrynowa"),
        entry("Ananas suszony kandyzowany", "Bakalie i owoce suszone", 0.5, cup = 125.0, tbsp = 8.0, tsp = 3.0, aliases = "suszony ananas"),
        entry("Wiórki kokosowe", "Bakalie i owoce suszone", 0.35, cup = 88.0, tbsp = 5.0, tsp = 2.0, aliases = "wiorki kokosowe suszone|kokos wiorki"),
        entry("Mieszanka bakaliowa", "Bakalie i owoce suszone", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, aliases = "bakalie mix"),

        // --- Kakao i czekolada ---
        entry("Kakao (proszek naturalny, niesłodzone)", "Kakao i czekolada", 0.4, cup = 100.0, tbsp = 6.0, tsp = 2.0, aliases = "kakao naturalne|kakao proszek"),
        entry("Kakao (proszek alkalizowany, holenderski)", "Kakao i czekolada", 0.42, cup = 105.0, tbsp = 6.0, tsp = 2.0, aliases = "kakao holenderskie|kakao alkalizowane"),
        entry("Czekolada gorzka (posiekana)", "Kakao i czekolada", 0.65, cup = 163.0, tbsp = 10.0, tsp = 3.0, piece = 10.0, aliases = "czekolada gorzka tarta"),
        entry("Czekolada mleczna (posiekana)", "Kakao i czekolada", 0.62, cup = 155.0, tbsp = 9.0, tsp = 3.0, piece = 10.0, aliases = "czekolada mleczna tarta"),
        entry("Czekolada biała (posiekana)", "Kakao i czekolada", 0.6, cup = 150.0, tbsp = 9.0, tsp = 3.0, piece = 10.0, aliases = "czekolada biala tarta"),
        entry("Czekolada w kropkach (chips)", "Kakao i czekolada", 0.68, cup = 170.0, tbsp = 10.0, tsp = 3.0, aliases = "czekoladowe kropki|chocolate chips"),
        entry("Krem czekoladowo-orzechowy", "Kakao i czekolada", 1.15, cup = 288.0, tbsp = 17.0, tsp = 6.0, aliases = "nutella"),
        entry("Polewa czekoladowa (roztopiona)", "Kakao i czekolada", 1.0, cup = 250.0, tbsp = 15.0, tsp = 5.0, aliases = "czekolada roztopiona"),

        // --- Płyny ---
        entry("Woda", "Płyny", 1.0, cup = 250.0, tbsp = 15.0, tsp = 5.0),
        entry("Ocet spirytusowy", "Płyny", 1.01, cup = 253.0, tbsp = 15.0, tsp = 5.0, aliases = "ocet"),
        entry("Ocet balsamiczny", "Płyny", 1.06, cup = 265.0, tbsp = 16.0, tsp = 5.0),
        entry("Ocet jabłkowy", "Płyny", 1.02, cup = 255.0, tbsp = 15.0, tsp = 5.0),
        entry("Sok z cytryny", "Płyny", 1.03, cup = 258.0, tbsp = 15.0, tsp = 5.0, aliases = "sok cytrynowy"),
        entry("Wino białe wytrawne", "Płyny", 0.99, cup = 248.0, tbsp = 15.0, tsp = 5.0),
        entry("Wino czerwone wytrawne", "Płyny", 0.99, cup = 248.0, tbsp = 15.0, tsp = 5.0),
        entry("Piwo", "Płyny", 1.01, cup = 253.0, tbsp = 15.0, tsp = 5.0),
        entry("Bulion (warzywny lub drobiowy)", "Płyny", 1.0, cup = 250.0, tbsp = 15.0, tsp = 5.0, aliases = "bulion warzywny|bulion drobiowy"),
        entry("Mleko kokosowe (z puszki)", "Płyny", 0.96, cup = 240.0, tbsp = 14.0, tsp = 5.0),
        entry("Mleko owsiane", "Płyny", 1.02, cup = 255.0, tbsp = 15.0, tsp = 5.0, aliases = "napoj owsiany"),
        entry("Mleko migdałowe", "Płyny", 1.0, cup = 250.0, tbsp = 15.0, tsp = 5.0, aliases = "napoj migdalowy"),

        // --- Przyprawy i dodatki ---
        entry("Sól kuchenna (drobna)", "Przyprawy i dodatki", 1.2, cup = 300.0, tbsp = 18.0, tsp = 6.0, aliases = "sol|sol drobna"),
        entry("Sól morska (gruboziarnista)", "Przyprawy i dodatki", 0.95, cup = 238.0, tbsp = 14.0, tsp = 5.0, aliases = "sol morska gruba"),
        entry("Pieprz czarny mielony", "Przyprawy i dodatki", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, aliases = "pieprz mielony"),
        entry("Cynamon mielony", "Przyprawy i dodatki", 0.52, cup = 130.0, tbsp = 8.0, tsp = 3.0),
        entry("Proszek do pieczenia", "Przyprawy i dodatki", 0.9, cup = 225.0, tbsp = 14.0, tsp = 5.0, aliases = "proszek do ciasta|baking powder"),
        entry("Soda oczyszczona", "Przyprawy i dodatki", 0.92, cup = 230.0, tbsp = 14.0, tsp = 5.0, aliases = "soda spozywcza"),
        entry("Drożdże suszone instant", "Przyprawy i dodatki", 0.68, cup = 170.0, tbsp = 10.0, tsp = 3.0, aliases = "drozdze instant|drozdze suszone"),
        entry("Drożdże świeże (kostka)", "Przyprawy i dodatki", 1.05, cup = 263.0, tbsp = 16.0, tsp = 5.0, piece = 100.0, aliases = "drozdze swieze|kostka drozdzy"),
        entry("Ekstrakt waniliowy", "Przyprawy i dodatki", 0.9, cup = 225.0, tbsp = 14.0, tsp = 5.0, aliases = "aromat waniliowy"),
        entry("Imbir mielony", "Przyprawy i dodatki", 0.5, cup = 125.0, tbsp = 8.0, tsp = 3.0),
        entry("Gałka muszkatołowa mielona", "Przyprawy i dodatki", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, aliases = "galka muszkatolowa"),
        entry("Curry (przyprawa w proszku)", "Przyprawy i dodatki", 0.45, cup = 113.0, tbsp = 7.0, tsp = 2.0, aliases = "przyprawa curry"),
        entry("Papryka słodka mielona", "Przyprawy i dodatki", 0.5, cup = 125.0, tbsp = 8.0, tsp = 3.0, aliases = "papryka mielona"),
        entry("Musztarda", "Przyprawy i dodatki", 1.03, cup = 258.0, tbsp = 16.0, tsp = 5.0, aliases = "musztarda stolowa"),
        entry("Majonez", "Przyprawy i dodatki", 0.92, cup = 230.0, tbsp = 14.0, tsp = 5.0),
        entry("Ketchup", "Przyprawy i dodatki", 1.1, cup = 275.0, tbsp = 17.0, tsp = 6.0),
        entry("Sos sojowy", "Przyprawy i dodatki", 1.1, cup = 275.0, tbsp = 17.0, tsp = 6.0),
        entry("Chrzan tarty", "Przyprawy i dodatki", 1.0, cup = 250.0, tbsp = 15.0, tsp = 5.0),
        entry("Żelatyna w proszku", "Przyprawy i dodatki", 0.85, cup = 213.0, tbsp = 13.0, tsp = 4.0),
        entry("Kwasek cytrynowy (proszek)", "Przyprawy i dodatki", 0.9, cup = 225.0, tbsp = 14.0, tsp = 5.0, aliases = "kwasek cytrynowy do przetworow"),

        // --- Warzywa i owoce ---
        entry("Czosnek (ząbek, posiekany)", "Warzywa i owoce", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, piece = 5.0, aliases = "czosnek zabek|zabek czosnku"),
        entry("Cebula (średnia, posiekana)", "Warzywa i owoce", 0.5, cup = 125.0, tbsp = 8.0, tsp = 3.0, piece = 110.0, aliases = "cebula"),
        entry("Cebula czerwona (średnia, posiekana)", "Warzywa i owoce", 0.5, cup = 125.0, tbsp = 8.0, tsp = 3.0, piece = 100.0),
        entry("Ziemniak (średni, w kostkę)", "Warzywa i owoce", 0.6, cup = 150.0, tbsp = 9.0, tsp = 3.0, piece = 150.0, aliases = "ziemniak"),
        entry("Marchew (średnia, starta)", "Warzywa i owoce", 0.45, cup = 113.0, tbsp = 7.0, tsp = 2.0, piece = 80.0, aliases = "marchew"),
        entry("Pomidor (średni, pokrojony)", "Warzywa i owoce", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, piece = 120.0, aliases = "pomidor"),
        entry("Cukinia (średnia, starta)", "Warzywa i owoce", 0.45, cup = 113.0, tbsp = 7.0, tsp = 2.0, piece = 300.0, aliases = "cukinia"),
        entry("Papryka czerwona (średnia, pokrojona)", "Warzywa i owoce", 0.45, cup = 113.0, tbsp = 7.0, tsp = 2.0, piece = 150.0, aliases = "papryka"),
        entry("Jabłko (średnie, pokrojone)", "Warzywa i owoce", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, piece = 180.0, aliases = "jablko"),
        entry("Banan (średni, obrany, rozgnieciony)", "Warzywa i owoce", 0.65, cup = 163.0, tbsp = 10.0, tsp = 3.0, piece = 120.0, aliases = "banan"),
        entry("Cytryna (średnia)", "Warzywa i owoce", 0.95, cup = 238.0, tbsp = 14.0, tsp = 5.0, piece = 100.0, aliases = "cytryna"),
        entry("Seler naciowy (łodyga, pokrojona)", "Warzywa i owoce", 0.45, cup = 113.0, tbsp = 7.0, tsp = 2.0, piece = 40.0, aliases = "seler naciowy"),
        entry("Pietruszka korzeń (średnia, pokrojona)", "Warzywa i owoce", 0.5, cup = 125.0, tbsp = 8.0, tsp = 3.0, piece = 50.0, aliases = "korzen pietruszki"),
        entry("Por (biała część, pokrojony)", "Warzywa i owoce", 0.35, cup = 88.0, tbsp = 5.0, tsp = 2.0, piece = 100.0, aliases = "por"),
        entry("Przecier pomidorowy (passata)", "Warzywa i owoce", 1.03, cup = 258.0, tbsp = 16.0, tsp = 5.0, aliases = "passata"),
        entry("Koncentrat pomidorowy 30%", "Warzywa i owoce", 1.1, cup = 275.0, tbsp = 17.0, tsp = 6.0, aliases = "koncentrat pomidorowy"),
        entry("Truskawki (świeże, pokrojone)", "Warzywa i owoce", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, aliases = "truskawka"),
        entry("Borówki amerykańskie (świeże)", "Warzywa i owoce", 0.65, cup = 163.0, tbsp = 10.0, tsp = 3.0, aliases = "jagody amerykanskie|borowki"),
        entry("Kapusta biała (poszatkowana)", "Warzywa i owoce", 0.3, cup = 75.0, tbsp = 5.0, tsp = 2.0, aliases = "kapusta poszatkowana"),
        entry("Kapusta kiszona (odsączona)", "Warzywa i owoce", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, aliases = "kiszona kapusta"),

        // --- Inne ---
        entry("Bułka tarta", "Inne", 0.45, cup = 113.0, tbsp = 7.0, tsp = 2.0),
        entry("Płatki kukurydziane (kruszone)", "Inne", 0.15, cup = 38.0, tbsp = 2.0, tsp = 1.0, aliases = "corn flakes"),
        entry("Otręby owsiane", "Inne", 0.4, cup = 100.0, tbsp = 6.0, tsp = 2.0),
        entry("Słód jęczmienny w proszku", "Inne", 0.55, cup = 138.0, tbsp = 8.0, tsp = 3.0, aliases = "slod jeczmienny"),
        entry("Agar-agar (proszek)", "Inne", 0.7, cup = 175.0, tbsp = 11.0, tsp = 4.0, aliases = "agar"),
        entry("Guma ksantanowa", "Inne", 0.5, cup = 125.0, tbsp = 8.0, tsp = 3.0, aliases = "xantan"),
        entry("Płatki drożdżowe odżywcze", "Inne", 0.3, cup = 75.0, tbsp = 5.0, tsp = 2.0, aliases = "drozdze odzywcze"),
        entry("Płatki ziemniaczane instant (puree)", "Inne", 0.25, cup = 63.0, tbsp = 4.0, tsp = 1.0, aliases = "puree w proszku"),
        entry("Grzanki (krutony)", "Inne", 0.25, cup = 63.0, tbsp = 4.0, tsp = 1.0, aliases = "krutony"),
        entry("Otręby żytnie", "Inne", 0.35, cup = 88.0, tbsp = 5.0, tsp = 2.0),
    )

    /**
     * Buduje wpis bazy wagowej. `nameNormalized` liczymy zawsze z [name] — nigdy
     * ręcznie — tak samo aliasy, żeby wyszukiwanie po `DensityDao` (które dostaje
     * już znormalizowane zapytanie) trafiało niezależnie od polskich znaków.
     */
    private fun entry(
        name: String,
        category: String,
        gramsPerMl: Double,
        cup: Double? = null,
        tbsp: Double? = null,
        tsp: Double? = null,
        piece: Double? = null,
        aliases: String = "",
    ): IngredientDensityEntity {
        val normalizedAliases = aliases.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("|") { TextNormalizer.normalize(it) }
        return IngredientDensityEntity(
            name = name,
            nameNormalized = TextNormalizer.normalize(name),
            aliases = normalizedAliases,
            category = category,
            gramsPerMl = gramsPerMl,
            gramsPerCup = cup,
            gramsPerTablespoon = tbsp,
            gramsPerTeaspoon = tsp,
            gramsPerPiece = piece,
            isBuiltIn = true,
        )
    }
}
