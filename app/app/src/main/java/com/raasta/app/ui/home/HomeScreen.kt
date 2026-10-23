package com.raasta.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.auth.DemoAuth
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian
import com.raasta.app.ui.theme.RaastaTheme

/** Home shows two large actions, one for each of Raasta's core jobs. */
@Composable
fun HomeScreen(
    phone: String,
    onPlanTrip: () -> Unit,
    onConnectionFailed: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.home_signed_in_as, DemoAuth.maskPhone(phone)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSignOut) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = stringResource(R.string.home_sign_out),
                    )
                }
            }
            Text(
                text = stringResource(R.string.home_question),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            ActionCard(
                title = stringResource(R.string.home_plan_title),
                subtitle = stringResource(R.string.home_plan_subtitle),
                icon = Icons.Filled.Place,
                containerColor = ElectricLime,
                contentColor = Obsidian,
                iconBoxColor = Obsidian,
                iconColor = ElectricLime,
                onClick = onPlanTrip,
            )
            ActionCard(
                title = stringResource(R.string.home_recover_title),
                subtitle = stringResource(R.string.home_recover_subtitle),
                icon = Icons.Filled.Warning,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconBoxColor = ElectricLime,
                iconColor = Obsidian,
                border = BorderStroke(1.dp, AttentionAmber.copy(alpha = 0.6f)),
                striped = true,
                onClick = onConnectionFailed,
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    iconBoxColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    border: BorderStroke? = null,
    striped: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        border = border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (striped) Modifier.diagonalStripes(contentColor.copy(alpha = 0.04f)) else Modifier)
                .heightIn(min = 200.dp)
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(iconBoxColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.75f),
            )
        }
    }
}

/** Subtle diagonal stripe texture for the dark action card. */
private fun Modifier.diagonalStripes(color: Color): Modifier = drawBehind {
    val step = 18.dp.toPx()
    val stroke = 6.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stroke)
        x += step
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RaastaTheme {
        HomeScreen(phone = "9876543210", onPlanTrip = {}, onConnectionFailed = {}, onSignOut = {})
    }
}
