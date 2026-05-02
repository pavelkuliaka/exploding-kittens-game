plugins {
    kotlin("jvm") version "2.3.10"
    application
}

group = "com.github.pavelkuliaka"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.fatboyindustrial.gson-javatime-serialisers:gson-javatime-serialisers:1.1.2")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("com.github.pavelkuliaka.MainKt")
}

val printClasspath by tasks.registering {
    doLast {
        println(sourceSets["main"].runtimeClasspath.files.joinToString(":") { it.absolutePath })
    }
}

tasks.withType<JavaExec> {
    mainClass.set("com.github.pavelkuliaka.MainKt")
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}