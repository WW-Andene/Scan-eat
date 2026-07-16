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
import fr.scanneat.data.local.db.MIGRATION_3_4
import fr.scanneat.data.local.db.MIGRATION_4_5
import fr.scanneat.data.local.db.MIGRATION_5_6
import fr.scanneat.data.local.db.MIGRATION_6_7
import fr.scanneat.data.local.db.MIGRATION_7_8
import fr.scanneat.data.local.db.MIGRATION_8_9
import fr.scanneat.data.local.db.MIGRATION_9_10
import fr.scanneat.data.local.db.MIGRATION_10_11
import fr.scanneat.data.local.db.MIGRATION_11_12
import fr.scanneat.data.local.db.MIGRATION_12_13
import fr.scanneat.data.local.db.MIGRATION_13_14
import fr.scanneat.data.local.db.MIGRATION_14_15
import fr.scanneat.data.local.db.MIGRATION_15_16
import fr.scanneat.data.local.db.MIGRATION_16_17
import fr.scanneat.data.local.db.MIGRATION_17_18
import fr.scanneat.data.local.db.MIGRATION_18_19
import fr.scanneat.data.local.db.MIGRATION_19_20
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "scanneat.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
            .fallbackToDestructiveMigration()   // safety net only — real migrations registered above
            .build()

    @Provides fun provideScanHistoryDao(db: AppDatabase)  = db.scanHistoryDao()
    @Provides fun provideConsumptionDao(db: AppDatabase)  = db.consumptionDao()
    @Provides fun provideCustomFoodDao(db: AppDatabase)   = db.customFoodDao()
    @Provides fun provideWeightDao(db: AppDatabase)       = db.weightDao()
    @Provides fun provideActivityDao(db: AppDatabase)     = db.activityDao()
    @Provides fun provideMealTemplateDao(db: AppDatabase) = db.mealTemplateDao()
    @Provides fun provideRecipeDao(db: AppDatabase)       = db.recipeDao()
    @Provides fun provideMedicationDao(db: AppDatabase)   = db.medicationDao()
    @Provides fun provideMedicationLogDao(db: AppDatabase) = db.medicationLogDao()
    @Provides fun provideScanScoreHistoryDao(db: AppDatabase) = db.scanScoreHistoryDao()
}
