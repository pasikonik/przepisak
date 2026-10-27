package pl.przepisak.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.Outcome
import pl.przepisak.core.util.TimeProvider
import pl.przepisak.data.local.dao.RecipeDao
import pl.przepisak.data.local.toDomain
import pl.przepisak.data.media.PhotoPaths
import pl.przepisak.data.media.PhotoStore
import pl.przepisak.data.prefs.SettingsRepository
import pl.przepisak.data.remote.supabase.RecipeRemoteDataSource
import pl.przepisak.data.remote.supabase.RemoteRecipeRow
import pl.przepisak.data.remote.supabase.toAggregate
import pl.przepisak.data.remote.supabase.toPayload
import pl.przepisak.data.local.relation.RecipeWithDetails
import pl.przepisak.domain.model.RecipeAggregate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kopia zapasowa i synchronizacja z Supabase (RF-15).
 *
 * Zasada rozstrzygania konfliktów: **wygrywa nowszy `updated_at`** (last-write-wins)
 * na poziomie całego przepisu. Przy jednym użytkowniku i kilku urządzeniach jest to
 * zachowanie przewidywalne, a co ważniejsze — nie potrafi wytworzyć przepisu
 * zlepionego z dwóch wersji, w którym składniki są z jednej, a kroki z drugiej.
 *
 * Wiersze i zdjęcia przetwarzamy po kolei, nie równolegle: darmowy plan Supabase ma
 * limity, a to zadanie działa w tle i nie ma powodu się spieszyć.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val recipeRepository: RecipeRepository,
    private val authRepository: AuthRepository,
    private val remote: RecipeRemoteDataSource,
    private val photoStore: PhotoStore,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SyncRepository {

    private val statusState = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    override val status: Flow<SyncStatus> = statusState.asStateFlow()

    override fun observePendingCount(): Flow<Int> = recipeDao.observeDirtyCount()

    override suspend fun syncNow(): Outcome<SyncStatus.Success> = withContext(io) {
        val current = settings.current()
        if (!current.isSupabaseConfigured) {
            return@withContext fail(AppError.SupabaseNotConfigured)
        }
        if (!authRepository.restoreSession()) {
            return@withContext fail(AppError.NotAuthenticated)
        }

        statusState.value = SyncStatus.Running

        val pushed = when (val result = push()) {
            is Outcome.Failure -> return@withContext fail(result.error)
            is Outcome.Success -> result.value
        }

        val pulled = when (val result = pull(current.lastSyncAtEpochMs)) {
            is Outcome.Failure -> return@withContext fail(result.error)
            is Outcome.Success -> result.value
        }

        recipeDao.purgeSyncedTombstones()

        val success = SyncStatus.Success(pushed = pushed, pulled = pulled, atEpochMs = time.nowMs())
        statusState.value = success
        Outcome.Success(success)
    }

    override suspend fun pushOnly(): Outcome<Int> = withContext(io) {
        if (!settings.current().isSupabaseConfigured) return@withContext Outcome.Failure(AppError.SupabaseNotConfigured)
        if (!authRepository.restoreSession()) return@withContext Outcome.Failure(AppError.NotAuthenticated)
        push()
    }

    // -----------------------------------------------------------------------
    // Wysyłanie
    // -----------------------------------------------------------------------

    private suspend fun push(): Outcome<Int> {
        val dirty = recipeDao.getDirtyRecipes()
        if (dirty.isEmpty()) return Outcome.Success(0)

        var sent = 0
        for (details in dirty) {
            // Zdjęcia wysyłamy przed zbudowaniem dokumentu, żeby zapisane w nim
            // ścieżki zdalne były już aktualne.
            uploadPhotos(details)

            val refreshed = recipeDao.getDetails(details.recipe.id) ?: continue
            val aggregate = refreshed.toDomain()
            val row = RemoteRecipeRow(
                id = aggregate.recipe.remoteId,
                payload = aggregate.toPayload(),
                updatedAt = aggregate.recipe.updatedAtEpochMs,
                deleted = refreshed.recipe.deletedAt != null,
            )

            when (val result = remote.upsert(listOf(row))) {
                is Outcome.Failure -> return result
                is Outcome.Success -> {
                    // markSynced nie zdejmie flagi, jeśli przepis zmienił się
                    // w trakcie wysyłki — wtedy pojedzie ponownie następnym razem.
                    recipeDao.markSynced(
                        recipeId = aggregate.recipe.id,
                        syncedUpdatedAt = aggregate.recipe.updatedAtEpochMs,
                        nowMs = time.nowMs(),
                    )
                    sent++
                }
            }
        }
        return Outcome.Success(sent)
    }

    private suspend fun uploadPhotos(details: RecipeWithDetails) {
        val remoteId = details.recipe.remoteId

        details.recipe.photo.localFileName
            ?.takeIf { details.recipe.photo.remotePath == null }
            ?.let { fileName ->
                uploadPhoto(remoteId, fileName)?.let { path ->
                    recipeDao.setRecipePhotoRemotePath(details.recipe.id, path)
                }
            }

        details.steps.forEach { step ->
            step.photo.localFileName
                ?.takeIf { step.photo.remotePath == null }
                ?.let { fileName ->
                    uploadPhoto(remoteId, fileName)?.let { path ->
                        recipeDao.setStepPhotoRemotePath(step.id, path)
                    }
                }
        }
    }

    private suspend fun uploadPhoto(remoteId: String, fileName: String): String? {
        val bytes = photoStore.readBytes(fileName) ?: return null
        val path = "$remoteId/$fileName"
        return when (remote.uploadPhoto(path, bytes)) {
            is Outcome.Failure -> null
            is Outcome.Success -> path
        }
    }

    // -----------------------------------------------------------------------
    // Pobieranie
    // -----------------------------------------------------------------------

    private suspend fun pull(lastSyncAt: Long): Outcome<Int> {
        val rows = when (val result = remote.fetchChangedSince(lastSyncAt)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        if (rows.isEmpty()) return Outcome.Success(0)

        var applied = 0
        for (row in rows) {
            val local = recipeDao.getDetailsByRemoteId(row.id)

            if (row.deleted) {
                // Nie usuwamy lokalnie, jeśli użytkownik zdążył zmienić przepis
                // po tym, jak nagrobek powstał na innym urządzeniu.
                if (local != null && local.recipe.updatedAt <= row.updatedAt) {
                    recipeDao.deleteRecipeHard(local.recipe.id)
                    applied++
                }
                continue
            }

            if (local != null && local.recipe.updatedAt >= row.updatedAt) continue

            val aggregate: RecipeAggregate = row.payload.toAggregate(local?.recipe?.id ?: 0L)
            val savedId = recipeRepository.saveRecipe(aggregate)
            // To dane z chmury, a nie lokalna zmiana — flagę „do wysłania” zdejmujemy,
            // inaczej odsyłalibyśmy z powrotem to, co właśnie przyszło.
            recipeDao.markSynced(savedId, aggregate.recipe.updatedAtEpochMs, time.nowMs())
            downloadMissingPhotos(savedId)
            applied++
        }

        // Znacznik ustawiamy na najnowszy `updated_at` z pobranych wierszy, a nie na
        // „teraz”: zegary urządzeń się rozjeżdżają i użycie lokalnego czasu mogłoby
        // trwale pominąć zmiany zapisane w chmurze z niewielkim opóźnieniem.
        rows.maxOfOrNull { it.updatedAt }?.let { settings.setLastSyncAt(it + 1) }
        return Outcome.Success(applied)
    }

    /** Zdjęcia ściągamy na dysk, żeby przepis dał się przeglądać bez sieci. */
    private suspend fun downloadMissingPhotos(recipeId: Long) {
        val details = recipeDao.getDetails(recipeId) ?: return

        val targets = buildList {
            details.recipe.photo.remotePath
                ?.takeIf { !PhotoPaths.exists(context, details.recipe.photo.localFileName) }
                ?.let { add(it to null) }
            details.steps.forEach { step ->
                step.photo.remotePath
                    ?.takeIf { !PhotoPaths.exists(context, step.photo.localFileName) }
                    ?.let { add(it to step.id) }
            }
        }

        for ((path, stepId) in targets) {
            val bytes = when (val result = remote.downloadPhoto(path)) {
                is Outcome.Failure -> continue
                is Outcome.Success -> result.value
            }
            when (val saved = photoStore.saveFromBytes(bytes)) {
                is Outcome.Failure -> continue
                is Outcome.Success ->
                    // Zapis nadaje nową nazwę pliku, więc trzeba ją wpisać do wiersza.
                    if (stepId == null) {
                        recipeDao.setRecipePhotoLocalFile(recipeId, saved.value)
                    } else {
                        recipeDao.setStepPhotoLocalFile(stepId, saved.value)
                    }
            }
        }
    }

    private fun fail(error: AppError): Outcome<SyncStatus.Success> {
        statusState.value = SyncStatus.Failed(error)
        return Outcome.Failure(error)
    }
}
