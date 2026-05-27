plugins {
    kotlin("jvm") version "2.3.10"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.github.pavelkuliaka"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "24"
    modules("javafx.controls", "javafx.graphics", "javafx.fxml")
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    testImplementation(kotlin("test"))
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("com.github.pavelkuliaka.gui.AppKt")
}

val printClasspath by tasks.registering {
    doLast {
        println(sourceSets["main"].runtimeClasspath.files.joinToString(":") { it.absolutePath })
    }
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}