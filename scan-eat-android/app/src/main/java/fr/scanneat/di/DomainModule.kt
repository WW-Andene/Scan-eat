package fr.scanneat.di

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.scanneat.data.remote.api.GroqApi
import fr.scanneat.data.repository.scan.OcrParser
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides @Singleton
    fun provideOcrParser(groqApi: GroqApi, moshi: Moshi): OcrParser = OcrParser(groqApi, moshi)
}
