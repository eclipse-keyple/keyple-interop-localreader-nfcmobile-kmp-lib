///////////////////////////////////////////////////////////////////////////////
//  GRADLE CONFIGURATION
///////////////////////////////////////////////////////////////////////////////

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.spotless)
  alias(libs.plugins.dokka)
  signing
  `maven-publish`
}

val title: String by project
val jvmToolchainVersion: String by project
val javaSourceLevel: String by project
val javaTargetLevel: String by project

///////////////////////////////////////////////////////////////////////////////
//  APP CONFIGURATION
///////////////////////////////////////////////////////////////////////////////

kotlin {
  jvmToolchain(jvmToolchainVersion.toInt())
  if (System.getProperty("os.name").lowercase().contains("mac")) {
    listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        )
        .forEach { iosTarget ->
          iosTarget.binaries.framework {
            baseName = "keypleinteroplocalreadernfcmobilelib"
            isStatic = false
          }
        }
  }
  androidTarget { publishLibraryVariants("release") }
  jvm { kotlin { jvmToolchain(jvmToolchainVersion.toInt()) } }
  sourceSets {
    commonMain.dependencies {
      implementation(libs.keyple.interop.jsonapi.client.kmp.lib)
      implementation(libs.kotlinx.coroutines)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization)
      implementation(libs.napier)
    }
    androidMain.dependencies {}
    iosMain.dependencies {}
    jvmMain.dependencies {}
  }
}

///////////////////////////////////////////////////////////////////////////////
//  STANDARD CONFIGURATION FOR KOTLIN-LIB MULTIPLATFORM PROJECTS
///////////////////////////////////////////////////////////////////////////////

if (project.hasProperty("releaseTag")) {
  project.version = project.property("releaseTag") as String
  println("Release mode: version set to ${project.version}")
} else {
  println("Development mode: version is ${project.version}")
}

val generatedOverviewFile = layout.buildDirectory.file("tmp/overview-dokka.md")
val dokkaOutputDir = layout.buildDirectory.dir("dokkaHtml")

android {
  namespace = project.findProperty("androidLibNamespace") as String
  compileSdk = (project.findProperty("androidCompileSdk") as String).toInt()
  defaultConfig { minSdk = (project.findProperty("androidMinSdk") as String).toInt() }
  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(javaSourceLevel)
    targetCompatibility = JavaVersion.toVersion(javaTargetLevel)
  }
  libraryVariants.all {
    outputs.all {
      val outputImpl = this as com.android.build.gradle.internal.api.LibraryVariantOutputImpl
      val variantName = name
      val versionName = project.version.toString()
      val newName = "${rootProject.name}-$versionName-$variantName.aar"
      outputImpl.outputFileName = newName
    }
  }
}

fun copyLicenseFiles() {
  val metaInfDir = File(layout.buildDirectory.get().asFile, "resources/main/META-INF")
  val licenseFile = File(project.rootDir, "LICENSE")
  val noticeFile = File(project.rootDir, "NOTICE.md")
  metaInfDir.mkdirs()
  licenseFile.copyTo(File(metaInfDir, "LICENSE"), overwrite = true)
  noticeFile.copyTo(File(metaInfDir, "NOTICE.md"), overwrite = true)
}

tasks.withType<AbstractArchiveTask>().configureEach { archiveBaseName.set(rootProject.name) }

tasks {
  spotless {
    kotlin {
      target("src/**/*.kt")
      licenseHeaderFile("${project.rootDir}/LICENSE_HEADER")
      ktfmt()
    }
    kotlinGradle {
      target("**/*.kts")
      ktfmt()
    }
  }
  register("generateDokkaOverview") {
    outputs.file(generatedOverviewFile)
    doLast {
      val file = generatedOverviewFile.get().asFile
      file.parentFile.mkdirs()
      file.writeText(
          buildString {
            appendLine("# Module $title")
            appendLine()
            appendLine(file("overview.md").takeIf { it.exists() }?.readText().orEmpty().trim())
            appendLine()
            appendLine("<br>")
            appendLine()
            appendLine("> ${project.findProperty("javadoc.copyright") as String}")
          })
    }
  }
  named<org.jetbrains.dokka.gradle.DokkaTask>("dokkaHtml") {
    dependsOn("generateDokkaOverview")
    outputDirectory.set(dokkaOutputDir.get().asFile)
    dokkaSourceSets.configureEach {
      noAndroidSdkLink.set(false)
      includeNonPublic.set(false)
      includes.from(files(generatedOverviewFile))
      moduleName.set(title)
    }
    doFirst { println("Generating Dokka HTML for ${project.name} version ${project.version}") }
  }
  register<Jar>("dokkaHtmlJar") {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.get().outputDirectory)
  }
}

afterEvaluate {
  tasks.withType<Jar>().configureEach {
    from(layout.buildDirectory.dir("resources/main"))
    doFirst { copyLicenseFiles() }
    manifest {
      attributes(
          mapOf("Implementation-Title" to title, "Implementation-Version" to project.version))
    }
  }
}

publishing {
  publications.withType<MavenPublication>().configureEach {
    if (name.contains("Jvm", ignoreCase = true) || name.contains("Android", ignoreCase = true)) {
      artifact(tasks["dokkaHtmlJar"])
    }
    pom {
      name.set(project.findProperty("title") as String)
      description.set(project.findProperty("description") as String)
      url.set(project.findProperty("project.url") as String)
      licenses {
        license {
          name.set(project.findProperty("license.name") as String)
          url.set(project.findProperty("license.url") as String)
          distribution.set(project.findProperty("license.distribution") as String)
        }
      }
      developers {
        developer {
          name.set(project.findProperty("developer.name") as String)
          email.set(project.findProperty("developer.email") as String)
        }
      }
      organization {
        name.set(project.findProperty("organization.name") as String)
        url.set(project.findProperty("organization.url") as String)
      }
      scm {
        connection.set(project.findProperty("scm.connection") as String)
        developerConnection.set(project.findProperty("scm.developerConnection") as String)
        url.set(project.findProperty("scm.url") as String)
      }
      ciManagement {
        system.set(project.findProperty("ci.system") as String)
        url.set(project.findProperty("ci.url") as String)
      }
      properties.set(
          mapOf(
              "project.build.sourceEncoding" to "UTF-8",
              "maven.compiler.source" to javaSourceLevel,
              "maven.compiler.target" to javaTargetLevel))
    }
  }
  repositories {
    maven {
      if (project.hasProperty("sonatypeURL")) {
        url = uri(project.property("sonatypeURL") as String)
        credentials {
          username = project.property("sonatypeUsername") as String
          password = project.property("sonatypePassword") as String
        }
      }
    }
  }
}

afterEvaluate {
  signing {
    if (project.hasProperty("releaseTag")) {
      useGpgCmd()
      publishing.publications.withType<MavenPublication>().forEach { sign(it) }
      // region Fix Gradle warning about signing tasks using publishing task outputs without
      // explicit dependencies
      // <https://youtrack.jetbrains.com/issue/KT-46466>
      tasks.withType<AbstractPublishToMaven>().configureEach {
        val signingTasks = tasks.withType<Sign>()
        mustRunAfter(signingTasks)
      }
      // endregion
    }
  }
}
