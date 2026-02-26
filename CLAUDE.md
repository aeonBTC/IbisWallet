# IbisWallet — Claude Development Guide

A self-custody Bitcoin wallet for Android built with Kotlin, Jetpack Compose, and the Bitcoin Development Kit (BDK).

---

## Purpose Of This File

This file is just a notebook to help guide Claude Code, if you use it.   You can modify this file in any way you like to help guide Claude during coding sessions.


## Toolchain Standards

### JDK — Eclipse Temurin 21 (LTS)

This project standardizes on **Eclipse Temurin 21 (LTS)**. Gradle 8.x supports a maximum of JDK 21; newer JDKs (22+) will cause daemon startup failures.

**Required installation path (macOS):**
```
/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

Install via Homebrew:
```bash
brew install --cask temurin@21
```

The JDK is pinned in two places so both the CLI and Android Studio use it consistently:

- **`gradle.properties`** — `org.gradle.java.home=...` (forces the Gradle daemon)
- **`~/.zshrc`** — `export JAVA_HOME=...` (CLI tools and shell commands)

Never change `compileOptions` or `kotlinOptions.jvmTarget` to a value other than `21` without also updating both locations above.

### Gradle — Version 8

The wrapper is pinned to **Gradle 8.13**. Do not upgrade beyond 8.x without verifying JDK compatibility.

```
gradle/wrapper/gradle-wrapper.properties → distributionUrl=.../gradle-8.13-bin.zip
```

**Key Gradle rules:**
- Always use the wrapper (`./gradlew`), never a system-installed Gradle.
- Use the **Kotlin DSL** (`build.gradle.kts`, `settings.gradle.kts`) — not Groovy.
- All dependency versions live in `gradle/libs.versions.toml` (Version Catalog). Add new dependencies there first, then reference via `libs.*` aliases.
- Do not use `implementation("group:artifact:version")` string literals directly — always go through the catalog.

---

## Project Structure

```
app/
  src/
    main/java/github/aeonbtc/ibiswallet/
      data/local/        # Room/SQLite caches (ElectrumCache)
      tor/               # CachingElectrumProxy — 3-socket Electrum bridge
      ui/                # Jetpack Compose screens and ViewModels
      util/              # Pure Kotlin utilities (ElectrumSeedUtil, QrFormatParser, UrAccountParser)
    test/java/           # JVM unit tests (Kotest + MockK)
gradle/
  libs.versions.toml     # Single source of truth for all dependency versions
  wrapper/               # Gradle wrapper — always use ./gradlew
```

---

## Build Commands

```bash
# Compile and run all unit tests
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "github.aeonbtc.ibiswallet.tor.CachingElectrumProxyTest"

# Generate JaCoCo HTML coverage report
./gradlew jacocoUnitTestReport
# Report: app/build/reports/jacoco/jacocoUnitTestReport/html/index.html

# Build debug APK
./gradlew assembleDebug

# Clean build
./gradlew clean assembleDebug
```

---

## Testing Framework

### Stack

| Library | Version | Purpose |
|---|---|---|
| **Kotest** | 5.9.1 | Test framework — use `FunSpec` style |
| **MockK** | 1.13.13 | Kotlin-native mocking |
| **kotlinx-coroutines-test** | 1.9.0 | Coroutine test utilities |
| **org.json:json** | 20231013 | Real JSON for unit tests (Android stubs throw) |

### Unit Test Rules

**Test style — always use `FunSpec`:**
```kotlin
class MyTest : FunSpec({
    context("feature group") {
        test("specific behavior") {
            // arrange / act / assert
        }
    }
})
```

**JUnit Platform** is required for Kotest. This is already configured in `app/build.gradle.kts`:
```kotlin
testOptions {
    unitTests.all { it.useJUnitPlatform() }
}
```

**`android.testOptions.unitTests.returnDefaultValues=true`** is set in `gradle.properties`. This suppresses most Android framework stubs from throwing, but it does NOT cover:
- `android.util.Log` — always throws even with `returnDefaultValues`. Must be mocked explicitly with `mockkStatic`.
- `org.json.JSONObject` / `JSONArray` methods — always throw. Solved by adding `testImplementation("org.json:json:20231013")` so the real implementation is on the test classpath.

**Standard `beforeSpec` block for any test class that touches Android framework code:**
```kotlin
beforeSpec {
    mockkStatic(android.util.Log::class)
    every { android.util.Log.d(any(), any<String>()) } returns 0
    every { android.util.Log.w(any(), any<String>()) } returns 0
    every { android.util.Log.e(any(), any<String>()) } returns 0
    every { android.util.Log.e(any(), any<String>(), any()) } returns 0
}

