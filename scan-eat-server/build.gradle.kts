plugins {
    kotlin("jvm")                version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin")         version "2.3.12"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group   = "fr.scanneat"
version = "0.1.0"

application {
    mainClass.set("fr.scanneat.ApplicationKt")
    // Not defaulted on here - Application.kt's CORS install() falls back to
    // anyHost() when isDevelopment is true, so any installDist/distTar build
    // (not just `gradle run`) would boot with CORS open to any origin unless
    // ALLOWED_ORIGINS is set. Pass -Dio.ktor.development=true explicitly for
    // local runs that want it instead.
}

repositories {
    mavenCentral()
}

val ktor_version     = "2.3.12"
val kotlin_version   = "2.0.21"
val logback_version  = "1.5.6"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-cors-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-default-headers-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-compression-jvm:$ktor_version")
    // Lets HEAD requests hit any GET route (notably /health) without a duplicate
    // handler - some reverse proxies, container platforms, and uptime monitors
    // probe liveness with HEAD instead of GET, which Ktor 404s on otherwise.
    implementation("io.ktor:ktor-server-auto-head-response-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")

    // Ktor client (for calling Groq + OFF)
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Test
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

tasks.shadowJar {
    archiveFileName.set("scan-eat-server.jar")
    manifest { attributes["Main-Class"] = "fr.scanneat.ApplicationKt" }
}

// Expands project.version into a resource read once at startup — /health
// previously hardcoded "0.1.0" as a literal, independent of this file's own
// `version =` above, so the two would silently diverge on the first bump.
tasks.processResources {
    filesMatching("version.properties") {
        expand("version" to project.version)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
