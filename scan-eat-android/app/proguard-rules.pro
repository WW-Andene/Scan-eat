# Keep Moshi models (codegen handles adapters, but keep kotlin reflect fallback)
-keep class fr.scanneat.domain.model.** { *; }
-keep class fr.scanneat.data.remote.api.** { *; }
-keep class fr.scanneat.data.local.db.** { *; }
# RecipeComponent, TemplateItem, and OcrParser's Llm*Dto classes are Moshi-
# reflection-serialized data classes living under data.repository, not
# data.remote.api - without this they're only protected in unminified debug
# builds and R8 can rename/strip their fields in release. Scoped to just these
# classes rather than the whole data.repository.** package, which also holds
# ScanRepository/HealthConnectRepository/etc - blanket-keeping those disabled
# R8 renaming for the code that handles the Groq API key and health data,
# making it far easier to reverse-engineer than it needs to be.
-keep class fr.scanneat.data.repository.planning.RecipeComponent { *; }
-keep class fr.scanneat.data.repository.planning.TemplateItem { *; }
-keep class fr.scanneat.data.repository.planning.ManualGroceryItem { *; }
-keep class fr.scanneat.data.repository.scan.LlmProductDto { *; }
-keep class fr.scanneat.data.repository.scan.LlmIngredientDto { *; }
-keep class fr.scanneat.data.repository.scan.LlmNutritionDto { *; }
-keep class fr.scanneat.data.repository.scan.ScoreSnapshot { *; }
-keep class fr.scanneat.data.repository.scan.NameOnlyDto { *; }
-keep class fr.scanneat.data.repository.nutrition.CustomFoodRepository$CustomFoodJson { *; }
# BackupBundle (data.backup.**) is Moshi-reflection-serialized for the whole
# export/import feature, and pulls in several DataStore-backed model classes
# from other packages that aren't @Database entities (already kept above) or
# otherwise Moshi-protected - without these, R8 renames their fields in
# release builds and every restore silently drops that section of the backup.
-keep class fr.scanneat.data.backup.** { *; }
-keep class fr.scanneat.data.repository.reminders.ReminderSettings { *; }
-keep class fr.scanneat.data.repository.reminders.CustomReminder { *; }
-keep class fr.scanneat.data.repository.health.FastCompletion { *; }
-keep class fr.scanneat.data.repository.biolism.BiolismRepository$BiolismBackupData { *; }
-keep class fr.scanneat.data.repository.biolism.BiolismRepository$TimerState { *; }
-keep class fr.scanneat.domain.engine.biolism.BiolismSession { *; }
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
