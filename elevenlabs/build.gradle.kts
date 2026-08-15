@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()
    abiValidation()

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ElevenLabs"
            isStatic = true
        }
    }
    
    android {
       namespace = "dev.yveskalume.elevenlabs.sdk"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization.json)
            implementation(libs.ktor.client.websockets)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (!version.toString().endsWith("-SNAPSHOT")) {
        signAllPublications()
    }

    pom {
        name.set("ElevenLabs KMP")
        description.set("A Kotlin Multiplatform SDK for the ElevenLabs API.")
        inceptionYear.set("2026")
        url.set("https://github.com/yveskalume/elevenlabs-kmp")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("yveskalume")
                name.set("Yves Kalume")
                url.set("https://github.com/yveskalume")
            }
        }

        scm {
            url.set("https://github.com/yveskalume/elevenlabs-kmp")
            connection.set("scm:git:git://github.com/yveskalume/elevenlabs-kmp.git")
            developerConnection.set("scm:git:ssh://git@github.com/yveskalume/elevenlabs-kmp.git")
        }
    }
}
