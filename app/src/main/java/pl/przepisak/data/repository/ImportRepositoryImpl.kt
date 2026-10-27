package pl.przepisak.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.Outcome
import pl.przepisak.core.util.TimeProvider
import pl.przepisak.core.util.map
import pl.przepisak.data.media.PhotoStore
import pl.przepisak.data.remote.gemini.GeminiApi
import pl.przepisak.data.remote.gemini.GeminiContentKind
import pl.przepisak.data.remote.web.HtmlFetcher
import pl.przepisak.data.remote.web.HtmlTextExtractor
import pl.przepisak.data.remote.web.MicrodataRecipeParser
import pl.przepisak.data.remote.web.SchemaOrgRecipeParser
import pl.przepisak.data.remote.youtube.OEmbedFetcher
import pl.przepisak.data.remote.youtube.VideoDetails
import pl.przepisak.data.remote.youtube.YouTubeTranscriptFetcher
import pl.przepisak.domain.importer.UrlClassifier
import pl.przepisak.domain.model.Category
import pl.przepisak.domain.model.ImportSource
import pl.przepisak.domain.model.ImportedRecipe
import pl.przepisak.domain.model.IngredientGroup
import pl.przepisak.domain.model.PhotoRef
import pl.przepisak.domain.model.Recipe
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.model.Step
import pl.przepisak.domain.model.Tag
import pl.przepisak.domain.scaling.IngredientLineParser
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Import przepisów z sieci (RF-04, RF-05, RF-06).
 *
 * Kolejność prób dla stron WWW jest celowa i idzie od najtańszej i najdokładniejszej
 * metody do najdroższej: dane strukturalne Schema.org → mikrodane → Gemini.
 * Dzięki temu na większości serwisów kulinarnych import działa bez klucza API
 * i nie zużywa darmowego limitu.
 */
