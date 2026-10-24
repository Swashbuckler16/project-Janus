package com.atharva.securevault
import com.atharva.securevault.authentication.PinAuthenticator
import com.atharva.securevault.authentication.ResearchAuthenticator
import androidx.compose.ui.platform.LocalContext
import com.atharva.securevault.security.LabPinStore
import com.atharva.securevault.network.LabServer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.atharva.securevault.security.crypto.HashAlgorithm
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect


class MainActivity : ComponentActivity() {
    private lateinit var labServer: LabServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        labServer = LabServer(this, 8080)

        try {
            labServer.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {

            var currentScreen by remember {
                mutableStateOf("vault")
            }
            val vaultUnlocked by labServer.vaultUnlocked.collectAsState()
            val attempts by labServer.attempts.collectAsState()
            val responseTime by labServer.lastResponseTime.collectAsState()

            LaunchedEffect(vaultUnlocked) {
                if (vaultUnlocked) {
                    currentScreen = "compromise"
                }
            }

            when (currentScreen) {

                "vault" -> {
                    SecureVaultScreen(
                        onOpenLab = {
                            currentScreen = "research"
                        }
                    )
                }

                "research" -> {
                    ResearchModeScreen(
                        onBack = {
                            currentScreen = "vault"
                        },
                        onAuthenticated = {
                            currentScreen = "lab"
                        }
                    )
                }

                "lab" -> {
                    SecurityLabScreen(
                        onBack = {
                            currentScreen = "vault"
                        },
                        labServer = labServer
                    )
                }
                "home" -> {
                    VaultHomeScreen(
                        attempts = labServer.successfulAttempt.collectAsState().value,
                        onLock = {
                            labServer.resetAttempts()
                            currentScreen = "vault"
                        }
                    )
                }
                "compromise" -> {
                    CompromiseReportScreen(
                        attempts = attempts,
                        responseTime = responseTime,
                        onViewVault = {
                            currentScreen = "home"
                        },
                        onLock = {
                            labServer.resetAttempts()
                            currentScreen = "vault"
                        }
                    )
                }
            }
        }
    }
    override fun onDestroy() {
        labServer.stop()
        super.onDestroy()
    }
}

