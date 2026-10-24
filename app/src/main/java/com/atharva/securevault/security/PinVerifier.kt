package com.atharva.securevault.security

import android.content.Context
import java.security.MessageDigest

class PinVerifier(context: Context) {

    private val pinStore = LabPinStore(context)

    fun verify(pin: String): Boolean {

        val storedVerifier = pinStore.getVerifier()
            ?: return false

        val digest = MessageDigest.getInstance("SHA-256")

        val hash = digest
            .digest(pin.toByteArray())
            .joinToString("") {
                "%02x".format(it)
            }

        return hash == storedVerifier
    }
}