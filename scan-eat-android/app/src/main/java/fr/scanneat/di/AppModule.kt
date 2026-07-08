package fr.scanneat.di

import android.content.Context
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.scanneat.data.local.db.*
import fr.scanneat.data.local.db.MIGRATION_1_2
import fr.scanneat.data.local.db.MIGRATION_2_3
import fr.scanneat.data.remote.api.*
import fr.scanneat.domain.engine.OcrParser
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
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

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)   // Groq vision can be slow
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", OFF_USER_AGENT)
                    .build()
            )
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    // Named "groq" — base URL is the Groq API; also used by ScanRepository
    // to build dynamic server Retrofit instances (reuses same OkHttpClient).
    @Provides @Singleton @Named("groq")
    fun provideGroqRetrofit(okHttp: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides @Singleton @Named("off")
    fun provideOffRetrofit(okHttp: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides @Singleton
    fun provideGroqApi(@Named("groq") retrofit: Retrofit): GroqApi =
        retrofit.create(GroqApi::class.java)

    @Provides @Singleton
    fun provideOffApi(@Named("off") retrofit: Retrofit): OpenFoodFactsApi =
        retrofit.create(OpenFoodFactsApi::class.java)

    @Provides @Singleton
    fun provideOcrParser(groqApi: GroqApi): OcrParser = OcrParser(groqApi)
}
