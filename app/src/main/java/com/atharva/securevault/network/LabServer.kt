package com.atharva.securevault.network

import android.content.Context
import com.atharva.securevault.authentication.PinAuthenticator
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import com.atharva.securevault.DefenseMode

class LabServer(
    context: Context,
    port: Int = 8080
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

    private val rateLimitWindowMs = 10_000L
    private val rateLimitMaxAttempts = 5

    private var windowStartTime = 0L
    private var windowAttempts = 0

    fun setDefenseMode(mode: DefenseMode) {
        _defenseMode.value = mode

        windowStartTime = System.currentTimeMillis()
        windowAttempts = 0
    }

    fun resetAttempts() {
        attemptCounter.set(0)

        _attempts.value = 0
        _vaultUnlocked.value = false
        _successfulAttempt.value = 0
        _lastResponseTime.value = 0
        _experimentStartTime.value = null

        windowStartTime = System.currentTimeMillis()
        windowAttempts = 0
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
            if (_defenseMode.value == DefenseMode.RATE_LIMIT) {

                val now = System.currentTimeMillis()

                if (now - windowStartTime >= rateLimitWindowMs) {
                    windowStartTime = now
                    windowAttempts = 0
                }

                if (windowAttempts >= rateLimitMaxAttempts) {

                    return newFixedLengthResponse(
                        Response.Status.TOO_MANY_REQUESTS,
                        "application/json",
                        """{"error":"rate_limit_exceeded","retryAfterMs":${rateLimitWindowMs - (now - windowStartTime)}}"""
                    )
                }

                windowAttempts++
            }

            // Count this authentication attempt
            val attempt = attemptCounter.incrementAndGet()
            _attempts.value = attempt
            if (attempt == 1) {
                _experimentStartTime.value = System.currentTimeMillis()
            }

            val telemetry = authenticator.verifyWithTelemetry(pin)
            val success = telemetry.isSuccess

            if (success) {
                _vaultUnlocked.value = true
                _successfulAttempt.value = attempt
            }

            val responseTimeMs = telemetry.executionTimeMs.toLong()
            _lastResponseTime.value = responseTimeMs

            val response = JSONObject().apply {
                put("success", success)
                put("attempt", attempt)
                put("responseTimeMs", responseTimeMs)
                put("executionTimeNanos", telemetry.executionTimeNanos)
                put("algorithm", telemetry.algorithm.name)
                put("constantTime", telemetry.isConstantTime)
                put("hardwareBacked", telemetry.isHardwareBacked)
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