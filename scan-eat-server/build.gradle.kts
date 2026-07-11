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
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=true")
}

repositories {
    mavenCentral()
}

val ktor_version     = "2.3.12"
val kotlin_version   = "2.0.21"
val logback_version  = "1.5.6"
val moshi_version    = "1.15.1"

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
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")

    // Ktor client (for calling Groq + OFF)
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.moshi:moshi-kotlin:$moshi_version")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:2.8.0")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.8.0")

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
