import gradle.kotlin.dsl.accessors._7350bef71a1a89c7c2584fed70f66434.java
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    java
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "pl.bartek"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

configure<DependencyManagementExtension> {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.0-M6")
        mavenBom("org.springframework.shell:spring-shell-dependencies:3.3.3")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":simple:common"))
}

configure<KotlinJvmProjectExtension> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xdebug")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
