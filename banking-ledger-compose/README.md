This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

### Local setup

- JDK: 21 or newer.
- Gradle: wrapper-managed `9.1.0`.
- Kotlin: `2.3.21`.
- Android Gradle Plugin: `9.0.1`.
- Compose Multiplatform: `1.11.0`.
- Android SDK: compile/target SDK `36`, minimum SDK `24`.
- iOS: open `iosApp/iosApp.xcodeproj` in Xcode and build the `iosApp` scheme.

### Backend configuration

The Compose client should read the Banking Ledger API base URL from platform configuration when API integration starts:

- Android: `local.properties`, BuildConfig, or a debug-only manifest placeholder.
- iOS: `iosApp/Configuration/Config.xcconfig`.
- Desktop: a Gradle run argument, environment variable, or local properties file.

Do not commit developer-specific base URLs or credentials. Tokens must use platform secure storage, while non-sensitive demo preferences can use DataStore.

### Verified foundation dependencies

The foundation build uses the ADR stack in `gradle/libs.versions.toml`: Compose Material 3/resources, Ktor Client with Kotlinx Serialization, coroutines, Koin, AndroidX/KMP lifecycle ViewModel/runtime Compose, AndroidX DataStore, JetBrains-published Navigation 3 UI for multiplatform targets, and common test libraries.

Dependency target support was verified with:

```shell
./gradlew :shared:compileKotlinMetadata :shared:jvmTest :desktopApp:compileKotlin :androidApp:assembleDebug
```

The iOS framework task is:

```shell
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

It requires working Xcode command line tools; `xcrun -f ld` must succeed on the host.
