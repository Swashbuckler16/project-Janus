package com.atharva.securevault.defense

/**
 * Multi-tier progressive defense levels enforcing increasing defensive penalties.
 */
enum class LockoutTier(
    val displayName: String,
    val description: String
) {
    /**
     * 0–3 failed attempts: Normal operation with no artificial delay.
     */
    NORMAL(
        "Tier 0: Normal",
        "Normal operation. Zero penalty delay."
    ),

    /**
     * 4–5 failed attempts: Progressive artificial latency injection (1.5s delay)
     * to disrupt automated high-frequency brute-force scripts without denying legitimate users.
     */
    THROTTLED(
        "Tier 1: Progressive Throttle",
        "Artificial latency penalty (1500ms) injected per attempt."
    ),

    /**
     * 6–9 failed attempts: Hard temporary time-based lockout with exponential cooldown.
     * All authentication requests are immediately rejected with HTTP 429.
     */
    TEMPORARY_LOCKOUT(
        "Tier 2: Hard Lockout",
        "Vault locked out. Exponential backoff cooldown active."
    ),

    /**
     * 10+ failed attempts: Critical lockout. The vault is locked down and requires
     * researcher lab reset or master recovery.
     */
    PERMANENT_LOCKOUT(
        "Tier 3: Critical Lockdown",
        "Critical threshold reached. Requires lab reset or administrative unlock."
    )
}
