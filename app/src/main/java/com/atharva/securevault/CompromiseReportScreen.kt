package com.atharva.securevault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharva.securevault.ui.theme.*

@Composable
fun CompromiseReportScreen(
    attempts: Int,
    responseTime: Long,
    onViewVault: () -> Unit,
    onLock: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeep)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AmberSurface)
                .border(1.dp, AmberBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = AmberLight,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Vault Compromised",
            color = WarmIvory,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Unlocked via automated test in $attempts attempts",
            color = SlateLight,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateSurface, shape = RoundedCornerShape(14.dp))
                .border(1.dp, SlateHighlight, RoundedCornerShape(14.dp))
                .padding(18.dp)
        ) {
            Column {
                SummaryRow(label = "Method", value = "Automated PIN Test")
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = OutlineVariantDark)
                Spacer(modifier = Modifier.height(12.dp))
                SummaryRow(label = "Attempts Used", value = "$attempts")
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = OutlineVariantDark)
                Spacer(modifier = Modifier.height(12.dp))
                SummaryRow(label = "Response Time", value = "${responseTime} ms")
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onViewVault,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AmberPrimary,
                contentColor = SlateDeep
            )
        ) {
            Text(
                text = "Open Vault",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onLock,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = SlateSurface,
                contentColor = WarmIvory
            ),
            border = BorderStroke(1.dp, SlateHighlight)
        ) {
            Text(
                text = "Lock Vault",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = SlateLight,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = WarmIvory,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
