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
    val attempts by labServer.attempts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("← BACK")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "🛡️",
            fontSize = 48.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SECURITY RESEARCH LAB",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Controlled authentication testing",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "TARGET PIN",
            color = Color.White,
            fontSize = 16.sp,
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
            label = {
                Text("Enter 4-digit PIN")
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {

                if (targetPin.length == 4) {

                    pinStore.savePin(targetPin)

                    saveMessage = "✓ Target PIN configured"

                } else {

                    saveMessage = "Enter exactly 4 digits"

                }

            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("SAVE TARGET")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = saveMessage,
            color = Color.LightGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Defense Configuration",
            color = Color.White,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("No Protection")
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ATTACK STATUS",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "API Attempts: $attempts",
            color = Color.LightGray,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "START EXPERIMENT",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
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