@Singleton
class ImportRepositoryImpl @Inject constructor(
    private val htmlFetcher: HtmlFetcher,
    private val schemaOrgParser: SchemaOrgRecipeParser,
    private val microdataParser: MicrodataRecipeParser,
    private val textExtractor: HtmlTextExtractor,
    private val geminiApi: GeminiApi,
    private val youTubeFetcher: YouTubeTranscriptFetcher,
    private val oEmbedFetcher: OEmbedFetcher,
    private val ingredientLineParser: IngredientLineParser,
    private val photoStore: PhotoStore,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ImportRepository {

    override suspend fun importFromUrl(url: String): Outcome<ImportedRecipe> = withContext(io) {
        val source = UrlClassifier.classify(url)
            ?: return@withContext Outcome.Failure(AppError.UnsupportedUrl(url))

        val cleanUrl = UrlClassifier.cleanTrackingParams(source.url)

        when (source) {
            is ImportSource.WebPage -> importWebPage(cleanUrl)
            is ImportSource.YouTubeVideo -> importYouTube(source.videoId, cleanUrl)
            is ImportSource.ShortVideo -> importShortVideo(cleanUrl, source.platform)
        }
    }

    override suspend fun importFromText(text: String): Outcome<ImportedRecipe> = withContext(io) {
        UrlClassifier.importableLink(text)?.let { return@withContext importFromUrl(it) }
        geminiApi.extractRecipe(content = text, kind = GeminiContentKind.PLAIN_TEXT)
    }

    override fun extractUrl(sharedText: String): String? = UrlClassifier.extractFirstUrl(sharedText)

    // -----------------------------------------------------------------------
    // Strony WWW
    // -----------------------------------------------------------------------

    private suspend fun importWebPage(url: String): Outcome<ImportedRecipe> {
        val page = when (val result = htmlFetcher.fetch(url)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }

        schemaOrgParser.parse(page)?.let { return Outcome.Success(fillGaps(it, page.html, url)) }
        microdataParser.parse(page)?.let { return Outcome.Success(fillGaps(it, page.html, url)) }

        // Brak danych strukturalnych — zostaje przepuszczenie treści przez model.
        val text = textExtractor.extractMainText(page.html)
        if (text.isBlank()) {
            return Outcome.Failure(AppError.ParsingFailed("strona nie zawiera czytelnej treści"))
        }

        return geminiApi.extractRecipe(
            content = text,
            kind = GeminiContentKind.WEB_PAGE,
            sourceUrl = url,
            sourceName = textExtractor.extractSiteName(page.html, url),
        ).map { imported -> fillGaps(imported, page.html, url) }
    }

    /**
     * Uzupełnia braki metadanymi Open Graph.
     *
     * Dane strukturalne bywają niepełne — częsty przypadek to przepis bez zdjęcia
     * w JSON-LD, choć `og:image` na stronie jest. Szkoda tracić zdjęcie tylko
     * dlatego, że autor nie wypełnił jednego pola.
     */
    private fun fillGaps(imported: ImportedRecipe, html: String, url: String): ImportedRecipe =
        imported.copy(
            imageUrl = imported.imageUrl ?: textExtractor.extractImage(html, url),
            sourceName = imported.sourceName ?: textExtractor.extractSiteName(html, url),
            sourceUrl = imported.sourceUrl ?: url,
            title = imported.title.ifBlank { textExtractor.extractTitle(html).orEmpty() },
        )

    // -----------------------------------------------------------------------
    // Filmy
    // -----------------------------------------------------------------------

    private suspend fun importYouTube(videoId: String, url: String): Outcome<ImportedRecipe> {
        val details = when (val result = youTubeFetcher.fetch(videoId)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        if (!details.hasUsableText) return Outcome.Failure(AppError.NoTranscriptAvailable)

        val transcript = details.transcript
        val hasTranscript = !transcript.isNullOrBlank()

        val content = if (hasTranscript) {
            buildString {
                appendLine("OPIS:")
                appendLine(details.description.orEmpty())
                appendLine()
                appendLine("NAPISY:")
                append(transcript)
            }
        } else {
            details.description.orEmpty()
        }

        val kind = if (hasTranscript) {
            GeminiContentKind.VIDEO_TRANSCRIPT
        } else {
            GeminiContentKind.VIDEO_DESCRIPTION
        }

        return geminiApi.extractRecipe(
            content = content,
            kind = kind,
            sourceUrl = url,
            sourceName = details.channelName,
        ).map { imported -> imported.withVideoMetadata(details, hasTranscript) }
    }

    private suspend fun importShortVideo(url: String, platform: String): Outcome<ImportedRecipe> {
        val details = when (val result = oEmbedFetcher.fetch(url, platform)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        if (!details.hasUsableText) return Outcome.Failure(AppError.NoTranscriptAvailable)

        return geminiApi.extractRecipe(
            content = details.description ?: details.title.orEmpty(),
            kind = GeminiContentKind.VIDEO_DESCRIPTION,
            sourceUrl = url,
            sourceName = details.channelName ?: platform,
        ).map { imported ->
            imported.copy(
                imageUrl = imported.imageUrl ?: details.thumbnailUrl,
                warnings = imported.warnings +
                    "Z $platform da się odczytać tylko opis — sprawdź, czy nie brakuje składników.",
            )
        }
    }

    private fun ImportedRecipe.withVideoMetadata(
        details: VideoDetails,
        hadTranscript: Boolean,
    ): ImportedRecipe = copy(
        imageUrl = imageUrl ?: details.thumbnailUrl,
        sourceName = sourceName ?: details.channelName,
        warnings = if (hadTranscript) {
            warnings
        } else {
            warnings + "Film nie ma napisów — przepis odczytano z samego opisu."
        },
    )

    // -----------------------------------------------------------------------
    // Zamiana na przepis gotowy do zapisu
    // -----------------------------------------------------------------------

    override suspend fun toAggregate(
        imported: ImportedRecipe,
        downloadPhoto: Boolean,
    ): RecipeAggregate = withContext(io) {
        val now = time.nowMs()

        val groups = imported.ingredientGroups
            .map { group ->
                IngredientGroup(
                    name = group.name,
                    ingredients = group.lines.mapIndexed { index, line ->
                        ingredientLineParser.toIngredient(line, index)
                    },
                )
            }
            .filter { it.ingredients.isNotEmpty() }
            .mapIndexed { index, group -> group.copy(position = index) }
            .ifEmpty { listOf(IngredientGroup()) }

        val steps = imported.steps
            .filter { it.isNotBlank() }
            .mapIndexed { index, text -> Step(position = index, text = text) }

        val photo = if (downloadPhoto) downloadPhoto(imported.imageUrl) else PhotoRef.fromUrl(imported.imageUrl)

        RecipeAggregate(
            recipe = Recipe(
                remoteId = UUID.randomUUID().toString(),
                title = imported.title,
                description = imported.description,
                prepTimeMinutes = imported.prepTimeMinutes,
                cookTimeMinutes = imported.cookTimeMinutes,
                totalTimeMinutesOverride = imported.totalTimeMinutes,
                difficulty = imported.difficulty,
                servings = imported.servings,
                servingsLabel = imported.servingsLabel,
                photo = photo,
                sourceUrl = imported.sourceUrl,
                sourceName = imported.sourceName,
                notes = imported.notes,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
            groups = groups,
            steps = steps,
            // Kategoria i tagi jadą po nazwie — repozytorium przepisów samo je
            // odnajdzie albo założy.
            tags = imported.tagNames.map { Tag(name = it) },
            category = imported.categoryName?.let { Category(name = it) },
        )
    }

    /**
     * Zdjęcie pobieramy od razu, żeby przepis dał się przeglądać bez sieci.
     * Gdy pobranie się nie uda, zapamiętujemy sam adres — obraz doczyta się później.
     */
    private suspend fun downloadPhoto(imageUrl: String?): PhotoRef {
        if (imageUrl.isNullOrBlank()) return PhotoRef.EMPTY
        return when (val bytes = htmlFetcher.fetchBytes(imageUrl)) {
            is Outcome.Failure -> PhotoRef.fromUrl(imageUrl)
            is Outcome.Success -> when (val saved = photoStore.saveFromBytes(bytes.value)) {
                is Outcome.Failure -> PhotoRef.fromUrl(imageUrl)
                is Outcome.Success -> PhotoRef(localFileName = saved.value, sourceUrl = imageUrl)
            }
        }
    }
}
