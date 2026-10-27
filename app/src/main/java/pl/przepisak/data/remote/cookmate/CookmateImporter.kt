package pl.przepisak.data.remote.cookmate

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.Outcome
import pl.przepisak.data.media.PhotoStore
import pl.przepisak.data.repository.ImportRepository
import pl.przepisak.data.repository.RecipeRepository
import pl.przepisak.domain.model.PhotoRef
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/** Podsumowanie pokazywane po imporcie — liczby, nie szczegóły, to narzędzie deweloperskie. */
data class CookmateImportResult(
    val total: Int,
    val imported: Int,
    val withoutPhoto: Int,
)

/**
 * Jednorazowy import kolekcji z aplikacji Cookmate (plik `.mcb`) — narzędzie
 * deweloperskie uruchamiane ręcznie z Ustawień w buildzie debug, nie ekran
 * produktowy. Składniki i kategorie idą przez ten sam [ImportRepository],
 * którego już używa import z Internetu — nic tu nie parsujemy dwa razy.
 */
@Singleton
class CookmateImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xmlParser: CookmateXmlParser,
    private val importRepository: ImportRepository,
    private val recipeRepository: RecipeRepository,
    private val photoStore: PhotoStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun importFromMcb(uri: Uri): Outcome<CookmateImportResult> = withContext(io) {
        try {
            val cacheFile = copyToCache(uri)
            try {
                importFromZip(cacheFile)
            } finally {
                cacheFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import z Cookmate nie powiódł się", e)
            Outcome.Failure(AppError.Unknown(e))
        }
    }

    private suspend fun importFromZip(zipFile: File): Outcome<CookmateImportResult> {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val xmlEntry = entries.firstOrNull { !it.isDirectory && !isImageFileName(it.name) }
                ?: return Outcome.Failure(AppError.ParsingFailed("brak pliku XML w archiwum .mcb"))

            val xmlText = zip.getInputStream(xmlEntry).use { it.readBytes() }.toString(Charsets.UTF_8)
            val recipes = xmlParser.parse(xmlText)

            var withoutPhoto = 0
            for (recipe in recipes) {
                val photoBytes = recipe.localImageFileName
                    ?.let { name -> entries.firstOrNull { it.name.substringAfterLast('/') == name } }
                    ?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }

                val aggregate = importRepository.toAggregate(recipe.imported, downloadPhoto = photoBytes == null)
                val localPhotoFileName = photoBytes
                    ?.let { photoStore.saveFromBytes(it) }
                    ?.getOrNull()

                val finalRecipe = aggregate.recipe.copy(
                    rating = recipe.rating,
                    photo = localPhotoFileName?.let { PhotoRef(localFileName = it) } ?: aggregate.recipe.photo,
                )
                if (finalRecipe.photo.isEmpty) withoutPhoto++
                recipeRepository.saveRecipe(aggregate.copy(recipe = finalRecipe))
            }

            return Outcome.Success(
                CookmateImportResult(total = recipes.size, imported = recipes.size, withoutPhoto = withoutPhoto),
            )
        }
    }

    private fun copyToCache(uri: Uri): File {
        val file = File.createTempFile("cookmate_import", ".mcb", context.cacheDir)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("nie udało się otworzyć pliku")
        input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }
        return file
    }

    private fun isImageFileName(name: String): Boolean {
        val lower = name.substringAfterLast('/').lowercase()
        return IMAGE_EXTENSIONS.any { lower.endsWith(it) }
    }

    private companion object {
        const val TAG = "CookmateImporter"
        val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif")
    }
}
