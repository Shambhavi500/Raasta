package com.raasta.app.ui.prefs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.planner.Goal
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.plan.clock
import com.raasta.app.ui.plan.goalDescription
import com.raasta.app.ui.plan.goalLabel
import com.raasta.app.safety.SafetyModel
import com.raasta.app.safety.hourOf
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian

/** Travel preferences for this journey: what matters most, safety-aware routing and walking. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(vm: TripViewModel, onBack: () -> Unit, onFindRoutes: () -> Unit) {
    // Leaving at night with no saved choice: start with safety-aware routing on.
    var preferences by remember {
        val start = vm.preferences
        val night = SafetyModel.isNight(hourOf(vm.startMinute))
        mutableStateOf(if (night && !vm.hasSavedPreferences) start.copy(safetyAware = true) else start)
    }
    val leaving = vm.departAt?.let { stringResource(R.string.depart_at_time, clock(it)) }
        ?: stringResource(R.string.leave_now_short)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prefs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { vm.findRoutes(preferences); onFindRoutes() },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
            ) {
                Text(stringResource(R.string.prefs_find_routes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.prefs_journey, vm.fromName, vm.destinationName, leaving),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.hasSavedPreferences) {
                Text(
                    stringResource(R.string.prefs_last_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricLime,
                )
            }
            Text(stringResource(R.string.prefs_priorities), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.prefs_priorities_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Goal.entries.forEach { goal ->
                GoalCard(
                    icon = goalIcon(goal),
                    title = stringResource(goalLabel(goal)),
                    body = stringResource(goalDescription(goal)),
                    selected = goal in preferences.goals,
                    onClick = {
                        val goals = if (goal in preferences.goals) preferences.goals - goal else preferences.goals + goal
                        preferences = preferences.copy(goals = goals)
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
            SwitchRow(
                icon = Icons.Filled.Shield,
                title = stringResource(R.string.prefs_safety),
                body = stringResource(R.string.prefs_safety_body),
                checked = preferences.safetyAware,
                onChange = { preferences = preferences.copy(safetyAware = it) },
            )
            SwitchRow(
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                title = stringResource(R.string.prefs_walks),
                body = stringResource(R.string.prefs_walks_body),
                checked = preferences.avoidLongWalks,
                onChange = { preferences = preferences.copy(avoidLongWalks = it) },
            )
        }
    }
}

private fun goalIcon(goal: Goal): ImageVector = when (goal) {
    Goal.TIME -> Icons.Filled.Timer
    Goal.COST -> Icons.Filled.CurrencyRupee
    Goal.CONVENIENCE -> Icons.Filled.EventSeat
    Goal.ACCESSIBILITY -> Icons.AutoMirrored.Filled.Accessible
}

@Composable
private fun GoalCard(icon: ImageVector, title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
            .border(
                if (selected) BorderStroke(2.dp, ElectricLime) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) ElectricLime else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Obsidian else MaterialTheme.colorScheme.onSurface)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ElectricLime)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = ElectricLime, checkedThumbColor = Obsidian),
        )
    }
}
