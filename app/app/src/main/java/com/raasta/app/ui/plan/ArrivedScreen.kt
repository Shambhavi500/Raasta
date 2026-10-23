package com.raasta.app.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.map.MapCamera
import com.raasta.app.map.RaastaMap
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.MapSheet
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian

/** Trip summary after arriving: time, fare per ride, and a quick rating. */
@Composable
fun ArrivedScreen(vm: TripViewModel, onDone: () -> Unit) {
    val journey = vm.trip ?: return
    val destinationName = vm.destinationId?.let { vm.corridor.destination(it).name }.orEmpty()
    var rating by rememberSaveable { mutableIntStateOf(0) }
    val finish = { vm.endTrip(); onDone() }
    BackHandler(onBack = finish)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMax = maxHeight * 0.66f
        RaastaMap(
            pins = journeyPins(vm, journey, pickupLabel = null, dropLabel = destinationName.substringBefore(",")),
            lines = journeyLines(vm, journey),
            camera = MapCamera.Fit(journeyPoints(vm, journey)),
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight - sheetMax + 120.dp),
        )
        MapSheet(
            Modifier
                .align(Alignment.BottomCenter)
                .heightIn(max = sheetMax),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ElectricLime),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Obsidian, modifier = Modifier.size(32.dp))
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.arrived_title), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.arrived_at, destinationName, clock(journey.arriveMin)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    SummaryStat(stringResource(R.string.journey_minutes, journey.arriveMin - vm.tripStartMinute), stringResource(R.string.arrived_time), Modifier.weight(1f))
                    SummaryStat(stringResource(R.string.journey_fare, journey.fare), stringResource(R.string.arrived_fare), Modifier.weight(1f))
                    SummaryStat(journey.transfers.toString(), stringResource(R.string.arrived_changes), Modifier.weight(1f))
                }
                if (vm.reroutes > 0) {
                    Text(
                        stringResource(R.string.arrived_rerouted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricLime,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                journey.fareLegs().forEach { leg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ModeBadge(leg.mode, size = 28.dp)
                        Text(
                            legTitle(leg),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        )
                        Text(stringResource(R.string.journey_fare, leg.fare), style = MaterialTheme.typography.titleMedium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.arrived_rate), style = MaterialTheme.typography.titleMedium)
                Row {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                if (star <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(R.string.arrived_star, star),
                                tint = if (star <= rating) ElectricLime else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                if (rating > 0) {
                    Text(
                        stringResource(R.string.arrived_thanks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = finish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
                ) {
                    Text(stringResource(R.string.arrived_done), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
