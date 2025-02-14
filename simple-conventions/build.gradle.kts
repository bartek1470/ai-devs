plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

val kotlinVersion = "1.9.25"
val springBootVersion = "3.3.5"
val springDependencyManagementVersion = "1.1.6"

dependencies {
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin.plugin.spring:org.jetbrains.kotlin.plugin.spring.gradle.plugin:$kotlinVersion")
    implementation("org.springframework.boot:org.springframework.boot.gradle.plugin:$springBootVersion")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:$springDependencyManagementVersion")
}
