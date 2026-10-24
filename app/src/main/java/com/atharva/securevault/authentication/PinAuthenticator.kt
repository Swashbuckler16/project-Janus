package com.atharva.securevault.authentication

import android.content.Context
import com.atharva.securevault.security.PinVerifier

class PinAuthenticator(context: Context) {

    private val verifier = PinVerifier(context)

    fun verify(pin: String): Boolean {
        return verifier.verify(pin)
    }
}