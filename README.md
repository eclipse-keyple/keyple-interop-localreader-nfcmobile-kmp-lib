# Keyple Kotlin Multiplatform NFC Reader Library

## Overview
The **Keyple Interop NFC Mobile Local Reader Library** is a Kotlin Multiplatform implementation enabling NFC card communications across
Android, iOS and desktop platforms. This library provides an abstraction layer for NFC communications, making it easier
to develop cross-platform applications.

## How it fits together
This library only handles the **local, physical NFC communication** (detecting a card, opening a channel, sending/receiving APDUs). It does not talk to a Keyple server by itself.

To run an actual Keyple transaction, it is meant to be used alongside [keyple-interop-jsonapi-client-kmp-lib](https://github.com/eclipse-keyple/keyple-interop-jsonapi-client-kmp-lib), which drives the transaction with a remote Keyple server:

- `LocalNfcReader` (this library, platform-specific `expect`/`actual` class) performs the low-level NFC operations for Android, iOS or JVM/PC-SC.
- `MultiplatformNfcReader` (this library) wraps a `LocalNfcReader` and implements the `LocalReader` SPI interface expected by `keyple-interop-jsonapi-client-kmp-lib`.
- `KeypleTerminal` (from `keyple-interop-jsonapi-client-kmp-lib`) takes that `LocalReader`, plus a `SyncNetworkClient` you implement to reach your Keyple server, and orchestrates the whole remote transaction (card selection, APDU exchanges driven by the server, etc.).

See the [class diagram](docs/uml/api_class_diagram.svg) for the full picture, and the [Quick Start](#quick-start) section below for a wiring example.

## Supported Platforms
- Android 7.0+ (API 24+)
- iOS (CoreNFC)
- JVM 11+ — ⚠️ **experimental only.** The JVM/PC-SC implementation (`src/jvmMain`) is explicitly marked `@Deprecated` as "not intended to be used in production and will be removed soon" (see [`NfcReader.kt`](src/jvmMain/kotlin/org/eclipse/keyple/interop/localreader/nfcmobile/api/NfcReader.kt#L21-L27)). Do not rely on it for production use; it exists for local/desktop testing convenience only.

## Platform Setup Requirements
Each platform's `LocalNfcReader` wraps a native NFC API, which comes with its own mandatory setup:

### Android
- Declare the NFC permission and feature in your app's `AndroidManifest.xml`:
  ```xml
  <uses-permission android:name="android.permission.NFC" />
  <uses-feature android:name="android.hardware.nfc" android:required="true" />
  ```
- `LocalNfcReader` requires a reference to the current `Activity` (it uses `NfcAdapter.enableReaderMode`/`enableForegroundDispatch`), so it must be constructed and released with an activity that is actually in the foreground.

### iOS
- Add an `NFCReaderUsageDescription` entry to your `Info.plist`, describing why the app needs NFC access.
- Enable the Core NFC capability and declare the required entitlements, typically:
  ```xml
  <key>com.apple.developer.nfc.readersession.formats</key>
  <array>
    <string>TAG</string>
  </array>
  ```
  If you plan to submit to the App Store, you will also need `com.apple.developer.nfc.readersession.iso7816.select-identifiers` listing the AID(s) your app selects.
- `LocalNfcReader` on iOS uses `CoreNFC` (`NFCTagReaderSession`), which is only available on physical devices (not on the iOS Simulator).

### JVM
- Requires a PC/SC-compatible smart card reader connected to the machine, and relies on `javax.smartcardio` (part of the JDK). See the experimental caveat above before using it beyond local testing.

## Quick Start
The exact way to instantiate `LocalNfcReader` is platform-specific (`expect`/`actual`), but wiring it into a Keyple transaction always follows the same pattern:

```kotlin
// 1. Create the platform-specific NFC reader
// Android: LocalNfcReader(activity)
// iOS:     LocalNfcReader(getErrorMsg = { e -> e.message ?: "NFC error" })
// JVM:     LocalNfcReader() // experimental, see caveat above
val nfcReader = LocalNfcReader(/* platform-specific parameter(s) */)

// 2. Wrap it so it satisfies the `LocalReader` SPI expected by keyple-interop-jsonapi-client-kmp-lib
val localReader: LocalReader = MultiplatformNfcReader(nfcReader)

// 3. Provide your own SyncNetworkClient implementation, reaching your Keyple server
val networkClient: SyncNetworkClient = MyHttpNetworkClient(/* ... */)

// 4. Create the KeypleTerminal (from keyple-interop-jsonapi-client-kmp-lib) and run a transaction
val terminal = KeypleTerminal(reader = localReader, clientId = "my-client-id", networkClient = networkClient)

terminal.waitForCard()
val result = terminal.executeRemoteService(serviceId = "MyService", inputData = null)
terminal.release()
```

For a full, runnable example including a `SyncNetworkClient` implementation over HTTP, see [SimpleHttpNetworkClient](https://github.com/calypsonet/keyple-demo-ticketing-reloading-remote/blob/main/client/kmp/composeApp/src/commonMain/kotlin/org/calypsonet/keyple/demo/reload/remote/network/SimpleHttpNetworkClient.kt).

## Documentation & Contribution Guide
Full documentation available at [keyple.org](https://keyple.org)

## Build
The code is built with **Gradle** and targets **Android**, **iOS**, and **JVM** platforms.
This library depends on [keyple-interop-jsonapi-client-kmp-lib](https://github.com/eclipse-keyple/keyple-interop-jsonapi-client-kmp-lib).
Ensure it's available through public maven repos, or by publishing it yourself locally prior to this library. 
To build and publish the artifacts for all supported targets locally, use:
```
./gradlew publishToMavenLocal
```
Note: you need to use a mac to build or use iOS artifacts. Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).

## API Documentation
API documentation & class diagrams are available
at [docs.keyple.org/keyple-interop-localreader-nfcmobile-kmp-lib](https://docs.keyple.org/keyple-interop-localreader-nfcmobile-kmp-lib/)
