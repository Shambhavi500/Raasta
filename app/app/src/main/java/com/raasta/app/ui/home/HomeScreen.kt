package com.raasta.app.ui.home

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.MetroAqua
import com.raasta.app.ui.theme.Obsidian

/** Map-first home: where you are, "Where to?", saved places and the missed-bus shortcut. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: TripViewModel,
    phone: String,
    onSearch: () -> Unit,
    onDestination: (String) -> Unit,
    onBusDidNotCome: () -> Unit,
    onSignOut: () -> Unit,
) {
    val corridor = vm.corridor
    val origin = corridor.origin(vm.originId)
    val you = LatLon(origin.lat, origin.lon)
    var recenter by remember { mutableIntStateOf(0) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }

    val pins = remember(you) {
        buildList {
            corridor.recoveryPoints.forEach { add(MapPin("stop-${it.id}", LatLon(it.lat, it.lon), PinStyle.STOP)) }
            corridor.metro.stations.forEach { add(MapPin("st-${it.name}", LatLon(it.lat, it.lon), PinStyle.STATION, color = MetroAqua)) }
            add(MapPin("you", you, PinStyle.YOU))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        RaastaMap(
            pins = pins,
            lines = emptyList(),
            camera = MapCamera.Center(you, 15.0, recenter),
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
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = ElectricLime,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
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
                    Text(
                        stringResource(R.string.home_you_are_at, origin.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WhereToBar(
                        timeText = vm.departAt?.let { stringResource(R.string.leave_at_time, clock(it)) }
                            ?: stringResource(R.string.leave_now_short),
                        onSearch = onSearch,
                        onTime = { showTime = true },
                    )
                    corridor.destinations.forEach { destination ->
                        PlaceRow(
                            title = destination.name,
                            subtitle = stringResource(
                                R.string.home_place_subtitle,
                                destination.metroStation,
                                distanceLabel(distanceM(you, LatLon(destination.lat, destination.lon))),
                            ),
                            onClick = { onDestination(destination.id) },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    BusDidNotComeCard(onClick = onBusDidNotCome)
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

@Composable
private fun WhereToBar(timeText: String, onSearch: () -> Unit, onTime: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onSearch)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(26.dp))
        Text(
            stringResource(R.string.home_where_to),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        TimePill(timeText, onClick = onTime)
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

@Composable
private fun BusDidNotComeCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(AttentionAmber.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AttentionAmber),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Obsidian)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(stringResource(R.string.home_recover_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_recover_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}
