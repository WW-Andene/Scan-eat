package fr.scanneat.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.scanneat.data.remote.api.GroqApi
import fr.scanneat.domain.engine.nutrition.OcrParser
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides @Singleton
    fun provideOcrParser(groqApi: GroqApi): OcrParser = OcrParser(groqApi)
}
