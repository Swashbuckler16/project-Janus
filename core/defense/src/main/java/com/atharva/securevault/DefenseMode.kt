package com.atharva.securevault

/**
 * Defensive countermeasure modes supported by Project Janus.
 */
enum class DefenseMode(val displayName: String) {
    NONE("Unprotected (No Defense)"),
    SLIDING_WINDOW("Sliding Window (5 req / 10s)"),
    ENTERPRISE_TOKEN_BUCKET("Enterprise Defense (Token-Bucket & Multi-Tier Lockout)")
}