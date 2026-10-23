package com.raasta.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.planner.Journey
import com.raasta.app.ui.SafetyStatus
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.MapSheet
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian

/** The chosen route in full: every step, what is uncertain, where numbers come from; then start. */
@Composable
fun JourneyDetailsScreen(vm: TripViewModel, onBack: () -> Unit, onStart: (Journey) -> Unit) {
    val options = vm.options
    val option = options.getOrNull(vm.selectedIndex) ?: options.firstOrNull() ?: return
    val journey = vm.chosen(option.journey)
    val safetyReady = vm.safetyStatus as? SafetyStatus.Ready

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * 0.64f
        RouteMap(
            vm,
            option.journey,
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
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        journeyTitle(journey.kind, journey.busRoute, journey.retriesFailedRoute),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.journey_arrive, clock(journey.departMin), clock(journey.arriveMin)) +
                            " · " + stringResource(R.string.journey_minutes, journey.totalMin) +
                            " · " + stringResource(R.string.journey_fare, journey.fare),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SectionTitle(
                        stringResource(if (option == options.firstOrNull()) R.string.details_why_recommended else R.string.details_why_this),
                    )
                    TradeoffList(vm.tradeoffs(option))
                    if (vm.preferences.safetyAware) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SectionTitle(stringResource(R.string.details_streets))
                        SafetyRoutePicker(vm, option.journey)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SectionTitle(stringResource(R.string.details_steps))
                    JourneySteps(journey)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SectionTitle(stringResource(R.string.sources_header))
                    SourcesList(vm.corridor)
                    safetyReady?.takeIf { vm.preferences.safetyAware }?.let { ready ->
                        SectionTitle(stringResource(R.string.safety_sources_header))
                        SourcesList(ready.model.graph.meta.sources + listOfNotNull(ready.crime?.data?.source))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = { onStart(journey) },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
                ) {
                    Text(stringResource(R.string.details_start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
