package com.atharva.securevault.network

import android.content.Context
import com.atharva.securevault.DefenseMode
import com.atharva.securevault.authentication.PinAuthenticator
import com.atharva.securevault.defense.DefenseManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Embedded research verification daemon running within the Android process on port 8080.
 * Regulated by [DefenseManager] enforcing token-bucket rate limiting and multi-tier progressive lockouts.
 */
class LabServer(
    context: Context,
    port: Int = 8080,
    val defenseManager: DefenseManager = DefenseManager(context)
) : NanoHTTPD(port) {

    private val authenticator = PinAuthenticator(context)
    private val attemptCounter = AtomicInteger(0)

    private val _attempts = MutableStateFlow(0)
    val attempts: StateFlow<Int> = _attempts.asStateFlow()

    private val _vaultUnlocked = MutableStateFlow(false)
    val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()

    private val _successfulAttempt = MutableStateFlow(0)
    val successfulAttempt: StateFlow<Int> = _successfulAttempt.asStateFlow()

    private val _lastResponseTime = MutableStateFlow(0L)
    val lastResponseTime: StateFlow<Long> = _lastResponseTime.asStateFlow()

    private val _experimentStartTime = MutableStateFlow<Long?>(null)
    val experimentStartTime: StateFlow<Long?> = _experimentStartTime.asStateFlow()

    private val _defenseMode = MutableStateFlow(DefenseMode.NONE)
    val defenseMode: StateFlow<DefenseMode> = _defenseMode.asStateFlow()

    fun setDefenseMode(mode: DefenseMode) {
        _defenseMode.value = mode
    }

    fun resetAttempts() {
        attemptCounter.set(0)
        _attempts.value = 0
        _vaultUnlocked.value = false
        _successfulAttempt.value = 0
        _lastResponseTime.value = 0
        _experimentStartTime.value = null
        defenseManager.resetDefenseState()
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/lab/verify") {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                """{"error":"endpoint_not_found"}"""
            )
        }

        if (session.method != Method.POST) {
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                "application/json",
                """{"error":"POST_required"}"""
            )
        }

        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"] ?: ""
            val json = JSONObject(body)
            val pin = json.optString("pin", "")

            if (!pin.matches(Regex("\\d{4}"))) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    """{"error":"pin_must_be_4_digits"}"""
                )
            }

            // Phase 2 Defense Evaluation (Token Bucket & Persistent Lockout Check)
            val defenseCheck = defenseManager.evaluateBeforeVerification(_defenseMode.value)
            if (!defenseCheck.isAllowed) {
                val errorJson = JSONObject().apply {
                    put("error", "rate_limit_exceeded")
                    put("tier", defenseCheck.tier.name)
                    put("failedAttempts", defenseCheck.failedAttempts)
                    put("retryAfterSeconds", defenseCheck.retryAfterSeconds ?: 1)
                    put("remainingLockoutSeconds", defenseCheck.remainingLockoutSeconds)
                    put("remainingTokens", defenseCheck.remainingTokens)
                    put("reason", defenseCheck.reason ?: "Defensive rate limit triggered")
                }
                return newFixedLengthResponse(
                    Response.Status.TOO_MANY_REQUESTS,
                    "application/json",
                    errorJson.toString()
                ).apply {
                    addHeader("Retry-After", (defenseCheck.retryAfterSeconds ?: 1).toString())
                    addHeader("X-RateLimit-Tier", defenseCheck.tier.name)
                }
            }

            // Inject progressive artificial latency if Tier 1 (Throttled)
            if (defenseCheck.penaltyDelayMs > 0) {
                try {
                    Thread.sleep(defenseCheck.penaltyDelayMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            val attempt = attemptCounter.incrementAndGet()
            _attempts.value = attempt
            if (attempt == 1) {
                _experimentStartTime.value = System.currentTimeMillis()
            }

            val telemetry = authenticator.verifyWithTelemetry(pin)
            val success = telemetry.isSuccess

            // Update defense engine with result (resets or increments failure count)
            val outcomeState = defenseManager.recordAttemptResult(success, _defenseMode.value)

            if (success) {
                _vaultUnlocked.value = true
                _successfulAttempt.value = attempt
            }

            val responseTimeMs = telemetry.executionTimeMs.toLong() + defenseCheck.penaltyDelayMs
            _lastResponseTime.value = responseTimeMs

            val response = JSONObject().apply {
                put("success", success)
                put("attempt", attempt)
                put("responseTimeMs", responseTimeMs)
                put("executionTimeNanos", telemetry.executionTimeNanos)
                put("algorithm", telemetry.algorithm.name)
                put("constantTime", telemetry.isConstantTime)
                put("hardwareBacked", telemetry.isHardwareBacked)
                put("defenseTier", outcomeState.tier.name)
                put("failedAttempts", outcomeState.failedAttempts)
                put("penaltyDelayMs", defenseCheck.penaltyDelayMs)
                put("remainingTokens", outcomeState.remainingTokens)
            }

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                response.toString()
            )

        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                """{"error":"invalid_request"}"""
            )
        }
    }
}