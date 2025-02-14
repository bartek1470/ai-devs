plugins {
    id("aidevs.simple-lib-conventions")
}

val apacheCommonsCodecVersion = "1.17.1"
val kotlinCoroutinesVersion = "1.9.0"
val exposedVersion = "0.57.0"
val sqliteVersion = "3.47.1.0"
val kotlinLoggingVersion = "7.0.0"
val jsoupVersion = "1.18.1"
val zip4jVersion = "2.11.5"

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("commons-codec:commons-codec:$apacheCommonsCodecVersion")
    api("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingVersion")
    api("net.lingala.zip4j:zip4j:$zip4jVersion")
    api("org.jetbrains.exposed:exposed-spring-boot-starter:$exposedVersion")
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinCoroutinesVersion")
    api("org.jsoup:jsoup:$jsoupVersion")
    api("org.springframework.ai:spring-ai-ollama")
    api("org.springframework.ai:spring-ai-openai-spring-boot-starter")
    api("org.springframework.ai:spring-ai-pdf-document-reader")
    api("org.springframework.ai:spring-ai-qdrant-store-spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-data-neo4j")
    api("org.springframework.shell:spring-shell-starter")
    api("org.xerial:sqlite-jdbc:$sqliteVersion")
    testFixturesApi("org.jetbrains.kotlin:kotlin-test-junit5")
    testFixturesApi("org.springframework.boot:spring-boot-starter-test")
    testFixturesRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
