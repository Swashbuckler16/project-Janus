package com.atharva.securevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharva.securevault.ui.theme.*

@Composable
fun VaultHomeScreen(
    attempts: Int,
    onLock: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeep)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Warm Emblem Container
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AmberSurface)
                .border(1.dp, AmberBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.LockOpen,
                contentDescription = null,
                tint = AmberLight,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "My Vault",
            color = WarmIvory,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Subtle Warm/Cool Status Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SlateSurface)
                .border(1.dp, SlateHighlight, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(AmberPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Hardware Protected • AES-256",
                color = SlateIce,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Saved Items",
            color = WarmCream,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        VaultAssetItem(
            icon = Icons.Outlined.VpnKey,
            title = "Saved Passwords",
            subtitle = "Account logins & passkeys",
            badge = "12 items"
        )

        Spacer(modifier = Modifier.height(10.dp))

        VaultAssetItem(
            icon = Icons.Outlined.Description,
            title = "Private Documents",
            subtitle = "Personal notes & recovery keys",
            badge = "5 files"
        )

        Spacer(modifier = Modifier.height(10.dp))

        VaultAssetItem(
            icon = Icons.Outlined.AccountBalance,
            title = "Cards & Banking",
            subtitle = "Saved payment methods",
            badge = "3 cards"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Warm Primary Action Button
        Button(
            onClick = onLock,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AmberPrimary,
                contentColor = SlateDeep
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Lock Vault",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun VaultAssetItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateSurface, shape = RoundedCornerShape(14.dp))
            .border(1.dp, SlateHighlight, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateElevated)
                    .border(1.dp, SlateHighlight, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AmberLight,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WarmIvory,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = SlateLight,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .background(AmberDeep, shape = RoundedCornerShape(8.dp))
                    .border(1.dp, AmberBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badge,
                    color = AmberSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
