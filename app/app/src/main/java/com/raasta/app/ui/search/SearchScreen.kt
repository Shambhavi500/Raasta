package com.raasta.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.map.LatLon
import com.raasta.app.map.distanceLabel
import com.raasta.app.map.distanceM
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.TimePill
import com.raasta.app.ui.home.PlaceRow
import com.raasta.app.ui.plan.LeaveTimeDialog
import com.raasta.app.ui.plan.clock
import com.raasta.app.ui.theme.ElectricLime

private data class Suggestion(val id: String, val name: String, val subtitle: String, val keywords: String)

/** Pickup and drop fields with live suggestions, like a ride-hailing search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: TripViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val corridor = vm.corridor
    var pickupQuery by rememberSaveable { mutableStateOf("") }
    var dropQuery by rememberSaveable { mutableStateOf("") }
    var editingPickup by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    val dropFocus = remember { FocusRequester() }
    val origin = corridor.origin(vm.originId)
    val here = LatLon(origin.lat, origin.lon)

    val pickups = corridor.origins.map {
        Suggestion(it.id, it.name, stringResource(R.string.search_pickup_subtitle), it.name)
    }
    val drops = corridor.destinations.map {
        Suggestion(
            it.id,
            it.name,
            stringResource(R.string.home_place_subtitle, it.metroStation, distanceLabel(distanceM(here, LatLon(it.lat, it.lon)))),
            "${it.name} ${it.metroStation} Pune",
        )
    }
    val query = if (editingPickup) pickupQuery else dropQuery
    val shown = (if (editingPickup) pickups else drops).filter { suggestion ->
        query.isBlank() || query.trim().split(" ").all { word -> suggestion.keywords.contains(word, ignoreCase = true) }
    }

    LaunchedEffect(Unit) { dropFocus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteDots(Modifier.padding(start = 18.dp, end = 6.dp))
                Column(Modifier.weight(1f)) {
                    SearchField(
                        value = pickupQuery,
                        onValueChange = { pickupQuery = it },
                        placeholder = origin.name,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) editingPickup = true },
                    )
                    SearchField(
                        value = dropQuery,
                        onValueChange = { dropQuery = it },
                        placeholder = stringResource(R.string.home_where_to),
                        modifier = Modifier
                            .focusRequester(dropFocus)
                            .onFocusChanged { if (it.isFocused) editingPickup = false },
                        onDone = { shown.singleOrNull()?.let { vm.chooseDestination(it.id); onDone() } },
                    )
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                TimePill(
                    vm.departAt?.let { stringResource(R.string.leave_at_time, clock(it)) } ?: stringResource(R.string.leave_now_short),
                    onClick = { showTime = true },
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (editingPickup) {
                    item {
                        Row(
                            Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = ElectricLime)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.search_pickup_hint), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                items(shown, key = { it.id }) { suggestion ->
                    PlaceRow(
                        title = suggestion.name,
                        subtitle = suggestion.subtitle,
                        onClick = {
                            if (editingPickup) {
                                vm.originId = suggestion.id
                                pickupQuery = ""
                                editingPickup = false
                                dropFocus.requestFocus()
                            } else {
                                vm.chooseDestination(suggestion.id)
                                onDone()
                            }
                        },
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.search_no_match, corridor.corridor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
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
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, maxLines = 1) },
        singleLine = true,
        shape = RectangleShape,
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear)) } }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = ElectricLime,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Pickup dot, connector line and drop square, as in ride-hailing apps. */
@Composable
private fun RouteDots(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface),
        )
        Box(
            Modifier
                .width(2.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Box(
            Modifier
                .size(10.dp)
                .background(ElectricLime),
        )
    }
}
