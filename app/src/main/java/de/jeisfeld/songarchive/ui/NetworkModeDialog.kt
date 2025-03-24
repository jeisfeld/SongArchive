package de.jeisfeld.songarchive.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.network.ClientMode
import de.jeisfeld.songarchive.network.PeerConnectionMode

@Composable
fun NetworkModeDialog(
    context: Context,
    selectedNetworkMode: PeerConnectionMode,
    selectedClientMode: ClientMode,
    onModeSelected: (PeerConnectionMode, ClientMode) -> Unit,
    onDismiss: () -> Unit
) {
    val networkOptions = listOf(PeerConnectionMode.DISABLED, PeerConnectionMode.SERVER, PeerConnectionMode.CLIENT)
    var selectedNetworkOption by remember { mutableStateOf(selectedNetworkMode) }
    val clientOptions = listOf(ClientMode.LYRICS, ClientMode.CHORDS)
    var selectedClientOption by remember { mutableStateOf(selectedClientMode) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.network_mode),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_small)))

                networkOptions.forEachIndexed { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(R.dimen.spacing_small))
                            .clickable { selectedNetworkOption = option }
                    ) {
                        RadioButton(
                            selected = (selectedNetworkOption == option),
                            onClick = { selectedNetworkOption = option }
                        )
                        Text(
                            text = stringArrayResource(R.array.network_modes)[index],
                            modifier = Modifier.padding(start = dimensionResource(R.dimen.spacing_medium))
                        )
                    }
                }

                // 👇 Only show when Client is selected
                if (selectedNetworkOption == PeerConnectionMode.CLIENT) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_medium)))
                    Text(
                        text = stringResource(R.string.client_type),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_small)))

                    clientOptions.forEachIndexed { index, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedClientOption = option }
                        ) {
                            RadioButton(
                                selected = (selectedClientOption == option),
                                onClick = { selectedClientOption = option }
                            )
                            Text(
                                text = stringArrayResource(R.array.client_modes)[index],
                                modifier = Modifier.padding(start = dimensionResource(R.dimen.spacing_medium))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_heading_vertical)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(context.getString(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.spacing_medium)))
                    TextButton(onClick = {
                        onModeSelected(selectedNetworkOption, selectedClientOption)
                    }) {
                        Text(context.getString(R.string.ok))
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_heading_vertical)))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_heading_vertical)))
                Text(
                    text = context.getString(R.string.further_options),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_small)))
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text(context.getString(R.string.configure_battery_optimizations))
                }
            }
        }
    }
}
