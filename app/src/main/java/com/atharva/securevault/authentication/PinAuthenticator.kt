package com.atharva.securevault.authentication

import android.content.Context
import com.atharva.securevault.security.PinVerifier
import com.atharva.securevault.security.domain.VerificationResult

class PinAuthenticator(context: Context) {

    private val verifier = PinVerifier(context)

    fun verify(pin: String): Boolean {
        return verifier.verify(pin)
    }

    fun verifyWithTelemetry(pin: String): VerificationResult {
        return verifier.verifyWithTelemetry(pin)
    }
}