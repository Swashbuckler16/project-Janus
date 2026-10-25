# Project Janus: Threat Model & Empirical Security Findings

> **Abstract:** This document formalizes the threat model, security assumptions, empirical methodology, quantified benchmarks, and architectural case studies evaluated within Project Janus — an adversarial testbed and hardened defense engine for Android authentication systems.

---

## 1. System Architecture & Trust Boundaries

```
┌────────────────────────────────────────────────────────────────────────┐
│                        UNTRUSTED ADVERSARY SPACE                       │
│  - Automated Attack Controller (Python Harness over ADB/Wi-Fi)         │
│  - Malicious Local Apps / Compromised IPC                              │
│  - Physical Attacker with intermittent device access                   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         HTTP / REST API Request
                         [/lab/verify { "pin": "..." }]
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        TRUST BOUNDARY: APP PROCESS                     │
│                                                                        │
│  ┌───────────────────────┐             ┌────────────────────────────┐  │
│  │     :core:network     │             │       :core:defense        │  │
│  │   Embedded NanoHTTPD  ├────────────►│  • Token Bucket Engine     │  │
│  │      Test Harness     │◄────────────┤  • Multi-Tier Lockout Store│  │
│  └───────────────────────┘  HTTP 429   │  • Timing-Invariant Gate   │  │
│                                        └─────────────┬──────────────┘  │
│                                                      │                 │
│                                                      ▼                 │
│                                        ┌────────────────────────────┐  │
│                                        │       :core:security       │  │
│                                        │  • ConstantTimeVerifier    │  │
│                                        │  • CryptoService (PBKDF2)  │  │
│                                        │  • SecurePinStore          │  │
│                                        └─────────────┬──────────────┘  │
└──────────────────────────────────────────────────────┼─────────────────┘
                                                       │
                                          Hardware Key │ Encryption
                                          (AES-256-GCM)│
                                                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        HARDWARE TRUST BOUNDARY                         │
│  Android Keystore System (TEE / StrongBox Keymaster)                   │
│  Non-exportable master key (master_credential_key)                     │
└────────────────────────────────────────────────────────────────────────┘
```

### Trust Boundary Definitions
1. **Hardware Enclave (TEE / StrongBox Keymaster):**
   - High trust. Stores the non-exportable AES-256-GCM master key. Plaintext key material cannot be extracted even under kernel compromise.
2. **Application Process Sandbox (`:app`, `:core:defense`, `:core:security`):**
   - Medium trust. Executes rate limiting, cryptographic derivation (PBKDF2-HMAC-SHA256), and constant-time memory comparisons. Protected by Linux UID sandboxing.
3. **Local Test Harness / Network Surface (`:core:network`):**
   - Low trust. Embedded HTTP server listening on the local loopback/Wi-Fi interface (`:8080`) to enable controlled black-box adversarial probing.
4. **Adversary Space:**
   - Zero trust. Simulates a remote brute-force botnet or local automated attacker cycling candidate PINs at maximum network throughput.

---

## 2. Adversary Model & Attack Vectors

| Attack Vector | Adversary Capability | Target Component | Defense Mechanism in Project Janus |
| :--- | :--- | :--- | :--- |
| **High-Throughput Online Brute Force** | High request rate over local network or IPC | `/lab/verify` endpoint | Token-Bucket Traffic Shaper + Multi-Tier Exponential Lockout |
| **Offline Dictionary & Rainbow Tables** | Backup extraction or disk acquisition | Credential Store (`/data/data/...`) | Hardware-backed AES-256-GCM Keystore encryption + 100k iteration PBKDF2 with unique cryptographic salt |
| **Timing Side-Channel Analysis** | High-precision network or local clock probes | `PinVerifier` verification loop | Bitwise constant-time verification (`MessageDigest.isEqual`) with uniform synthetic pad |
| **Credential Storage Downgrade** | Exploiting migration shims or legacy prefs | Unencrypted SharedPreferences | Active purge policy on upgrade; no unsalted hash fallback |
| **Reboot & Tamper Bypass** | Power-cycling device to reset attempt counters | Rate Limiter memory state | Atomic disk-persisted `LockoutStateStore` with monotonic clock skew detection |

---

## 3. Deliberate Lab-Only Credentials Notice

> [!IMPORTANT]
> **Lab-Only Bench Credential (`ResearchAuthenticator.kt`):**
> The hardcoded PIN `"2468"` inside `ResearchAuthenticator.kt` is a **deliberate, documented lab credential** specifically dedicated to automated attack harness calibration and developer bench testing (see `test_pin_generator.py` and `main.py`).
>
> In production security architecture, user authentication is managed exclusively through hardware-backed credentials persisted via `SecurePinStore` and verified using `PinVerifier` with biometric/PIN binding.

---

## 4. Empirical Security Findings & Quantitative Benchmarks

All metrics below were collected across **19 empirical attack trials** using our dedicated Python Attack Controller running against a physical Android target device over a local Wi-Fi test harness.

