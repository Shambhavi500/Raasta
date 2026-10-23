package com.raasta.app.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.map.LatLon
import com.raasta.app.map.MapCamera
import com.raasta.app.map.MapPin
import com.raasta.app.map.PinStyle
import com.raasta.app.map.RaastaMap
import com.raasta.app.map.distanceLabel
import com.raasta.app.map.distanceM
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.MapButton
import com.raasta.app.ui.common.MapSheet
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian

/** "My bus didn't come": pick the stop, the bus and where you're going, then see other ways. */
@Composable
fun RecoverScreen(vm: TripViewModel, onBack: () -> Unit, onFindOptions: () -> Unit) {
    val corridor = vm.corridor
    val points = corridor.recoveryPoints
    val origin = corridor.origin(vm.originId)
    val you = LatLon(origin.lat, origin.lon)
    var pointId by rememberSaveable {
        mutableStateOf(points.minBy { distanceM(you, LatLon(it.lat, it.lon)) }.id)
    }
    val point = corridor.recoveryPoint(pointId)
    var failedRoute by rememberSaveable(pointId) { mutableStateOf(point.routes.first()) }
    var destinationId by rememberSaveable { mutableStateOf(vm.destinationId ?: corridor.destinations.first().id) }

    val pins = points.map { stop ->
        MapPin(
            id = stop.id,
            at = LatLon(stop.lat, stop.lon),
            style = PinStyle.STOP,
            label = if (stop.id == pointId) stop.name else null,
            color = if (stop.id == pointId) ElectricLime else AttentionAmber,
        )
    } + MapPin("you", you, PinStyle.YOU)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * 0.6f
        Box(
            Modifier
                .fillMaxWidth()
                .height(maxHeight - sheetHeight + 28.dp),
        ) {
            RaastaMap(
                pins = pins,
                lines = emptyList(),
                camera = MapCamera.Fit(points.map { LatLon(it.lat, it.lon) } + you),
                modifier = Modifier.fillMaxSize(),
                onPinClick = { id -> if (points.any { it.id == id }) pointId = id },
            )
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
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.recover_where), style = MaterialTheme.typography.titleLarge)
                    points.forEach { stop ->
                        val selected = stop.id == pointId
                        val shape = RoundedCornerShape(16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .then(
                                    if (selected) Modifier
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .border(BorderStroke(2.dp, ElectricLime), shape)
                                    else Modifier,
                                )
                                .clickable { pointId = stop.id }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AttentionAmber),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = Obsidian, modifier = Modifier.size(20.dp))
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(stop.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.recover_stop_subtitle,
                                        stop.routes.joinToString(", ") { displayRoute(it) },
                                        distanceLabel(distanceM(you, LatLon(stop.lat, stop.lon))),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.recover_failed_bus), style = MaterialTheme.typography.titleMedium)
                    ChipRow(
                        options = point.routes.map { it to stringResource(R.string.recover_bus_route, displayRoute(it)) },
                        selected = failedRoute,
                        onSelect = { failedRoute = it },
                    )
                    Text(stringResource(R.string.recover_going_to), style = MaterialTheme.typography.titleMedium)
                    ChipRow(
                        options = corridor.destinations.map { it.id to it.name },
                        selected = destinationId,
                        onSelect = { destinationId = it },
                    )
                }
                Button(
                    onClick = {
                        vm.startRecovery(pointId, failedRoute)
                        vm.chooseDestination(destinationId)
                        onFindOptions()
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
                ) {
                    Text(stringResource(R.string.recover_find), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(id) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricLime,
                    selectedLabelColor = Obsidian,
                ),
            )
        }
    }
}