@Composable
fun SecureVaultScreen(
    onOpenLab: () -> Unit
) {

    var pin by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("Enter your 4-digit PIN") }

    val context = LocalContext.current

    val authenticator = remember {
        PinAuthenticator(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "🔒",
            fontSize = 48.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "PROJECT JANUS",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "DEVICE LOCKED",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = if (pin.isEmpty()) "• • • •"
            else pin.map { "•" }.joinToString(" "),
            color = Color.White,
            fontSize = 32.sp,
            letterSpacing = 8.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = message,
            color = Color.LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(25.dp))

        val numbers = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )

        numbers.forEach { row ->

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {

                row.forEach { number ->

                    Button(
                        onClick = {

                            if (number == "⌫") {

                                if (pin.isNotEmpty()) {
                                    pin = pin.dropLast(1)
                                }

                            } else if (number.isNotEmpty() && pin.length < 4) {

                                pin += number

                                if (pin.length == 4) {

                                    attempts++

                                    if (authenticator.verify(pin)) {
                                        message = "✓ ACCESS GRANTED"
                                    } else {
                                        message = "✗ INCORRECT PIN"
                                        pin = ""
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(75.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25272C)
                        )
                    ) {

                        Text(
                            text = number,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Attempts: $attempts",
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onOpenLab,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "SECURITY LAB",
                fontSize = 14.sp
            )
        }
    }
}
@Composable
fun SecurityLabScreen(
    onBack: () -> Unit,
    labServer: LabServer
) {
    var targetPin by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val pinStore = remember { LabPinStore(context) }
    var selectedAlgorithm by remember { mutableStateOf(pinStore.getActiveAlgorithm()) }
    val attempts by labServer.attempts.collectAsState()
    val defenseMode by labServer.defenseMode.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114))
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25272C))
            ) {
                Text("← BACK", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "🏛️",
            fontSize = 44.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "PROJECT JANUS LAB",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Authentication Benchmark & Telemetry",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Hardware Keystore Status Badge
        Row(
            modifier = Modifier
                .background(
                    if (pinStore.isHardwareProtected()) Color(0xFF0F3822) else Color(0xFF3B2E10),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (pinStore.isHardwareProtected()) "🔒 Hardware TEE / StrongBox Protected" else "🛡️ Keystore AES-256-GCM Active",
                color = if (pinStore.isHardwareProtected()) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Key Derivation Selector
        Text(
            text = "KEY DERIVATION ALGORITHM",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedAlgorithm = HashAlgorithm.PBKDF2_HMAC_SHA256 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedAlgorithm == HashAlgorithm.PBKDF2_HMAC_SHA256) Color(0xFF2563EB) else Color(0xFF1F2228)
                )
            ) {
                Text("PBKDF2 (100k)", fontSize = 12.sp, color = Color.White)
            }

            Button(
                onClick = { selectedAlgorithm = HashAlgorithm.LEGACY_SHA256 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedAlgorithm == HashAlgorithm.LEGACY_SHA256) Color(0xFFDC2626) else Color(0xFF1F2228)
                )
            ) {
                Text("SHA-256 (Legacy)", fontSize = 12.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Target PIN Configuration
        Text(
            text = "TARGET PIN",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.material3.OutlinedTextField(
            value = targetPin,
            onValueChange = { value ->
                if (value.length <= 4 && value.all { it.isDigit() }) {
                    targetPin = value
                    saveMessage = ""
                }
            },
            label = { Text("Enter 4-digit PIN") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (targetPin.length == 4) {
                    pinStore.savePin(targetPin, selectedAlgorithm)
                    saveMessage = "✓ Saved with ${selectedAlgorithm.name}"
                } else {
                    saveMessage = "Enter exactly 4 digits"
                }
            },
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("SAVE TARGET PIN")
        }

        if (saveMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = saveMessage,
                color = Color(0xFF38BDF8),
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Defense Mode Toggle
        Text(
            text = "DEFENSIVE COUNTERMEASURES",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val nextMode = if (defenseMode == DefenseMode.NONE) DefenseMode.RATE_LIMIT else DefenseMode.NONE
                labServer.setDefenseMode(nextMode)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (defenseMode == DefenseMode.RATE_LIMIT) Color(0xFF059669) else Color(0xFF374151)
            )
        ) {
            Text(
                text = if (defenseMode == DefenseMode.RATE_LIMIT) "✓ Rate Limiter: ACTIVE (Max 5 req/10s)" else "Rate Limiter: OFF (Unprotected)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Attack Status & Reset
        Text(
            text = "ATTACK & TELEMETRY STATUS",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Server Attempts: $attempts",
            color = Color.LightGray,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { labServer.resetAttempts() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25272C))
        ) {
            Text("RESET TELEMETRY & LOCK", color = Color.White)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
@Composable
fun ResearchModeScreen(
    onBack: () -> Unit,
    onAuthenticated: () -> Unit
) {

    var pin by remember { mutableStateOf("") }
    var message by remember {
        mutableStateOf("Enter researcher PIN")
    }

    val authenticator = remember {
        ResearchAuthenticator()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "🛡️",
            fontSize = 48.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "RESEARCH MODE",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Authorized security testing only",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = if (pin.isEmpty())
                "• • • •"
            else
                pin.map { "•" }.joinToString(" "),
            color = Color.White,
            fontSize = 32.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            color = Color.LightGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(25.dp))

        val numbers = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )

        numbers.forEach { row ->

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {

                row.forEach { number ->

                    Button(
                        onClick = {

                            if (number == "⌫") {

                                if (pin.isNotEmpty()) {
                                    pin = pin.dropLast(1)
                                }

                            } else if (
                                number.isNotEmpty() &&
                                pin.length < 4
                            ) {

                                pin += number

                                if (pin.length == 4) {

                                    if (authenticator.verify(pin)) {

                                        message = "✓ ACCESS GRANTED"

                                        onAuthenticated()

                                    } else {

                                        message = "✗ INCORRECT PIN"
                                        pin = ""
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(75.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25272C)
                        )
                    ) {

                        Text(
                            text = number,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("← BACK")
        }
    }
}