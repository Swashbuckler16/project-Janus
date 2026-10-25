# Project Janus 🏛️
> **Dual-Faced Mobile Authentication Security & PIN Enumeration Research Testbed**

[![CI/CD](https://github.com/Swashbuckler16/project-Janus/actions/workflows/ci.yml/badge.svg)](https://github.com/Swashbuckler16/project-Janus/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-Modular%20Multi--Module-orange.svg)](#multi-module-gradle-architecture)
[![Threat Model](https://img.shields.io/badge/Security-Threat%20Model%20%26%20Findings-red.svg)](THREAT_MODEL.md)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%7C%20Python%203-blue.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)](https://developer.android.com/jetpack/compose)

---

## Overview

Named after **Janus**—the Roman god of doors and gates depicted with two faces looking in opposite directions—**Project Janus** is an instrumentation and benchmarking framework designed to evaluate both sides of mobile authentication security:
1. **The Attack Face (Offensive Automation)**: Automated PIN enumeration and brute-force stress testing across local network interfaces.
2. **The Defense Face (Countermeasure Benchmarking)**: Real-time telemetry, response-time profiling, and rate-limiting enforcement within an Android application.

Project Janus simulates a high-security mobile vault app with an embedded research verification daemon, paired with an external Python test harness for empirical measurement.

---

## Multi-Module Gradle Architecture

```mermaid
graph TD
    App[":app<br/>(UI, Jetpack Compose, Activities, DI)"]
    CoreNet[":core:network<br/>(Embedded NanoHTTPD Daemon :8080)"]
    CoreDef[":core:defense<br/>(TokenBucket, LockoutManager, DefenseTiers)"]
    CoreSec[":core:security<br/>(Keystore AES-256-GCM, PBKDF2, PinVerifier)"]

    App --> CoreNet
    App --> CoreDef
    App --> CoreSec

    CoreNet --> CoreDef
    CoreNet --> CoreSec
```

---

## Repository Structure

```
├── app/                                 # Android application presentation & orchestration
│   ├── src/main/java/com/atharva/securevault/
│   │   ├── MainActivity.kt             # Jetpack Compose UI routing & Lab controls
│   │   ├── VaultHomeScreen.kt          # Simulated protected vault records
│   │   ├── CompromiseReportScreen.kt   # Real-time compromise alert screen
│   │   └── authentication/
│   │       └── ResearchAuthenticator.kt# Lab gatekeeper (Default PIN: 2468)
│   └── build.gradle.kts
│
├── core/
│   ├── security/                       # :core:security module
│   │   ├── src/main/java/com/atharva/securevault/
│   │   │   ├── security/
│   │   │   │   ├── crypto/
│   │   │   │   │   ├── CryptoService.kt    # PBKDF2 (100k rounds), SHA-256, Constant-Time comparison
│   │   │   │   │   └── HashAlgorithm.kt    # Algorithm enum (PBKDF2_HMAC_SHA256 vs LEGACY_SHA256)
│   │   │   │   ├── domain/
│   │   │   │   │   └── VerificationResult.kt # High-resolution execution & security telemetry
│   │   │   │   ├── keystore/
│   │   │   │   │   └── KeyStoreManager.kt  # Android Keystore hardware-backed AES-256-GCM
│   │   │   │   ├── storage/
│   │   │   │   │   └── SecurePinStore.kt   # Encrypted credential persistence at rest
│   │   │   │   ├── LabPinStore.kt          # Lab configuration bridge
│   │   │   │   └── PinVerifier.kt          # Dual-mode cryptographic verifier
│   │   │   └── authentication/
│   │   │       └── PinAuthenticator.kt     # Verification bridge & telemetry
│   │   ├── src/test/java/                  # Security Unit Tests
│   │   │   └── com/atharva/securevault/security/
│   │   │       ├── CryptoServiceTest.kt    # Determinism, salt uniqueness, and standard vectors
│   │   │       └── TimingResistanceTest.kt # Side-channel timing resistance assertions
│   │   └── build.gradle.kts
│   │
│   ├── defense/                        # :core:defense module
│   │   ├── src/main/java/com/atharva/securevault/
│   │   │   ├── defense/
│   │   │   │   ├── DefenseManager.kt       # Persistent lockout orchestrator & tamper-resistant store
│   │   │   │   ├── DefenseState.kt         # Real-time defensive telemetry snapshot
│   │   │   │   ├── LockoutTier.kt          # Multi-tier levels (Normal, Throttled, Lockout, Critical)
│   │   │   │   └── TokenBucket.kt          # High-precision traffic shaping algorithm
│   │   │   └── DefenseMode.kt              # Countermeasure policies (NONE, SLIDING_WINDOW, ENTERPRISE)
│   │   ├── src/test/java/                  # Defense Unit Tests
│   │   │   └── com/atharva/securevault/defense/
│   │   │       ├── TokenBucketTest.kt      # Traffic shaping & depletion tests
│   │   │       └── TokenBucketConcurrencyTest.kt # Concurrent thread-safety & race condition tests
│   │   └── build.gradle.kts
│   │
│   └── network/                        # :core:network module
│       ├── src/main/java/com/atharva/securevault/network/
│       │   └── LabServer.kt            # Embedded NanoHTTPD daemon with HTTP 429 enforcement
│       └── build.gradle.kts
│
├── Python attack controller/
│   └── Attack-Controller/
│       ├── main.py                     # Attack runner with HTTP 429 defense handling & cooldowns
│       ├── analyze_results.py          # Visualization & statistical benchmark analysis suite
│       ├── pin_generator.py            # Zero-padded 4-digit PIN candidate generator
│       ├── test_pin_generator.py       # Unit tests for attack harness
│       ├── logger.py                   # CSV export & experiment logger
│       └── Results/                    # Benchmark datasets & generated publication charts
│           ├── baseline_sha256_unprotected.csv
│           ├── defended_tiered_lockout.csv
│           ├── exhaustive_bruteforce_run.csv
│           └── plots/                  # Visual publication plots (.png)
│
├── .github/workflows/
│   └── ci.yml                          # Automated multi-module build, test & packaging CI pipeline
│
├── THREAT_MODEL.md                     # Formal threat model, assumptions & quantitative findings
├── LICENSE                             # MIT Open Source License
└── README.md
```

---

## Features

* **Hardware-Backed Keystore (TEE / StrongBox)**: Master keys generated directly inside Android's hardware security module with AES-256-GCM, encrypting verification records and salts at rest.
* **Dual Key Derivation Architecture**:
  * **Hardened Mode**: PBKDF2 with HMAC-SHA256, cryptographically random 128-bit salt, 100,000 iterations, and constant-time byte comparisons (`MessageDigest.isEqual`).
  * **Vulnerable Baseline Mode**: Raw unsalted SHA-256 with intentional early-exit comparisons to benchmark rainbow table exposure and timing side-channels.
* **Token-Bucket Rate Limiting**: Regulates incoming request burst traffic (5 token burst capacity, 0.5 tokens/sec continuous refill) to prevent socket exhaustion and rapid hammering.
* **Multi-Tier Progressive Lockout Engine**:
  * **Tier 0 (0–3 failed attempts)**: Normal operation, zero latency penalty.
  * **Tier 1 (4–5 failed attempts)**: Progressive artificial delay injection (+1500ms delay) to disrupt high-frequency automated scripts without locking legitimate users.
  * **Tier 2 (6–9 failed attempts)**: Hard temporary lockout with exponential backoff (30s $\rightarrow$ 60s $\rightarrow$ 120s $\rightarrow$ 300s). Inbound requests rejected with HTTP 429 (`Retry-After`).
  * **Tier 3 (10+ failed attempts)**: Critical system lockdown requiring security lab reset.
* **Tamper-Resistant Persistence**: Failed attempt counters and lockout timestamps persist across app restarts and process kills, thwarting reboot bypass techniques.
* **Embedded Verification Daemon**: NanoHTTPD server runs directly in the Android process on port `8080`, allowing isolated authentication calls without cloud dependencies.
* **Reactive Compromise Detection**: Jetpack Compose observes Kotlin Coroutine `StateFlow` streams. The moment the correct PIN is received via the API, the app transitions in real-time to a `CompromiseReportScreen` with telemetry metrics.
* **Nanosecond Telemetry Logging**: Measures internal cryptographic/verification latency (`responseTimeMs`, `executionTimeNanos`) separately from network latency and records timestamped CSV datasets in `Results/`.

---

## Quickstart

### 1. Build and Run the Android App

1. Open the project in **Android Studio**.
2. Connect an Android device (or launch an emulator) on the same local Wi-Fi network.
3. Build and install:
   ```bash
   ./gradlew installDebug
   ```
4. On the lock screen, tap **SECURITY LAB**.
5. Enter the researcher access PIN: `2468`.
6. Set your **Target PIN** (e.g. `0042`) and save. Note down your device's local IP address.

### 2. Run the Attack Controller

Ensure Python 3 is installed.

```bash
cd "Python attack controller/Attack-Controller"

# Run a test against target device
python main.py --ip <DEVICE_IP> --port 8080 --start 0 --end 100 --delay 0.05
```

#### CLI Parameters:
* `--ip`: IP address of the target Android device (Default: `192.168.1.45`)
* `--port`: Port running `LabServer` (Default: `8080`)
* `--start`: Starting numeric PIN candidate (Default: `0`)
* `--end`: Ending numeric PIN candidate (Default: `9`)
* `--delay`: Throttle interval between consecutive requests in seconds (Default: `0.0`)
* `--max-attempts`: Safety threshold to halt testing (Default: `100`)

### 3. Generate Benchmark Analytics & Visualizations

Transform raw CSV attack telemetry into publication-grade analytical plots:

```bash
cd "Python attack controller/Attack-Controller"
python analyze_results.py
```

Generated plots in `Results/plots/`:
* `latency_comparison.png`: Device cryptographic execution vs network roundtrip latency.
* `keyspace_exhaustion.png`: Log-scale projected duration to exhaust 10,000 PIN space across defense tiers.
* `progressive_lockout_steep.png`: Step staircase visualizing consecutive failed attempt escalation and HTTP 429 cooldowns.

---

## Lab Credential Notice: Research Gatekeeper

> [!NOTE]
> **Researcher Access PIN (`2468`):**
> The default PIN `2468` in `ResearchAuthenticator.kt` is a **deliberate, documented lab test-bench credential** intended strictly to automate test device calibration and developer metrics unlocking during synthetic attacks (see `test_pin_generator.py` and `main.py`).
>
> Application credential storage and hardware-backed user verification are decoupled and implemented in `:core:security` (`SecurePinStore` and `PinVerifier`).

---

## Security Case Study: The Migration Shim Trap

During an architectural security audit of `SecurePinStore.kt`, a subtle but critical real-world vulnerability was identified and resolved:

* **The Trap**: To maintain backward compatibility with early prototype releases, the store originally contained an unconditional write to unencrypted SharedPreferences:
  ```kotlin
  legacyPrefs.edit()
      .putString(LEGACY_VERIFIER_KEY, cryptoService.toHex(cryptoService.hashSha256(pin)))
      .apply()
  ```
* **The Impact**: Even when an administrator selected `PBKDF2_HMAC_SHA256` with hardware-backed AES-256-GCM Keystore encryption, an unsalted single-iteration SHA-256 hash was still written to plaintext XML storage (`secure_vault_lab.xml`). An attacker with ADB backup or local read access could crack the unsalted hash in less than 2 milliseconds, completely nullifying the Keystore hardware boundary.
* **The Fix**: Implemented an explicit **Zero-Persistence Legacy Policy**: saving with `PBKDF2_HMAC_SHA256` actively purges `LEGACY_VERIFIER_KEY` from legacy storage (`legacyPrefs.edit().remove(...).apply()`), preventing migration shims from silently undermining modern defense layers.

Full threat modeling, assumptions, and quantitative results are documented in [THREAT_MODEL.md](THREAT_MODEL.md).

---

## Research Disclaimer

This project is created strictly for **academic and security research purposes** to demonstrate the importance of multi-factor authentication, account lockouts, rate limiting, and secure hardware keystores (e.g., Android Keystore / StrongBox). Do not use this software against any target without explicit authorization.

---

## License

This project is licensed under the [MIT License](LICENSE).
