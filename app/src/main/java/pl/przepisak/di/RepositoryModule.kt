package pl.przepisak.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.przepisak.core.util.SystemTimeProvider
import pl.przepisak.core.util.TimeProvider
import pl.przepisak.data.prefs.EncryptedSecretStore
import pl.przepisak.data.prefs.SecretStore
import pl.przepisak.data.prefs.SettingsRepository
import pl.przepisak.data.prefs.SettingsRepositoryImpl
import pl.przepisak.data.remote.gemini.GeminiApi
import pl.przepisak.data.remote.gemini.GeminiApiImpl
import pl.przepisak.data.remote.web.HtmlFetcher
import pl.przepisak.data.remote.web.KtorHtmlFetcher
import pl.przepisak.data.repository.AuthRepository
import pl.przepisak.data.repository.AuthRepositoryImpl
import pl.przepisak.data.repository.DensityRepository
import pl.przepisak.data.repository.DensityRepositoryImpl
import pl.przepisak.data.repository.ImportRepository
import pl.przepisak.data.repository.ImportRepositoryImpl
import pl.przepisak.data.repository.RecipeRepository
import pl.przepisak.data.repository.RecipeRepositoryImpl
import pl.przepisak.data.repository.SyncRepository
import pl.przepisak.data.repository.SyncRepositoryImpl
import pl.przepisak.data.repository.TaxonomyRepository
import pl.przepisak.data.repository.TaxonomyRepositoryImpl
import pl.przepisak.data.repository.TimerRepository
import pl.przepisak.data.repository.TimerRepositoryImpl
import javax.inject.Singleton

/**
 * Powiązania interfejsów z implementacjami.
 *
 * Interfejsy nie są tu ozdobą: dzięki nim testy jednostkowe parserów, skalowania
 * i minutników nie potrzebują ani sieci, ani Androida.
 */
@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds @Singleton fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds @Singleton fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton fun bindSecretStore(impl: EncryptedSecretStore): SecretStore

    @Binds @Singleton fun bindRecipeRepository(impl: RecipeRepositoryImpl): RecipeRepository

    @Binds @Singleton fun bindTaxonomyRepository(impl: TaxonomyRepositoryImpl): TaxonomyRepository

    @Binds @Singleton fun bindDensityRepository(impl: DensityRepositoryImpl): DensityRepository

    @Binds @Singleton fun bindImportRepository(impl: ImportRepositoryImpl): ImportRepository

    @Binds @Singleton fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds @Singleton fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton fun bindTimerRepository(impl: TimerRepositoryImpl): TimerRepository

    @Binds @Singleton fun bindGeminiApi(impl: GeminiApiImpl): GeminiApi

    @Binds @Singleton fun bindHtmlFetcher(impl: KtorHtmlFetcher): HtmlFetcher
}
