package com.raasta.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.planner.parseClock
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.MapSheet
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian

/** The recommended route and the alternatives, each with its advantages and disadvantages. */
@Composable
fun CompareRoutesScreen(vm: TripViewModel, onBack: () -> Unit, onEditPreferences: () -> Unit, onDetails: () -> Unit) {
    val options = vm.options
    val selected = options.getOrNull(vm.selectedIndex) ?: options.firstOrNull()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * 0.62f
        RouteMap(
            vm,
            selected?.journey,
            onBack = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight - sheetHeight + 28.dp),
        )

        MapSheet(
            Modifier
                .align(Alignment.BottomCenter)
                .height(sheetHeight),
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.compare_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.options_route, vm.fromName, vm.destinationName) + " · " + clock(vm.searchMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            preferencesText(vm.preferences),
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricLime,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onEditPreferences) { Text(stringResource(R.string.compare_change_prefs)) }
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(options) { index, option ->
                        if (index == 1) {
                            SectionTitle(
                                stringResource(R.string.compare_alternatives),
                                Modifier.padding(start = 6.dp, top = 6.dp),
                            )
                        }
                        RouteCard(
                            option = option,
                            tradeoffs = vm.tradeoffs(option),
                            recommended = index == 0,
                            selected = option == selected,
                            onClick = { vm.selectedIndex = index },
                            safetyScore = vm.safetyScore(option.journey),
                        )
                    }
                    if (vm.notRunningCount > 0) {
                        item {
                            val service = vm.corridor.metro.service
                            UnavailableNote(
                                vm.notRunningCount,
                                clock(parseClock(service.firstTrain)),
                                clock(parseClock(service.lastTrainLatest)),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = onDetails,
                    enabled = selected != null,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
                ) {
                    Text(
                        stringResource(if (selected != null) R.string.compare_view_details else R.string.plan_no_results),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnavailableNote(count: Int, opensAt: String, closesBy: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(AttentionAmber.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.NightsStay, contentDescription = null, tint = AttentionAmber)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(stringResource(R.string.options_metro_closed, count), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.options_metro_hours, opensAt, closesBy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
