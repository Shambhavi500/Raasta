package com.raasta.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.auth.DemoAuth
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
import com.raasta.app.ui.common.TimePill
import com.raasta.app.ui.plan.LeaveTimeDialog
import com.raasta.app.ui.plan.SourcesList
import com.raasta.app.ui.plan.clock
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.MetroAqua
import com.raasta.app.ui.theme.Obsidian

/** Home: a map and the journey search — from, to and when. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: TripViewModel,
    phone: String,
    onEditFrom: () -> Unit,
    onEditTo: () -> Unit,
    onContinue: () -> Unit,
    onReports: () -> Unit,
    onSignOut: () -> Unit,
) {
    val corridor = vm.corridor
    val origin = corridor.origin(vm.originId)
    val from = LatLon(origin.lat, origin.lon)
    val destination = vm.destinationId?.let { corridor.destination(it) }
    var recenter by remember { mutableIntStateOf(0) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }

    val pins = remember(from, destination) {
        buildList {
            corridor.recoveryPoints.forEach { add(MapPin("stop-${it.id}", LatLon(it.lat, it.lon), PinStyle.STOP)) }
            corridor.metro.stations.forEach { add(MapPin("st-${it.name}", LatLon(it.lat, it.lon), PinStyle.STATION, color = MetroAqua)) }
            add(MapPin("from", from, PinStyle.YOU))
            destination?.let { add(MapPin("to", LatLon(it.lat, it.lon), PinStyle.DROP, label = it.name.substringBefore(","))) }
        }
    }
    val camera = destination?.let { MapCamera.Fit(listOf(from, LatLon(it.lat, it.lon)), recenter) }
        ?: MapCamera.Center(from, 15.0, recenter)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        RaastaMap(
            pins = pins,
            lines = emptyList(),
            camera = camera,
            modifier = Modifier.fillMaxSize(),
            centerOffsetY = -maxHeight * 0.24f,
        )

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapButton(Icons.Filled.Person, stringResource(R.string.home_account), onClick = { showAccount = true })
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.raasta_logo),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = ElectricLime,
                    )
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            MapButton(
                Icons.Filled.MyLocation,
                stringResource(R.string.map_recenter),
                onClick = { recenter++ },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp, bottom = 12.dp),
            )
            MapSheet {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge)
                    JourneyFields(
                        from = origin.name,
                        to = destination?.name,
                        onFrom = onEditFrom,
                        onTo = onEditTo,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.home_when),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TimePill(
                            vm.departAt?.let { stringResource(R.string.depart_at_time, clock(it)) }
                                ?: stringResource(R.string.leave_now_short),
                            onClick = { showTime = true },
                        )
                    }
                    Button(
                        onClick = onContinue,
                        enabled = destination != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
                    ) {
                        Text(
                            stringResource(if (destination != null) R.string.home_continue else R.string.home_choose_destination),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        stringResource(R.string.home_places),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    corridor.destinations.forEach { place ->
                        PlaceRow(
                            title = place.name,
                            subtitle = stringResource(
                                R.string.home_place_subtitle,
                                place.metroStation,
                                distanceLabel(distanceM(from, LatLon(place.lat, place.lon))),
                            ),
                            onClick = { vm.chooseDestination(place.id) },
                        )
                    }
                }
            }
        }
    }

    if (showTime) {
        LeaveTimeDialog(
            initialMinute = vm.startMinute,
            onPick = { vm.departAt = it; showTime = false },
            onDismiss = { showTime = false },
        )
    }

    if (showAccount) {
        ModalBottomSheet(
            onDismissRequest = { showAccount = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.home_account), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.home_signed_in_as, DemoAuth.maskPhone(phone)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.plan_corridor_note, corridor.corridor, corridor.generatedOn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showAccount = false; onReports() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Flag, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reports_title))
                }
                OutlinedButton(onClick = { showAccount = false; onSignOut() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_sign_out))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.sources_header), style = MaterialTheme.typography.titleMedium)
                SourcesList(corridor)
            }
        }
    }
}

/** The starting point and destination, each tappable to change it. */
@Composable
private fun JourneyFields(from: String, to: String?, onFrom: () -> Unit, onTo: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        FieldRow(stringResource(R.string.plan_from), from, filled = true, onClick = onFrom, start = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 44.dp))
        FieldRow(stringResource(R.string.plan_to), to ?: stringResource(R.string.home_to_hint), filled = to != null, onClick = onTo, start = false)
    }
}

@Composable
private fun FieldRow(label: String, value: String, filled: Boolean, onClick: () -> Unit, start: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(if (start) CircleShape else MaterialTheme.shapes.extraSmall)
                .background(if (start) MaterialTheme.colorScheme.onSurface else ElectricLime),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PlaceRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
