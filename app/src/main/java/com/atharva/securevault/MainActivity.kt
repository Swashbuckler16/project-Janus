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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

        try {
            labServer.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            SecureVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
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
 * Minimalist & Professional Security Lock Screen
 * Clean Swiss design with high contrast and subtle tactile feedback
 */
@Composable
fun SecureVaultScreen(
    onOpenLab: () -> Unit,
    labServer: LabServer
) {
    var pin by remember { mutableStateOf("") }
    var userFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isPinIncorrect by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val authenticator = remember { PinAuthenticator(context) }
    val pinStore = remember { LabPinStore(context) }
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

    val isLockedOut = remainingCooldown > 0 || defenseState.tier == LockoutTier.PERMANENT_LOCKOUT

    val statusPillText = when {
        defenseState.tier == LockoutTier.PERMANENT_LOCKOUT -> "Device Locked • Reset in Bench"
        remainingCooldown > 0 -> "Cooldown Active • ${remainingCooldown}s"
        defenseState.tier == LockoutTier.THROTTLED -> "Rate Limited"
        userFeedbackMessage != null -> userFeedbackMessage!!
        else -> "Enter Passcode"
    }

    val badgeBg = when {
        defenseState.tier == LockoutTier.PERMANENT_LOCKOUT || isPinIncorrect -> StatusCriticalContainer.copy(alpha = 0.6f)
        remainingCooldown > 0 || defenseState.tier == LockoutTier.THROTTLED -> StatusWarningContainer.copy(alpha = 0.6f)
        userFeedbackMessage == "Access Granted" -> StatusSuccessContainer.copy(alpha = 0.6f)
        else -> SurfaceVariantDark
    }

    val badgeBorder = when {
        defenseState.tier == LockoutTier.PERMANENT_LOCKOUT || isPinIncorrect -> StatusCritical.copy(alpha = 0.4f)
        remainingCooldown > 0 || defenseState.tier == LockoutTier.THROTTLED -> StatusWarning.copy(alpha = 0.4f)
        userFeedbackMessage == "Access Granted" -> StatusSuccess.copy(alpha = 0.4f)
        else -> OutlineDark
    }

    val badgeTextColor = when {
        defenseState.tier == LockoutTier.PERMANENT_LOCKOUT || isPinIncorrect -> OnStatusCritical
        remainingCooldown > 0 || defenseState.tier == LockoutTier.THROTTLED -> OnStatusWarning
        userFeedbackMessage == "Access Granted" -> OnStatusSuccess
        else -> TextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Minimalist Status Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (pinStore.isHardwareProtected()) StatusSuccess else StatusInfo)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SECURE VAULT",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceDark)
                    .border(1.dp, OutlineDark, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = if (pinStore.isHardwareProtected()) StatusSuccess else TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (pinStore.isHardwareProtected()) "HARDWARE TEE" else "SOFTWARE KEYSTORE",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center Identity & PIN Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevatedDark)
                    .border(
                        1.5.dp,
                        if (isLockedOut || isPinIncorrect) StatusCritical.copy(alpha = 0.5f) else OutlineDark,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isLockedOut -> Icons.Outlined.Lock
                        isPinIncorrect -> Icons.Outlined.ErrorOutline
                        else -> Icons.Outlined.Shield
                    },
                    contentDescription = "Security Status",
                    tint = when {
                        isLockedOut || isPinIncorrect -> StatusCritical
                        else -> PrimaryAccentHover
                    },
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Project Janus",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Semantic Status Badge Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(badgeBg)
                    .border(1.dp, badgeBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusPillText,
                    color = badgeTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // PIN Indicator Slots
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
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
                                    isPinIncorrect -> StatusCritical
                                    isEntered -> PrimaryAccent
                                    else -> Color.Transparent
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = when {
                                    isPinIncorrect -> StatusCritical
                                    isEntered -> PrimaryAccent
                                    else -> OutlineDark
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        // Numeric Keypad
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
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        if (key.digit.isEmpty()) {
                            Spacer(modifier = Modifier.size(72.dp))
                        } else {
                            TactileKeycap(
                                digit = key.digit,
                                sublabel = key.sublabel,
                                isEnabled = !isLockedOut,
                                onClick = {
                                    if (!isLockedOut) {
                                        if (key.digit == "⌫") {
                                            if (pin.isNotEmpty()) {
                                                pin = pin.dropLast(1)
                                                isPinIncorrect = false
                                                userFeedbackMessage = null
                                            }
                                        } else if (pin.length < 4) {
                                            pin += key.digit
                                            isPinIncorrect = false
                                            userFeedbackMessage = null

                                            if (pin.length == 4) {
                                                val isCorrect = authenticator.verify(pin)
                                                labServer.defenseManager.recordAttemptResult(isCorrect, defenseMode)

                                                if (isCorrect) {
                                                    userFeedbackMessage = "Access Granted"
                                                } else {
                                                    isPinIncorrect = true
                                                    userFeedbackMessage = "Incorrect PIN"
                                                    pin = ""
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls - Professional Outlined Action
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = onOpenLab,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary
                ),
                border = BorderStroke(1.dp, OutlineDark)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = null,
                    tint = PrimaryAccentHover,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Security Bench & Diagnostics",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class KeypadKey(val digit: String, val sublabel: String)

@Composable
private fun TactileKeycap(
    digit: String,
    sublabel: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (isEnabled) KeypadButtonNormal else KeypadButtonLocked)
            .border(
                1.dp,
                if (isEnabled) OutlineDark else OutlineVariantDark,
                CircleShape
            )
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (digit == "⌫") {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Backspace",
                tint = if (isEnabled) TextPrimary else TextMuted,
                modifier = Modifier.size(22.dp)
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
                    color = if (isEnabled) TextPrimary else TextMuted
                )
                if (sublabel.isNotEmpty()) {
                    Text(
                        text = sublabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                        color = if (isEnabled) KeypadTextSecondary else TextMuted.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

/**
 * Clean & Professional Security Calibration Bench
 */
@Composable
fun SecurityLabScreen(
    onBack: () -> Unit,
    labServer: LabServer
) {
    var targetPin by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val pinStore = remember { LabPinStore(context) }
    var selectedAlgorithm by remember { mutableStateOf(pinStore.getActiveAlgorithm()) }
    val attempts by labServer.attempts.collectAsState()
    val defenseMode by labServer.defenseMode.collectAsState()
    val defenseState by labServer.defenseManager.defenseState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, OutlineDark, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SECURITY BENCH",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Configuration & Diagnostics",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = {
                    labServer.resetAttempts()
                    saveMessage = "Telemetry counters reset"
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, OutlineDark, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Reset Telemetry",
                    tint = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hardware Enclave Info Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(OutlineDark)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevatedDark)
                        .border(1.dp, OutlineDark, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = PrimaryAccentHover,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (pinStore.isHardwareProtected()) "Hardware TEE Active" else "Keystore Emulation Active",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "AES-256-GCM Master Key",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (pinStore.isHardwareProtected()) StatusSuccessContainer else SurfaceVariantDark,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (pinStore.isHardwareProtected()) StatusSuccess.copy(alpha = 0.4f) else OutlineDark,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (pinStore.isHardwareProtected()) "ENCLAVE" else "SOFTWARE",
                        color = if (pinStore.isHardwareProtected()) OnStatusSuccess else TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Key Derivation Algorithm
        SectionHeader(title = "KEY DERIVATION", icon = Icons.Outlined.Key)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AlgorithmSelectorCard(
                modifier = Modifier.weight(1f),
                title = "PBKDF2-HMAC",
                subtitle = "100k rounds + salt",
                isSelected = selectedAlgorithm == HashAlgorithm.PBKDF2_HMAC_SHA256,
                badgeText = "HARDENED",
                onClick = { selectedAlgorithm = HashAlgorithm.PBKDF2_HMAC_SHA256 }
            )

            AlgorithmSelectorCard(
                modifier = Modifier.weight(1f),
                title = "SHA-256",
                subtitle = "Single pass baseline",
                isSelected = selectedAlgorithm == HashAlgorithm.LEGACY_SHA256,
                badgeText = "BASELINE",
                onClick = { selectedAlgorithm = HashAlgorithm.LEGACY_SHA256 }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Target Credential
        SectionHeader(title = "TARGET CREDENTIAL", icon = Icons.Outlined.Password)

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(OutlineDark)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                OutlinedTextField(
                    value = targetPin,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all { it.isDigit() }) {
                            targetPin = value
                            saveMessage = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Target 4-Digit PIN", color = TextSecondary) },
                    placeholder = { Text("e.g. 2468", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = OutlineDark,
                        focusedContainerColor = SurfaceVariantDark,
                        unfocusedContainerColor = SurfaceVariantDark
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        letterSpacing = 4.sp
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (targetPin.length == 4) {
                            pinStore.savePin(targetPin, selectedAlgorithm)
                            saveMessage = "Target PIN updated successfully"
                        } else {
                            saveMessage = "Input must contain 4 digits"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent,
                        contentColor = OnPrimary
                    )
                ) {
                    Text(
                        text = "Save Target PIN",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (saveMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = saveMessage!!,
                        color = if (saveMessage!!.contains("success", ignoreCase = true)) StatusSuccess else StatusWarning,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Defensive Policy Matrix
        SectionHeader(title = "DEFENSE POLICY", icon = Icons.Outlined.Security)

        Spacer(modifier = Modifier.height(10.dp))

        DefensePolicyCard(
            title = "Token-Bucket Rate Limiter",
            description = "Progressive exponential lockout with token bucket shaping.",
            badge = "HARDENED",
            isSelected = defenseMode == DefenseMode.ENTERPRISE_TOKEN_BUCKET,
            onClick = { labServer.setDefenseMode(DefenseMode.ENTERPRISE_TOKEN_BUCKET) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        DefensePolicyCard(
            title = "Sliding Window Limiter",
            description = "Fixed window allowing 5 requests per 10-second evaluation frame.",
            badge = "STANDARD",
            isSelected = defenseMode == DefenseMode.SLIDING_WINDOW,
            onClick = { labServer.setDefenseMode(DefenseMode.SLIDING_WINDOW) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        DefensePolicyCard(
            title = "Unprotected Baseline",
            description = "Zero artificial delays or request throttling.",
            badge = "BASELINE",
            isSelected = defenseMode == DefenseMode.NONE,
            onClick = { labServer.setDefenseMode(DefenseMode.NONE) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Diagnostics
        SectionHeader(title = "DEFENSE DIAGNOSTICS", icon = Icons.Outlined.Analytics)

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(OutlineDark)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Escalation Tier", color = TextSecondary, fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .background(SurfaceElevatedDark, shape = RoundedCornerShape(6.dp))
                            .border(1.dp, OutlineDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = defenseState.tier.displayName.uppercase(),
                            color = when (defenseState.tier) {
                                LockoutTier.PERMANENT_LOCKOUT, LockoutTier.TEMPORARY_LOCKOUT -> StatusCritical
                                LockoutTier.THROTTLED -> StatusWarning
                                else -> TextPrimary
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Token Capacity", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = "${String.format("%.1f", defenseState.remainingTokens)} / 5.0",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (defenseState.remainingTokens / 5.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = PrimaryAccent,
                    trackColor = SurfaceElevatedDark
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Failed Attempts", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = "${defenseState.failedAttempts}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Inbound Request Count", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = "$attempts",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedButton(
                    onClick = { labServer.resetAttempts() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceDark,
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, OutlineDark)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset Defense State",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryAccentHover,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun AlgorithmSelectorCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    badgeText: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) SurfaceVariantDark else SurfaceDark
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) PrimaryAccent else OutlineDark
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .background(SurfaceElevatedDark, shape = RoundedCornerShape(4.dp))
                    .border(
                        1.dp,
                        if (isSelected) PrimaryAccent.copy(alpha = 0.5f) else OutlineDark,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    color = if (isSelected) PrimaryAccentHover else TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DefensePolicyCard(
    title: String,
    description: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) SurfaceVariantDark else SurfaceDark
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) PrimaryAccent else OutlineDark
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = PrimaryAccent,
                    unselectedColor = OutlineDark
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .background(SurfaceElevatedDark, shape = RoundedCornerShape(4.dp))
                            .border(
                                1.dp,
                                if (isSelected) PrimaryAccent.copy(alpha = 0.5f) else OutlineDark,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = if (isSelected) PrimaryAccentHover else TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Clean Deliberate Lab Authorization Screen
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Back Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, OutlineDark, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
        }

        // Header & Indicator
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevatedDark)
                    .border(
                        1.5.dp,
                        if (isError) StatusCritical.copy(alpha = 0.5f) else OutlineDark,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VpnKey,
                    contentDescription = null,
                    tint = if (isError) StatusCritical else PrimaryAccentHover,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "BENCH ACCESS",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter authorization passkey",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Feedback / Status
            if (feedbackMessage != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isError) StatusCriticalContainer.copy(alpha = 0.6f) else StatusSuccessContainer.copy(alpha = 0.6f))
                        .border(
                            1.dp,
                            if (isError) StatusCritical.copy(alpha = 0.4f) else StatusSuccess.copy(alpha = 0.4f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = feedbackMessage!!,
                        color = if (isError) OnStatusCritical else OnStatusSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(28.dp))
            }

            // PIN Indicator Slots
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
                                    isError -> StatusCritical
                                    isEntered -> PrimaryAccent
                                    else -> Color.Transparent
                                }
                            )
                            .border(
                                1.5.dp,
                                when {
                                    isError -> StatusCritical
                                    isEntered -> PrimaryAccent
                                    else -> OutlineDark
                                },
                                CircleShape
                            )
                    )
                }
            }
        }

        // Keypad
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
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        if (key.digit.isEmpty()) {
                            Spacer(modifier = Modifier.size(72.dp))
                        } else {
                            TactileKeycap(
                                digit = key.digit,
                                sublabel = key.sublabel,
                                isEnabled = true,
                                onClick = {
                                    if (key.digit == "⌫") {
                                        if (pin.isNotEmpty()) {
                                            pin = pin.dropLast(1)
                                            isError = false
                                            feedbackMessage = null
                                        }
                                    } else if (pin.length < 4) {
                                        pin += key.digit
                                        isError = false
                                        feedbackMessage = null

                                        if (pin.length == 4) {
                                            if (authenticator.verify(pin)) {
                                                feedbackMessage = "Access Granted"
                                                onAuthenticated()
                                            } else {
                                                isError = true
                                                feedbackMessage = "Invalid Passkey"
                                                pin = ""
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
