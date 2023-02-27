import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.10"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("MainKt")
}

dependencies {
    implementation("org.http4k:http4k-core:4.39.0.0")
    implementation("org.http4k:http4k-graphql:4.39.0.0")
    implementation("org.http4k:http4k-client-okhttp:4.39.0.0")
    implementation("com.github.ajalt.clikt:clikt:3.5.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
