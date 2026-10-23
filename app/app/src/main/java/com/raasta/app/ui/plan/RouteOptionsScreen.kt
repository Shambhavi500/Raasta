package com.raasta.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.map.MapCamera
import com.raasta.app.map.RaastaMap
import com.raasta.app.planner.Journey
import com.raasta.app.planner.Priority
import com.raasta.app.planner.parseClock
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.MapButton
import com.raasta.app.ui.common.MapSheet
import com.raasta.app.ui.common.TimePill
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian

/** Route choice over a map, like choosing a ride: options, price, time, then confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteOptionsScreen(vm: TripViewModel, onBack: () -> Unit, onStart: (Journey) -> Unit) {
    val result = vm.plan ?: return
    val destinationName = vm.destinationId?.let { vm.corridor.destination(it).name }.orEmpty()
    // An option that means waiting hours for the first bus is not a real choice right now.
    val (ranked, notRunning) = result.ranked.partition { it.journey.waitMin <= MAX_WAIT_MIN }
    val notRunningCount = notRunning.size + result.unavailable.size
    val selected = ranked.getOrNull(vm.selectedIndex)?.journey ?: ranked.firstOrNull()?.journey
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(ranked) { ranked.forEach { vm.loadShapes(it.journey) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * 0.58f
        Box(
            Modifier
                .fillMaxWidth()
                .height(maxHeight - sheetHeight + 28.dp),
        ) {
            if (selected != null) {
                RaastaMap(
                    pins = journeyPins(vm, selected, pickupLabel = vm.fromName.substringBefore(","), dropLabel = destinationName.substringBefore(",")),
                    lines = journeyLines(vm, selected),
                    camera = MapCamera.Fit(journeyPoints(vm, selected)),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                RaastaMap(pins = emptyList(), lines = emptyList(), camera = MapCamera.Fit(listOf(vm.fromPoint)), modifier = Modifier.fillMaxSize())
            }
            MapButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.action_back),
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp),
            )
        }

        MapSheet(
            Modifier
                .align(Alignment.BottomCenter)
                .height(sheetHeight),
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (vm.recovery != null) R.string.options_title_recover else R.string.options_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(R.string.options_route, vm.fromName, destinationName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TimePill(
                        vm.departAt?.let { clock(it) } ?: stringResource(R.string.leave_now_short),
                        onClick = { showTime = true },
                    )
                }
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Priority.entries.forEach { priority ->
                        FilterChip(
                            selected = vm.priority == priority,
                            onClick = { vm.priority = priority; vm.selectedIndex = 0 },
                            label = { Text(stringResource(priorityLabel(priority))) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricLime,
                                selectedLabelColor = Obsidian,
                            ),
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    vm.recovery?.let { rescue ->
                        if (ranked.isNotEmpty()) {
                            item { RecommendationBanner(result, rescue.failedRoute, Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                        }
                    }
                    itemsIndexed(ranked) { index, option ->
                        OptionRow(
                            ranked = option,
                            selected = option.journey == selected,
                            onClick = { vm.selectedIndex = index },
                        )
                    }
                    if (notRunningCount > 0) {
                        item {
                            val service = vm.corridor.metro.service
                            UnavailableNote(
                                notRunningCount,
                                clock(parseClock(service.firstTrain)),
                                clock(parseClock(service.lastTrainLatest)),
                                onChangeTime = { showTime = true },
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showDetails = true }, enabled = selected != null) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.options_details))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { selected?.let(onStart) },
                        enabled = selected != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
                    ) {
                        Text(
                            selected?.let { stringResource(R.string.options_start, stringResource(R.string.journey_fare, it.fare)) }
                                ?: stringResource(R.string.plan_no_results),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showDetails && selected != null) {
        ModalBottomSheet(
            onDismissRequest = { showDetails = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    journeyTitle(selected.kind, selected.busRoute, selected.retriesFailedRoute),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.journey_arrive, clock(selected.departMin), clock(selected.arriveMin)) +
                        " · " + stringResource(R.string.journey_minutes, selected.totalMin),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                JourneySteps(selected)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.sources_header), style = MaterialTheme.typography.titleMedium)
                SourcesList(vm.corridor)
            }
        }
    }

    if (showTime) {
        LeaveTimeDialog(
            initialMinute = vm.startMinute,
            onPick = { vm.departAt = it; vm.selectedIndex = 0; showTime = false },
            onDismiss = { showTime = false },
        )
    }
}

@Composable
private fun UnavailableNote(count: Int, opensAt: String, closesBy: String, onChangeTime: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(8.dp)
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
            Text(
                stringResource(R.string.options_metro_closed, count),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.options_metro_hours, opensAt, closesBy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChangeTime) { Text(stringResource(R.string.options_change_time)) }
    }
}

private const val MAX_WAIT_MIN = 90
