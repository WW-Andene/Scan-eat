package fr.scanneat.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.scanneat.data.local.db.AppDatabase
import fr.scanneat.data.local.db.MIGRATION_1_2
import fr.scanneat.data.local.db.MIGRATION_2_3
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "scanneat.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()   // safety net only — real migrations registered above
            .build()

    @Provides fun provideScanHistoryDao(db: AppDatabase)  = db.scanHistoryDao()
    @Provides fun provideConsumptionDao(db: AppDatabase)  = db.consumptionDao()
    @Provides fun provideCustomFoodDao(db: AppDatabase)   = db.customFoodDao()
    @Provides fun provideWeightDao(db: AppDatabase)       = db.weightDao()
    @Provides fun provideActivityDao(db: AppDatabase)     = db.activityDao()
    @Provides fun provideMealTemplateDao(db: AppDatabase) = db.mealTemplateDao()
    @Provides fun provideRecipeDao(db: AppDatabase)       = db.recipeDao()
}
