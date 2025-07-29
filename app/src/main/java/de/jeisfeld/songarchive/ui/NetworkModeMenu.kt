package de.jeisfeld.songarchive.ui

import android.content.Context
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.network.ClientMode
import de.jeisfeld.songarchive.network.PeerConnectionMode
import de.jeisfeld.songarchive.ui.theme.AppColors

@Composable
fun NetworkModeMenu(
    expanded: Boolean,
    context: Context,
    selectedNetworkMode: PeerConnectionMode,
    selectedClientMode: ClientMode,
    onModeSelected: (PeerConnectionMode, ClientMode) -> Unit,
    onDismiss: () -> Unit
) {
    val networkOptions = listOf(PeerConnectionMode.DISABLED, PeerConnectionMode.SERVER, PeerConnectionMode.CLIENT)
    var selectedNetworkOption by remember { mutableStateOf(selectedNetworkMode) }
    val clientOptions = listOf(ClientMode.LYRICS_BS, ClientMode.LYRICS_BW, ClientMode.LYRICS_WB, ClientMode.CHORDS)
    var selectedClientOption by remember { mutableStateOf(selectedClientMode) }

    val widthActions = dimensionResource(R.dimen.width_actions)
    val minHeight = dimensionResource(R.dimen.network_menu_min_height)
    val verticalOffset = dimensionResource(R.dimen.network_menu_vertical_offset)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(-widthActions, verticalOffset),
        modifier = Modifier.background(AppColors.BackgroundShaded)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 4.dp,
            color = AppColors.BackgroundShaded
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .heightIn(min = minHeight)
            ) {
                Text(
                    text = stringResource(R.string.network_mode),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.TextColor
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
                            onClick = { selectedNetworkOption = option },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AppColors.TextColor,
                                unselectedColor = AppColors.TextColorLight
                            )
                        )
                        Text(
                            text = stringArrayResource(R.array.network_modes)[index],
                            modifier = Modifier.padding(start = dimensionResource(R.dimen.spacing_medium)),
                            color = AppColors.TextColor
                        )
                    }
                }

                // 👇 Only show when Client is selected
                if (selectedNetworkOption == PeerConnectionMode.CLIENT) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_medium)))
                    Text(
                        text = stringResource(R.string.client_type),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextColor
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
                                onClick = { selectedClientOption = option },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AppColors.TextColor,
                                    unselectedColor = AppColors.TextColorLight
                                )
                            )
                            Text(
                                text = stringArrayResource(R.array.client_modes)[index],
                                modifier = Modifier.padding(start = dimensionResource(R.dimen.spacing_medium)),
                                color = AppColors.TextColor
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

            }
        }
    }
}
