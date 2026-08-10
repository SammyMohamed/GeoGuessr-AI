plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    application
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.geoguessr"
version = "0.1.0"

application {
    // Kotlin compiles top-level functions in Application.kt into a class
    // called ApplicationKt — that's the actual JVM entry point.
    mainClass.set("com.geoguessr.ApplicationKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"
val coroutinesVersion = "1.8.1"
val exposedVersion = "0.53.0"

dependencies {
    // Ktor CLIENT — used by KtorInferenceClient to call the Python service.
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // Ktor SERVER — the actual API (routes, request/response handling).
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")

    // Exposed — SQL DSL, deliberately not the DAO/entity API, so queries stay
    // explicit rather than going through ORM-style entity mapping.
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // H2 for now (zero-setup, file/in-memory) — swap for the Postgres JDBC
    // driver + connection URL when deploying for real.
    implementation("com.h2database:h2:2.2.224")

    // AWS SDK v2 — S3 client for S3ImageStorage
    implementation("software.amazon.awssdk:s3:2.28.11")

    // Postgres — used in production (Neon) via DATABASE_URL; H2 above stays
    // for local dev and tests, so both drivers need to be present.
    implementation("org.postgresql:postgresql:42.7.4")

    // Real logging — without this, Ktor's own startup/request logs are
    // silently dropped by a no-op SLF4J logger instead of printed.
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Test-only: Ktor's MockEngine lets us test the inference client without
    // any real network calls, mirroring how we mocked the CLIP processor in
    // Python. ktor-server-test-host does the same for routes.
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.13.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

tasks.test {
    useJUnitPlatform()
}