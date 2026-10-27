package pl.przepisak.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.przepisak.data.local.PrzepisakDatabase
import pl.przepisak.data.local.dao.CategoryDao
import pl.przepisak.data.local.dao.DensityDao
import pl.przepisak.data.local.dao.RecipeDao
import pl.przepisak.data.local.dao.TagDao
import pl.przepisak.data.local.dao.TimerDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PrzepisakDatabase =
        Room.databaseBuilder(context, PrzepisakDatabase::class.java, PrzepisakDatabase.NAME)
            .addCallback(PrzepisakDatabase.seedCallback)
            // Świadomie bez `fallbackToDestructiveMigration` — przepisy to dane,
            // których nie wolno cicho usunąć przy nieudanej migracji.
            .build()

    @Provides fun provideRecipeDao(db: PrzepisakDatabase): RecipeDao = db.recipeDao()

    @Provides fun provideCategoryDao(db: PrzepisakDatabase): CategoryDao = db.categoryDao()

    @Provides fun provideTagDao(db: PrzepisakDatabase): TagDao = db.tagDao()

    @Provides fun provideDensityDao(db: PrzepisakDatabase): DensityDao = db.densityDao()

    @Provides fun provideTimerDao(db: PrzepisakDatabase): TimerDao = db.timerDao()
}
