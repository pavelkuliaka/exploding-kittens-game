plugins {
    kotlin("jvm") version "2.3.10"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "com.github.pavelkuliaka"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
