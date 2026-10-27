package pl.przepisak.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pl.przepisak.core.util.ApplicationScope
import pl.przepisak.core.util.DefaultDispatcher
import pl.przepisak.core.util.IoDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * Zakres dla zadań, które muszą dokończyć się nawet po zamknięciu ekranu —
     * np. kompresja i zapis zdjęcia albo wysyłka do chmury. [SupervisorJob]
     * sprawia, że błąd jednego zadania nie ubija pozostałych.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
