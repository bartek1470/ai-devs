import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "pl.bartek"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configure<DependencyManagementExtension> {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.0-M6")
        mavenBom("org.springframework.shell:spring-shell-dependencies:3.3.3")
    }
}

configure<KotlinJvmProjectExtension> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xdebug")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
