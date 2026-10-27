package pl.przepisak.data.remote.gemini

/**
 * Prompty budujące treść żądania do Gemini.
 *
 * Osobny wariant na każdy [GeminiContentKind], bo źródła różnią się jakością:
 * transkrypcja z automatycznych napisów wymaga innych zastrzeżeń niż czysta
 * strona WWW, a suchy opis filmu — jeszcze innych (zakaz zgadywania).
 * `responseSchema` w żądaniu i tak wymusza kształt JSON-a, ale powtarzamy to
 * też w tekście promptu — dodatkowa siatka bezpieczeństwa, tania w utrzymaniu.
 */
internal object GeminiPrompts {

    fun build(kind: GeminiContentKind, content: String): String {
        val roleInstructions = when (kind) {
            GeminiContentKind.WEB_PAGE -> WEB_PAGE_INSTRUCTIONS
            GeminiContentKind.VIDEO_TRANSCRIPT -> VIDEO_TRANSCRIPT_INSTRUCTIONS
            GeminiContentKind.VIDEO_DESCRIPTION -> VIDEO_DESCRIPTION_INSTRUCTIONS
            GeminiContentKind.PLAIN_TEXT -> PLAIN_TEXT_INSTRUCTIONS
        }
        return buildString {
            append(roleInstructions)
            append('\n')
            append('\n')
            append(COMMON_RULES)
            append('\n')
            append('\n')
            append(SOURCE_DELIMITER)
            append('\n')
            append(content)
        }
    }

    private val WEB_PAGE_INSTRUCTIONS = """
        Poniższy tekst jest wyciągnięty z treści strony internetowej z przepisem
        kulinarnym. Może zawierać resztki nawigacji, reklam albo komentarzy
        czytelników, niezwiązane z samym przepisem — pomiń je. Wyciągnij z tekstu
        przepis i zwróć go jako jeden obiekt JSON zgodny z podanym schematem.
    """.trimIndent()

    private val VIDEO_TRANSCRIPT_INSTRUCTIONS = """
        Poniższy tekst to automatyczna transkrypcja (napisy) filmu kulinarnego,
        wygenerowana rozpoznawaniem mowy — NIE jest to czysty, poprawiony tekst.
        Spodziewaj się błędów rozpoznawania, powtórzeń, urwanych zdań i dygresji
        niezwiązanych z przepisem (pozdrowienia, prośby o subskrypcję itp.) —
        zignoruj je. Ilości i jednostki składników najczęściej padają w
        wypowiedzi osoby gotującej ("dwie łyżki", "pół szklanki mąki") — wyłuskaj
        je z kontekstu wypowiedzi, nawet gdy nie są zapisane cyframi. Zwróć wynik
        jako jeden obiekt JSON zgodny z podanym schematem.
    """.trimIndent()

    private val VIDEO_DESCRIPTION_INSTRUCTIONS = """
        Poniższy tekst to opis filmu kulinarnego — napisów nie udało się pobrać,
        więc to jedyne dostępne źródło. Opisy bywają niepełne: mogą nie zawierać
        wszystkich składników, ilości albo pełnej listy kroków. NIE WOLNO Ci
        niczego dopowiadać ani zgadywać żadnej brakującej wartości — jeśli danej
        informacji nie ma wprost w tekście, zostaw odpowiednie pole puste (pustą
        listę albo null), zamiast wymyślać treść. Zwróć wynik jako jeden obiekt
        JSON zgodny z podanym schematem.
    """.trimIndent()

    private val PLAIN_TEXT_INSTRUCTIONS = """
        Poniższy tekst wkleił ręcznie użytkownik aplikacji i twierdzi, że zawiera
        przepis kulinarny — może nie być sformatowany jak przepis (np. zwykła
        notatka albo wiadomość od znajomego). Wyciągnij z niego przepis i zwróć
        go jako jeden obiekt JSON zgodny z podanym schematem.
    """.trimIndent()

    private val COMMON_RULES = """
        Zasady obowiązujące niezależnie od źródła:
        - Zachowaj ORYGINALNY język przepisu — nie tłumacz tytułu, składników ani
          kroków na inny język, nawet jeśli te instrukcje są po polsku.
        - Zachowaj jednostki miary DOKŁADNIE takie, jak w źródle (gramy, szklanki,
          łyżki, uncje, funty itd.) — NIE przeliczaj ich na inny system miar.
        - Jeśli źródło wyraźnie dzieli składniki na grupy (np. "Ciasto", "Polewa",
          "Do dekoracji"), odwzoruj ten podział w ingredientGroups. Jeśli źródło
          nie dzieli składników na grupy, zwróć jedną grupę z name = null.
        - Rozbij instrukcje na krótkie, pojedyncze kroki (jedna czynność na krok),
          nawet jeśli w źródle są zlepione w jeden akapit.
        - Odpowiedz WYŁĄCZNIE czystym JSON-em zgodnym ze schematem — bez
          markdownu, bez bloków ```, bez komentarzy, bez żadnego tekstu przed ani
          po samym JSON-ie.
    """.trimIndent()

    private const val SOURCE_DELIMITER = "--- TREŚĆ ŹRÓDŁOWA (poniżej, do samego końca) ---"
}
