package com.atharva.securevault.authentication

/**
 * Research-only gatekeeper used strictly to calibrate automated attack experiments
 * and unlock developer metrics on test devices.
 *
 * LAB CREDENTIAL NOTICE:
 * The hardcoded PIN ("2468") is a deliberate, documented test-bench credential created
 * exclusively for automated attack harness calibration (see test_pin_generator.py and main.py).
 * Production application security is handled by hardware-backed Keystore biometric/PIN authentication
 * via [com.atharva.securevault.security.storage.SecurePinStore] and [com.atharva.securevault.security.PinVerifier].
 */
class ResearchAuthenticator {

    companion object {
        /**
         * Documented lab credential for synthetic test automation.
         */
        const val LAB_RESEARCH_PIN = "2468"
    }

    fun verify(pin: String): Boolean {
        return pin == LAB_RESEARCH_PIN
    }
}