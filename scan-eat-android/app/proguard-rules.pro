# Keep Moshi models (codegen handles adapters, but keep kotlin reflect fallback)
-keep class fr.scanneat.domain.model.** { *; }
-keep class fr.scanneat.data.remote.api.** { *; }
-keep class fr.scanneat.data.local.db.** { *; }
# RecipeComponent, TemplateItem, and OcrParser's Llm*Dto classes are Moshi-
# reflection-serialized data classes living under data.repository, not
# data.remote.api - without this they're only protected in unminified debug
# builds and R8 can rename/strip their fields in release.
-keep class fr.scanneat.data.repository.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# Retrofit + OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

# ML Kit
-keep class com.google.mlkit.** { *; }

# CameraX
-keep class androidx.camera.** { *; }
