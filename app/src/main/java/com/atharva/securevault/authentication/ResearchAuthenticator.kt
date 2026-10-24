package com.atharva.securevault.authentication

class ResearchAuthenticator {

    private val researchPin = "2468"

    fun verify(pin: String): Boolean {
        return pin == researchPin
    }
}