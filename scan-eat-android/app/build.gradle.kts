import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Release signing — reads from a git-ignored keystore.properties (local dev) or
// RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD
// env vars (CI/Play publishing pipeline), never from a literal in this file. Neither
// source exists on this repo's own CI runner, so releaseSigningConfig stays null and
// `release` falls back to no signingConfig — same as before, an unsigned APK/AAB good
// enough for the R8-minification smoke test that workflow runs, just not for upload.
// A real release build supplies one of the two sources at build time.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun releaseSigningProp(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFile     = releaseSigningProp("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningProp("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias      = releaseSigningProp("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword   = releaseSigningProp("keyPassword", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace   = "fr.scanneat"
    compileSdk  = 35

    defaultConfig {
        applicationId = "fr.scanneat.app"
        minSdk        = 26          // Android 8 — covers >98 % of active devices
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export for migration tracking
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    // A stable, checked-in debug key — not sensitive, debug builds are never
    // distributed for production. Without this, AGP falls back to whatever
    // default debug keystore exists on the machine building it; on ephemeral
    // CI runners (no persisted ~/.android) that's freshly auto-generated on
    // every run, so every CI-built debug APK ends up signed with a different
    // random certificate. Android then refuses to install a new one over an
    // existing install of the same app ("signatures don't match") without an
    // explicit uninstall first — so testers who just re-install over the old
    // app silently keep running stale code with no error they'd notice,
    // however many commits have actually landed since.
    signingConfigs {
        // AGP already auto-creates a "debug" entry — configure it in place
        // rather than create("debug"), which collides with that built-in one
        // ("Cannot add a SigningConfig with name 'debug'... already exists").
        getByName("debug") {
            storeFile     = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias      = "androiddebugkey"
            keyPassword   = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile     = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias      = releaseKeyAlias
                keyPassword   = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable    = true
            signingConfig   = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
            buildConfigField("String", "DEFAULT_GROQ_ENDPOINT", "\"https://api.groq.com/openai/v1/chat/completions\"")
            buildConfigField("String", "OFF_ENDPOINT",          "\"https://world.openfoodfacts.org/api/v2/product\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "DEFAULT_GROQ_ENDPOINT", "\"https://api.groq.com/openai/v1/chat/completions\"")
            buildConfigField("String", "OFF_ENDPOINT",          "\"https://world.openfoodfacts.org/api/v2/product\"")
            // Separate schema dir from debug: debug + release KSP tasks can run in parallel
            // (e.g. `./gradlew test`), and both writing room.schemaLocation to the same path
            // races and can throw "Empty schema file" (IllegalStateException) intermittently.
            ksp {
                arg("room.schemaLocation", "$projectDir/schemas/release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    // android-build.yml runs `./gradlew lintDebug` in CI. abortOnError keeps real
    // lint errors (not just warnings) failing that gate rather than only warning.
    lint {
        abortOnError      = true
        checkReleaseBuilds = true
    }
}

dependencies {
    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.appcompat)   // per-app language switching (AppCompatDelegate)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // Camera + ML Kit
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // Health Connect
    implementation(libs.health.connect.client)
    implementation(libs.guava)

    // Glance (home-screen "Today" widget)
    implementation(libs.glance.appwidget)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
