import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependencyManagement)
}

group = "pl.bartek"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.ai.bom
                .get()
                .toString(),
        )
    }
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.dataformat.xml)
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.ai.openai.starter)
    implementation(libs.kotlin.logging)
    implementation(libs.spring.boot.starter.actuator)
    testImplementation(libs.spring.boot.test.starter)
    testImplementation(libs.kotlin.test.junit5)
    developmentOnly(libs.spring.boot.devtools)
    testRuntimeOnly(libs.junit.platformLauncher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xdebug")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

configure<SpotlessExtension> {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