afterEach {
    clearAllMocks(answers = false) // keep stubs, clear recorded calls
}
```

**Test file location:**
```
app/src/test/java/<package-mirroring-main>/
```
For example, tests for `github.aeonbtc.ibiswallet.tor.CachingElectrumProxy` live at:
```
app/src/test/java/github/aeonbtc/ibiswallet/tor/CachingElectrumProxyTest.kt
```

### What to Test

Prefer testing **pure logic** — methods that take inputs and return outputs without side effects. For classes that do I/O (sockets, databases), test via:
1. **Mocked dependencies** — inject a `mockk<ElectrumCache>()` instead of a real SQLite database.
2. **Loopback sockets** — spin up a real `ServerSocket(0)` on localhost for testing TCP protocol logic (see `CachingElectrumProxyTest`).

Avoid testing Android UI, ViewModels with `LiveData`, and anything requiring an emulator in unit tests — those belong in instrumented tests (`androidTest/`).

### Coverage

Run `./gradlew jacocoUnitTestReport` to generate a coverage report. The project targets meaningful coverage of business logic classes:
- `ElectrumSeedUtil` — ~94% line coverage
- `UrAccountParser` — ~64% line coverage
- `CachingElectrumProxy` — ~47% line coverage

---

## Kotlin Best Practices

### General

- **Prefer `val` over `var`** everywhere. Use `var` only when mutation is genuinely required.
- **Prefer data classes** for value types — they get `equals`, `hashCode`, `copy`, and `toString` for free.
- **Prefer sealed classes/interfaces** for domain-modeled state and results over nullable returns or exception-based control flow.
- **Avoid `!!` (non-null assertion)**. Use `?.let { }`, `?: return`, or `requireNotNull()` with a message instead.
- **Use `@Volatile`** for fields read/written across threads without a lock. Use `ReentrantLock` or `@Synchronized` for compound operations.
- **Scope coroutines to lifecycle owners** — never launch `GlobalScope` coroutines. Use `viewModelScope`, `lifecycleScope`, or an explicitly managed `CoroutineScope(SupervisorJob())`.
- **Use `SupervisorJob()`** in shared coroutine scopes so a failure in one child doesn't cancel siblings.

### Coroutines

- Use `Dispatchers.IO` for blocking I/O (sockets, file, database). Use `Dispatchers.Default` for CPU-intensive work. Never block `Dispatchers.Main`.
- Use `withContext(Dispatchers.IO) { }` to switch context within a coroutine rather than launching new coroutines unnecessarily.
- Prefer `SharedFlow` over `LiveData` for reactive streams in non-UI layers. Use `StateFlow` for observable state.
- When using `MutableSharedFlow`, set `replay = 1` if late subscribers need the last value. The default `replay = 0` with `extraBufferCapacity` only buffers for *existing* slow subscribers — values emitted before subscription are lost.
- Use `tryEmit()` for fire-and-forget emissions from non-suspending contexts. Use `emit()` from coroutines to apply backpressure.

### Android-specific

- **Never call `Log.*` in production code paths that execute frequently** (e.g., per-frame or per-packet). Gate all logging behind `if (BuildConfig.DEBUG)`.
- **Use `@SuppressLint` sparingly** — only when the lint warning is a confirmed false positive, and always leave a comment explaining why.
- **Prefer `EncryptedSharedPreferences`** (via `androidx.security.crypto`) for any sensitive data stored on-device.
- **Biometric authentication** — always handle `BiometricPrompt` callbacks on the main thread.

### Jetpack Compose

- **Hoist state** out of composables. Composables should be stateless where possible and receive state + callbacks as parameters.
- **Use `remember { }` and `derivedStateOf { }`** to avoid unnecessary recompositions.
- **Avoid side effects in composable bodies** — use `LaunchedEffect`, `SideEffect`, or `DisposableEffect` for lifecycle-aware side effects.
- **Preview with `@Preview`** for all non-trivial composables. Pass fake/stub data; never inject ViewModels into previews.
- **Navigation** — use `NavController` + `NavHost` with type-safe routes. Keep navigation logic out of ViewModels; expose `UiEvent` channels instead.

---

## Architecture

This project follows a layered architecture:

```
UI Layer        → Composables + ViewModels (ui/)
Domain Layer    → WalletRepository, pure business logic (repository, util/)
Data Layer      → ElectrumCache (SQLite), EncryptedSharedPreferences, CachingElectrumProxy
Network Layer   → CachingElectrumProxy (TCP/SSL/Tor), BDK ElectrumClient
```

**Key rules:**
- ViewModels expose `StateFlow<UiState>` and handle user events via `fun onEvent(event: UiEvent)`.
- The repository layer is the single source of truth — ViewModels do not hold business logic.
- Network code (sockets, BDK calls) always runs on `Dispatchers.IO`. Results are surfaced via `SharedFlow` or `StateFlow` to the ViewModel.
- Avoid God classes — if a class exceeds ~400 lines, consider splitting by responsibility.

---

## Security Considerations

This is a **self-custody Bitcoin wallet**. Security mistakes can result in permanent loss of funds.

- **Never log private keys, seed phrases, or xprv strings** — not even behind `BuildConfig.DEBUG`.
- **Wipe sensitive byte arrays** from memory after use (overwrite with zeros). Avoid converting seeds to `String` — strings are interned and GC-nondeterministic.
- **Tor proxy** is supported for privacy. When `useTorProxy = true`, always resolve hostnames through the SOCKS5 proxy using `InetSocketAddress.createUnresolved()` — never pre-resolve DNS on the device.
- **TOFU (Trust On First Use)** for SSL — `TofuTrustManager` pins the server certificate on first connection and rejects changes thereafter.
- **BIP39 vs Electrum seeds** are fundamentally different. Electrum seeds use a custom HMAC-based versioning scheme. Never treat them interchangeably. See `ElectrumSeedUtil` for the distinction.
- **ProGuard is enabled for release builds** — verify that BDK native library classes and any reflection-based code are properly kept in `proguard-rules.pro`.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Gradle daemon uses wrong JDK | Set `org.gradle.java.home` in `gradle.properties` |
| JDK > 21 breaks Gradle 8 | Gradle 8.x supports max JDK 21. Use Temurin 21. |
| `android.util.Log` throws in unit tests | `mockkStatic(android.util.Log::class)` in `beforeSpec` |
| `org.json.JSONObject` throws in unit tests | Add `testImplementation("org.json:json:20231013")` |
| `SharedFlow` emissions lost before subscription | Start collectors before emitting, or use `replay = 1` |
| `startNotificationListener` loop exits immediately | Ensure `isRunning = true` via `start()` before calling subscription methods |
| BDK native library crashes on x86 emulator | Use ARM emulator or physical device (`abiFilters` restricts to `armeabi-v7a`, `arm64-v8a`) |
| Verbose `blockchain.transaction.get` bypasses cache | Cache only intercepts non-verbose (BDK default). Verbose queries go directly to server. |
