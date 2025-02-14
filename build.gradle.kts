plugins {
    id("com.diffplug.spotless") version "7.0.0.BETA4"
}

repositories {
    mavenCentral()
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/")
        ktlint()
        toggleOffOn()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/")
        ktlint()
        toggleOffOn()
    }
}
