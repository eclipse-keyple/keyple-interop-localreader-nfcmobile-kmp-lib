plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.dokka)
    id("com.diffplug.spotless")
    id("maven-publish")
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    if (System.getProperty("os.name").lowercase().contains("mac")) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "keypleinteropreadernfcmobile"
                isStatic = false
            }
        }
    }

    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    jvm {
        kotlin {
            jvmToolchain(libs.versions.jdk.get().toInt())
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.keyple.interop.jsonapi.client.kmp.lib)

            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization)

            implementation(libs.napier)
        }

        androidMain.dependencies {
        }

        iosMain.dependencies {
        }

        jvmMain.dependencies {
        }
    }
}

android {
    namespace = "org.eclipse.keyple.interop.localreader.nfcmobile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

publishing {
    repositories {
        mavenLocal {
            //...
        }
    }
}

///////////////////////////////////////////////////////////////////////////////
//  TASKS CONFIGURATION
///////////////////////////////////////////////////////////////////////////////
tasks {
    dokkaHtml {
        outputDirectory.set(layout.buildDirectory.dir("dokka"))

        dokkaSourceSets {
            configureEach {
                includeNonPublic.set(false)
                skipDeprecated.set(true)
                reportUndocumented.set(true)
                jdkVersion.set(libs.versions.jdk.get().toInt())
            }
        }
    }

    spotless {
        kotlin {
            target("**/*.kt")
            ktfmt()
            licenseHeaderFile("${project.rootDir}/LICENSE_HEADER")
        }
    }
}