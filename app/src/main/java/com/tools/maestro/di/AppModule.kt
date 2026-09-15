package com.tools.maestro.di

import android.content.Context
import androidx.room.Room
import com.tools.maestro.data.local.TOolsDatabase
import com.tools.maestro.data.local.dao.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module for application-level singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provide Room database instance.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): TOolsDatabase {
        return Room.databaseBuilder(
            context,
            TOolsDatabase::class.java,
            TOolsDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Provide ProjectDao from database.
     */
    @Provides
    @Singleton
    fun provideProjectDao(database: TOolsDatabase): ProjectDao {
        return database.projectDao()
    }
}
