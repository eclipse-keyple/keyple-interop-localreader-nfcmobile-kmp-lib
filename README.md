# Keyple Kotlin Multiplatform NFC Reader Library

## Overview
The **Keyple Interop NFC Mobile Local Reader Library** is a Kotlin Multiplatform implementation enabling NFC card communications across
Android, iOS and desktop platforms. This library provides an abstraction layer for NFC communications, making it easier
to develop cross-platform applications.

## Documentation & Contribution Guide
Full documentation available at [keyple.org](https://keyple.org)

## Supported Platforms
- Android 7.0+ (API 24+)
- iOS (CoreNFC)
- JVM 17+

## Build
The code is built with **Gradle** and targets **Android**, **iOS**, and **JVM** platforms.
This library depends on [keyple-interop-jsonapi-client-kmp-lib](https://github.com/eclipse-keyple/keyple-interop-jsonapi-client-kmp-lib).
Ensure it's available through public maven repos, or by publishing it yourself locally prior to this library. 
To build and publish the artifacts for all supported targets locally, use:
```
./gradlew publishToMavenLocal
```
Note: you need to use a mac to build or use iOS artifacts. Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

## API Documentation
API documentation & class diagrams are available
at [docs.keyple.org/keyple-interop-localreader-nfcmobile-kmp-lib](https://docs.keyple.org/keyple-interop-localreader-nfcmobile-kmp-lib/)