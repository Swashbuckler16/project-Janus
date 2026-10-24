package com.atharva.securevault.security

import android.content.Context
import java.security.MessageDigest

class LabPinStore(context: Context) {

    private val preferences = context.getSharedPreferences(
        "secure_vault_lab",
        Context.MODE_PRIVATE
    )

    fun savePin(pin: String) {
        val hash = hashPin(pin)

        preferences.edit()
            .putString("pin_verifier", hash)
            .apply()
    }

    fun getVerifier(): String? {
        return preferences.getString("pin_verifier", null)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")

        return digest
            .digest(pin.toByteArray())
            .joinToString("") {
                "%02x".format(it)
            }
    }
}