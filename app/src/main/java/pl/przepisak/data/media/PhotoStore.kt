package pl.przepisak.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.Outcome
import pl.przepisak.core.util.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Magazyn zdjęć przepisów na dysku (RF-14).
 *
 * Odpowiada za trzy rzeczy: zapis nowego zdjęcia (przez [ImageCompressor]),
 * udostępnienie pliku aparatowi do zrobienia zdjęcia oraz sprzątanie plików
 * osieroconych — np. gdy użytkownik dodał zdjęcie w formularzu, a potem
 * anulował edycję przepisu bez zapisu.
 */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compressor: ImageCompressor,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Kompresuje i zapisuje zdjęcie spod [uri] (np. z galerii). Zwraca samą nazwę pliku, nie ścieżkę. */
    suspend fun saveFromUri(uri: Uri): Outcome<String> = withContext(io) {
        val fileName = PhotoPaths.newFileName()
        compressor.compress(uri, PhotoPaths.file(context, fileName)).map { fileName }
    }

    /** Jak [saveFromUri], ale dla bajtów już wczytanych do pamięci (np. pobranych podczas importu). */
    suspend fun saveFromBytes(bytes: ByteArray): Outcome<String> = withContext(io) {
        val fileName = PhotoPaths.newFileName()
        compressor.compress(bytes, PhotoPaths.file(context, fileName)).map { fileName }
    }

    /** Usuwa plik zdjęcia. Brak pliku (albo `null`/pusta nazwa) nie jest błędem. */
    suspend fun delete(fileName: String?) {
        if (fileName.isNullOrBlank()) return
        withContext(io) {
            PhotoPaths.file(context, fileName).delete()
        }
    }

    suspend fun readBytes(fileName: String): ByteArray? = withContext(io) {
        val file = PhotoPaths.file(context, fileName)
        if (file.isFile) file.readBytes() else null
    }

    /**
     * Przygotowuje plik docelowy dla zdjęcia z aparatu i zwraca jego `content://` URI
     * (do przekazania w `MediaStore.ACTION_IMAGE_CAPTURE`) razem z nazwą pliku, pod
     * którą trzeba go potem zarejestrować w przepisie.
     *
     * Authority budujemy z [Context.getPackageName], a nie na sztywno wpisanego
     * `pl.przepisak.fileprovider` — wariant debug ma `applicationIdSuffix ".debug"`,
     * więc realny identyfikator aplikacji (i authority z manifestu, zbudowane jako
     * `${applicationId}.fileprovider`) różni się od tego w wariancie release.
     */
    fun createCameraOutput(): Pair<Uri, String> {
        val fileName = PhotoPaths.newFileName(prefix = "camera")
        val file = PhotoPaths.file(context, fileName)
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return uri to fileName
    }

    /** Usuwa z katalogu zdjęć wszystko, czego nazwy nie ma w [keepFileNames] (porządki po zapisie bazy). */
    suspend fun deleteOrphans(keepFileNames: Set<String>) = withContext(io) {
        val files = PhotoPaths.photosDir(context).listFiles() ?: return@withContext
        for (file in files) {
            if (file.name !in keepFileNames) file.delete()
        }
    }
}
