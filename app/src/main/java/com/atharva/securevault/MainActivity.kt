package com.atharva.securevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharva.securevault.authentication.PinAuthenticator
import com.atharva.securevault.authentication.ResearchAuthenticator
import com.atharva.securevault.DefenseMode
import com.atharva.securevault.defense.LockoutTier
import com.atharva.securevault.network.LabServer
import com.atharva.securevault.security.LabPinStore
import com.atharva.securevault.security.crypto.HashAlgorithm
import com.atharva.securevault.ui.theme.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var labServer: LabServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        labServer = LabServer(this, 8080)
        labServer.resetAttempts()

        // Ensure default PIN (1234) is seeded if not configured or migrated
        val pinStore = LabPinStore(this)
        if (!pinStore.isConfigured() || pinStore.getConfiguredPin() == null) {
            pinStore.savePin("1234", pinStore.getActiveAlgorithm())
        }

        try {
            labServer.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            SecureVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SlateDeep
                ) {
                    var currentScreen by remember { mutableStateOf("vault") }
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
                                onUnlock = {
                                    labServer.resetAttempts()
                                    currentScreen = "home"
                                },
                                onOpenLab = { currentScreen = "research" },
                                labServer = labServer
                            )
                        }
                        "research" -> {
                            ResearchModeScreen(
                                onBack = { currentScreen = "vault" },
                                onAuthenticated = { currentScreen = "lab" }
                            )
                        }
                        "lab" -> {
                            SecurityLabScreen(
                                onBack = { currentScreen = "vault" },
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
                                onViewVault = { currentScreen = "home" },
                                onLock = {
                                    labServer.resetAttempts()
                                    currentScreen = "vault"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        labServer.stop()
        super.onDestroy()
    }
}

/**
 * User-Friendly Warm & Cool Lock Screen
 */
@Composable
fun SecureVaultScreen(
    onUnlock: () -> Unit,
    onOpenLab: () -> Unit,
    labServer: LabServer
) {
    var pin by remember { mutableStateOf("") }
    var userFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isPinIncorrect by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val authenticator = remember { PinAuthenticator(context) }
    val defenseState by labServer.defenseManager.defenseState.collectAsState()
    val defenseMode by labServer.defenseMode.collectAsState()

    var remainingCooldown by remember { mutableStateOf(defenseState.remainingLockoutSeconds) }

    LaunchedEffect(defenseState.remainingLockoutSeconds) {
        remainingCooldown = defenseState.remainingLockoutSeconds
        while (remainingCooldown > 0) {
            delay(1000)
            remainingCooldown -= 1
        }
    }

    val isLockedOut = defenseMode == DefenseMode.ENTERPRISE_TOKEN_BUCKET &&
        (remainingCooldown > 0 || defenseState.tier == LockoutTier.PERMANENT_LOCKOUT)

    fun onKeyClick(digit: String) {
        if (digit == "⌫") {
            if (pin.isNotEmpty()) {
                pin = pin.dropLast(1)
                isPinIncorrect = false
                userFeedbackMessage = null
            }
        } else if (pin.length < 4) {
            val newPin = pin + digit
            pin = newPin
            isPinIncorrect = false
            userFeedbackMessage = null

            if (newPin.length == 4) {
                val isCorrect = authenticator.verify(newPin)
                labServer.defenseManager.recordAttemptResult(isCorrect, defenseMode)

                if (isCorrect) {
                    isPinIncorrect = false
                    userFeedbackMessage = null
                    onUnlock()
                } else {
                    isPinIncorrect = true
                    userFeedbackMessage = "Incorrect PIN, please try again"
                    pin = ""
                }
            }
        }
    }

    val statusText = when {
        userFeedbackMessage != null -> userFeedbackMessage!!
        isLockedOut && remainingCooldown > 0 -> "Please wait ${remainingCooldown}s before trying again"
        isLockedOut -> "Rate limit active"
        else -> "Enter your 4-digit PIN to unlock"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeep)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Friendly Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Secure Vault",
                    color = WarmIvory,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SlateSurface)
                    .border(1.dp, SlateHighlight, RoundedCornerShape(20.dp))
                    .clickable { onOpenLab() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Security Lab",
                    tint = AmberLight,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Security Lab",
                    color = SlateIce,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center Greeting & PIN Indicators
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AmberSurface)
                    .border(1.dp, AmberBorder, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = AmberLight,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome Back",
                color = WarmIvory,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusText,
                color = if (isPinIncorrect) WarmCoral else SlateLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(26.dp))

            // PIN Dots in Warm Amber & Cool Slate
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isEntered = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isPinIncorrect -> WarmCoral
                                    isEntered -> AmberPrimary
                                    else -> SlateSurface
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = when {
                                    isPinIncorrect -> WarmCoral
                                    isEntered -> AmberLight
                                    else -> SlateHighlight
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        // Circular Number Buttons Keypad
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keypadLayout = listOf(
                listOf(KeypadKey("1", ""), KeypadKey("2", "ABC"), KeypadKey("3", "DEF")),
                listOf(KeypadKey("4", "GHI"), KeypadKey("5", "JKL"), KeypadKey("6", "MNO")),
                listOf(KeypadKey("7", "PQRS"), KeypadKey("8", "TUV"), KeypadKey("9", "WXYZ")),
                listOf(KeypadKey("", ""), KeypadKey("0", "+"), KeypadKey("⌫", ""))
            )

            keypadLayout.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        if (key.digit.isEmpty()) {
                            Spacer(modifier = Modifier.size(74.dp))
                        } else {
                            TactileKeycap(
                                digit = key.digit,
                                sublabel = key.sublabel,
                                onClick = { onKeyClick(key.digit) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

private data class KeypadKey(val digit: String, val sublabel: String)

@Composable
private fun TactileKeycap(
    digit: String,
    sublabel: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(74.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SlateSurface,
            contentColor = WarmIvory
        ),
        border = BorderStroke(1.dp, SlateHighlight)
    ) {
        if (digit == "⌫") {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Backspace",
                tint = AmberSoft,
                modifier = Modifier.size(21.dp)
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digit,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WarmIvory
                )
                if (sublabel.isNotEmpty()) {
                    Text(
                        text = sublabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = SlateLight
                    )
                }
            }
        }
    }
}

/**
 * User-Friendly Security Lab Settings
 */
@Composable
fun SecurityLabScreen(
    onBack: () -> Unit,
    labServer: LabServer
) {
    val context = LocalContext.current
    val pinStore = remember { LabPinStore(context) }
    var targetPin by remember { mutableStateOf(pinStore.getConfiguredPin() ?: "1234") }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    var selectedAlgorithm by remember { mutableStateOf(pinStore.getActiveAlgorithm()) }
    val attempts by labServer.attempts.collectAsState()
    val defenseMode by labServer.defenseMode.collectAsState()
    val defenseState by labServer.defenseManager.defenseState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeep)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SlateSurface)
                    .border(1.dp, SlateHighlight, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = WarmIvory
                )
            }

            Text(
                text = "Security Lab",
                color = WarmIvory,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(
                onClick = {
                    labServer.resetAttempts()
                    saveMessage = "Activity reset"
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SlateSurface)
                    .border(1.dp, SlateHighlight, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Reset",
                    tint = AmberLight
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 1: Vault PIN
        Text(
            text = "Vault PIN",
            color = WarmCream,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Set the 4-digit passcode used to unlock the vault",
            color = SlateLight,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateSurface, shape = RoundedCornerShape(14.dp))
                .border(1.dp, SlateHighlight, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                OutlinedTextField(
                    value = targetPin,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all { it.isDigit() }) {
                            targetPin = value
                            saveMessage = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter 4-digit PIN", color = SlateMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WarmIvory,
                        unfocusedTextColor = WarmIvory,
                        focusedBorderColor = AmberPrimary,
                        unfocusedBorderColor = SlateHighlight,
                        focusedContainerColor = SlateDark,
                        unfocusedContainerColor = SlateDark
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (targetPin.length == 4) {
                            pinStore.savePin(targetPin, selectedAlgorithm)
                            labServer.resetAttempts()
                            saveMessage = "PIN updated successfully"
                        } else {
                            saveMessage = "Please enter 4 digits"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = SlateDeep
                    )
                ) {
                    Text(text = "Save PIN", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                if (saveMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = saveMessage!!,
                        color = AmberSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 2: Encryption Method
        Text(
            text = "Encryption Method",
            color = WarmCream,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AlgoChip(
                modifier = Modifier.weight(1f),
                title = "PBKDF2",
                subtitle = "Strong • Salted",
                isSelected = selectedAlgorithm == HashAlgorithm.PBKDF2_HMAC_SHA256,
                onClick = {
                    selectedAlgorithm = HashAlgorithm.PBKDF2_HMAC_SHA256
                    if (targetPin.length == 4) {
                        pinStore.savePin(targetPin, HashAlgorithm.PBKDF2_HMAC_SHA256)
                    }
                }
            )

            AlgoChip(
                modifier = Modifier.weight(1f),
                title = "SHA-256",
                subtitle = "Fast • Basic",
                isSelected = selectedAlgorithm == HashAlgorithm.LEGACY_SHA256,
                onClick = {
                    selectedAlgorithm = HashAlgorithm.LEGACY_SHA256
                    if (targetPin.length == 4) {
                        pinStore.savePin(targetPin, HashAlgorithm.LEGACY_SHA256)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 3: Rate Limiting & Protection
        Text(
            text = "Login Protection",
            color = WarmCream,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        DefenseModeRow(
            title = "Smart Lockout",
            subtitle = "Delays repeated failed attempts",
            isSelected = defenseMode == DefenseMode.ENTERPRISE_TOKEN_BUCKET,
            onClick = { labServer.setDefenseMode(DefenseMode.ENTERPRISE_TOKEN_BUCKET) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        DefenseModeRow(
            title = "Window Limit",
            subtitle = "Limits attempts within 10 seconds",
            isSelected = defenseMode == DefenseMode.SLIDING_WINDOW,
            onClick = { labServer.setDefenseMode(DefenseMode.SLIDING_WINDOW) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        DefenseModeRow(
            title = "Off",
            subtitle = "No rate limit for speed testing",
            isSelected = defenseMode == DefenseMode.NONE,
            onClick = { labServer.setDefenseMode(DefenseMode.NONE) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section 4: Activity Summary
        Text(
            text = "Activity Summary",
            color = WarmCream,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateSurface, shape = RoundedCornerShape(14.dp))
                .border(1.dp, SlateHighlight, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                DiagRow(label = "Status", value = defenseState.tier.displayName)
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = OutlineVariantDark)
                Spacer(modifier = Modifier.height(10.dp))
                DiagRow(label = "Failed Tries", value = "${defenseState.failedAttempts}")
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = OutlineVariantDark)
                Spacer(modifier = Modifier.height(10.dp))
                DiagRow(label = "Total Requests", value = "$attempts")
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { labServer.resetAttempts() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SlateElevated,
                        contentColor = WarmIvory
                    ),
                    border = BorderStroke(1.dp, SlateHighlight)
                ) {
                    Text(text = "Reset Activity", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AlgoChip(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AmberSurface else SlateSurface)
            .border(
                1.dp,
                if (isSelected) AmberPrimary else SlateHighlight,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                color = if (isSelected) AmberSoft else WarmIvory,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = if (isSelected) WarmCream else SlateLight,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DefenseModeRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AmberSurface else SlateSurface)
            .border(
                1.dp,
                if (isSelected) AmberPrimary else SlateHighlight,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = AmberPrimary,
                    unselectedColor = SlateLight
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = if (isSelected) AmberSoft else WarmIvory,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = SlateLight,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = SlateLight, fontSize = 13.sp)
        Text(text = value, color = WarmIvory, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * User-Friendly Security Lab Passkey Screen
 */
@Composable
fun ResearchModeScreen(
    onBack: () -> Unit,
    onAuthenticated: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val authenticator = remember { ResearchAuthenticator() }

    fun onKeyClick(digit: String) {
        if (digit == "⌫") {
            if (pin.isNotEmpty()) {
                pin = pin.dropLast(1)
                isError = false
                feedbackMessage = null
            }
        } else if (pin.length < 4) {
            val newPin = pin + digit
            pin = newPin
            isError = false
            feedbackMessage = null

            if (newPin.length == 4) {
                if (authenticator.verify(newPin)) {
                    feedbackMessage = null
                    onAuthenticated()
                } else {
                    isError = true
                    feedbackMessage = "Incorrect passkey, please try again"
                    pin = ""
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeep)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SlateSurface)
                    .border(1.dp, SlateHighlight, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = WarmIvory
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AmberSurface)
                    .border(1.dp, AmberBorder, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VpnKey,
                    contentDescription = null,
                    tint = AmberLight,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Security Lab Access",
                color = WarmIvory,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = feedbackMessage ?: "Enter the 4-digit lab passkey",
                color = if (isError) WarmCoral else SlateLight,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isEntered = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isError -> WarmCoral
                                    isEntered -> AmberPrimary
                                    else -> SlateSurface
                                }
                            )
                            .border(
                                1.5.dp,
                                when {
                                    isError -> WarmCoral
                                    isEntered -> AmberLight
                                    else -> SlateHighlight
                                },
                                CircleShape
                            )
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keypadLayout = listOf(
                listOf(KeypadKey("1", ""), KeypadKey("2", "ABC"), KeypadKey("3", "DEF")),
                listOf(KeypadKey("4", "GHI"), KeypadKey("5", "JKL"), KeypadKey("6", "MNO")),
                listOf(KeypadKey("7", "PQRS"), KeypadKey("8", "TUV"), KeypadKey("9", "WXYZ")),
                listOf(KeypadKey("", ""), KeypadKey("0", "+"), KeypadKey("⌫", ""))
            )

            keypadLayout.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        if (key.digit.isEmpty()) {
                            Spacer(modifier = Modifier.size(74.dp))
                        } else {
                            TactileKeycap(
                                digit = key.digit,
                                sublabel = key.sublabel,
                                onClick = { onKeyClick(key.digit) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
