package com.raasta.app.ui.plan

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.raasta.app.R
import com.raasta.app.map.LatLon
import com.raasta.app.planner.Journey
import com.raasta.app.safety.PlaceKind
import com.raasta.app.safety.SafeStop
import com.raasta.app.ui.theme.DangerRed

/** India's emergency number and the national helpline for women in distress. */
private const val EMERGENCY = "112"
private const val WOMEN_HELPLINE = "1091"

/**
 * Help during a trip. The call buttons open the phone's dialer with the number filled in, so nothing is
 * dialled until the rider presses call, and no phone permission is needed. Sharing hands the trip and a
 * map link to the system share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosSheet(
    journey: Journey,
    destination: String,
    at: LatLon?,
    stop: SafeStop?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val shareTitle = stringResource(R.string.sos_share_title)
    val shareText = stringResource(
        R.string.sos_share_text,
        destination,
        clock(journey.arriveMin),
        at?.let { "https://www.google.com/maps/search/?api=1&query=${it.lat},${it.lon}" }
            ?: stringResource(R.string.sos_location_unknown),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.sos_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.sos_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            DialButton(R.string.sos_call_emergency, EMERGENCY, filled = true) { dial(context, EMERGENCY) }
            DialButton(R.string.sos_call_women, WOMEN_HELPLINE, filled = false) { dial(context, WOMEN_HELPLINE) }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sos_share))
            }
            if (stop != null) {
                OutlinedButton(
                    onClick = {
                        uriHandler.openUri(
                            "https://www.google.com/maps/dir/?api=1&destination=${stop.at.lat},${stop.at.lon}&travelmode=walking",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sos_nearest, stringResource(placeKindLabel(stop.kind)), distance(stop.distanceM)))
                }
            } else {
                Text(
                    stringResource(R.string.sos_nearest_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DialButton(label: Int, number: String, filled: Boolean, onClick: () -> Unit) {
    val text = stringResource(label, number)
    if (filled) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = MaterialTheme.colorScheme.onError),
        ) { DialLabel(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { DialLabel(text) }
    }
}

@Composable
private fun DialLabel(text: String) {
    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, modifier = Modifier.padding(vertical = 4.dp))
}

private fun dial(context: android.content.Context, number: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
}

private fun placeKindLabel(kind: PlaceKind): Int = when (kind) {
    PlaceKind.SHOP -> R.string.place_shop
    PlaceKind.FOOD -> R.string.place_food
    PlaceKind.BANK -> R.string.place_bank
    PlaceKind.PHARMACY -> R.string.place_pharmacy
    PlaceKind.CLINIC -> R.string.place_clinic
    PlaceKind.CINEMA -> R.string.place_cinema
    PlaceKind.MARKETPLACE -> R.string.place_market
    PlaceKind.HOSPITAL -> R.string.place_hospital
    PlaceKind.FUEL -> R.string.place_fuel
    PlaceKind.POLICE -> R.string.place_police
    PlaceKind.ATM -> R.string.place_atm
    PlaceKind.TRANSIT -> R.string.place_transit
}