### 4.1 Cryptographic Latency & Cost Multiplier

| Algorithm Configuration | Iteration Count | Salt | Mean Verification Time (Device) | Mean End-to-End Latency | Computational Cost Multiplier |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Legacy Prototype (SHA-256)** | 1 (Single Pass) | None (Unsalted) | **< 0.1 ms** | ~52.4 ms | **1.0x** (Baseline) |
| **Hardened Janus (PBKDF2-HMAC-SHA256)** | 100,000 | 128-bit Cryptographic | **440.25 ms** | ~461.62 ms | **> 4,400x Slower** |

![Cryptographic Latency Comparison](Python%20attack%20controller/Attack-Controller/Results/plots/latency_comparison.png)

### 4.2 Four-Digit PIN Space (10,000 Combinations) Exhaustion Projections

| Defense Tier Configuration | Feasible Request Rate | Total Time to Exhaust 10,000 PIN Space | Practical Risk Level |
| :--- | :--- | :--- | :--- |
| **Offline Unprotected SHA-256** (Extracted Database) | > 10,000,000 hashes/sec | **< 2 milliseconds** | **CRITICAL** (Instant Compromise) |
| **Online SHA-256 (No Defense)** | ~20 requests/sec | **8.3 minutes** | **HIGH** (Trivial Network Attack) |
| **Online Hardened PBKDF2 (No Rate Limit)** | ~2.2 requests/sec | **1.22 hours** | **MEDIUM** (Feasible Overnight) |
| **Sliding Window Rate Limiter** (10 req/min) | 0.16 requests/sec | **16.7 hours** | **LOW** (Noticeable Delay) |
| **Project Janus Enterprise Multi-Tier Engine** | **0 req/sec after Tier 4** | **INFINITE** (Locked out after 10 failed attempts) | **NEGLIGIBLE** (Hardware Enforced) |

![Keyspace Exhaustion Projections](Python%20attack%20controller/Attack-Controller/Results/plots/keyspace_exhaustion.png)

### 4.3 Multi-Tier Defense Escalation Engine

When an adversary initiates a brute-force attack, the defense engine progresses through four deterministic mitigation tiers:

```
Attempts 1-3   ──► NORMAL: Standard PBKDF2 processing (~440ms)
Attempts 4-5   ──► THROTTLED: Dynamic +500ms jitter penalty enforced
Attempts 6-7   ──► TEMPORARY LOCKOUT: HTTP 429 with 30s Cooldown Header
Attempts 8-9   ──► EXTENDED LOCKOUT: HTTP 429 with 300s (5-Minute) Cooldown
Attempt 10+    ──► PERMANENT LOCKOUT: HTTP 429 with 86,400s (24h/Wipe Required)
```

![Multi-Tier Escalation](Python%20attack%20controller/Attack-Controller/Results/plots/progressive_lockout_steep.png)

---

## 5. Security Case Study: The Backward-Compatibility Migration Trap

During architectural review of the migration shim in `SecurePinStore.kt`, an insidious real-world vulnerability was uncovered and eradicated:

### The Vulnerability
To maintain backward compatibility with legacy prototype releases, `SecurePinStore.savePin()` initially contained:
```kotlin
// VULNERABLE CODE:
legacyPrefs.edit()
    .putString(LEGACY_VERIFIER_KEY, cryptoService.toHex(cryptoService.hashSha256(pin)))
    .apply()
```
Even when the user selected `PBKDF2_HMAC_SHA256` with hardware Keystore encryption, this shim executed unconditionally — quietly writing an **unsalted, single-iteration SHA-256 hash** into standard unencrypted `SharedPreferences` (`secure_vault_lab.xml`).

An attacker with read access (via ADB backup, physical storage dump, or root inspection) could bypass the hardware Keystore entirely by cracking the unsalted SHA-256 hash in less than 2 milliseconds.

### The Remediation
The migration logic was redesigned to adhere to a **Zero-Persistence Legacy Policy**:
1. When configuring `PBKDF2_HMAC_SHA256`, the store explicitly calls:
   ```kotlin
   legacyPrefs.edit().remove(LEGACY_VERIFIER_KEY).apply()
   ```
2. Unsalted hashes are only ever persisted if the user explicitly switches the testbed into legacy research simulation mode.
3. Upgrading automatically purges all legacy unencrypted preferences from disk.

---

## 6. Limitations & Future Work

1. **Argon2id Memory-Hard KDF:** While PBKDF2-HMAC-SHA256 provides robust protection against CPU attacks, memory-hard functions like Argon2id provide superior resistance against FPGA and ASIC adversaries. Future phases will integrate Argon2id via Android NDK C++.
2. **Biometric Crypto-Object Binding:** Binding biometric authentication directly to the `Cipher` object in Keystore so cryptographic keys can only be unlocked upon valid hardware biometric prompt.
3. **Distributed IP Attack Shaping:** The current token bucket tracks client IP. In production environments, client reputation scores and global device attestation (Play Integrity API) should augment IP-based rate limiting.
