package de.jeisfeld.songarchive.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.network.ClientMode
import de.jeisfeld.songarchive.network.DefaultNetworkConnection
import de.jeisfeld.songarchive.network.PeerConnectionMode
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.network.isNearbyConnectionPossible
import de.jeisfeld.songarchive.ui.favoritelists.FavoriteListsActivity
import de.jeisfeld.songarchive.ui.settings.SettingsViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors

@Composable
fun MainDropdownMenu(
    showMenu: Boolean,
    onDismissRequest: () -> Unit,
    context: Context,
    onNetworkConnectionRequested: (PeerConnectionMode, ClientMode) -> Unit,
    onShareText: () -> Unit,
    onSync: () -> Unit,
    onAddSong: () -> Unit,
) {
    var showNetworkMenu by remember { mutableStateOf(false) }
    val settingsViewModel: SettingsViewModel = viewModel()
    val defaultConnection by settingsViewModel.defaultNetworkConnection.collectAsState()

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.background(AppColors.BackgroundShaded)
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(id = R.string.sync),
                    color = AppColors.TextColor
                )
            },
            onClick = {
                onDismissRequest()
                onSync()
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sync),
                    contentDescription = "Sync",
                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                )
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(id = R.string.add_song),
                    color = AppColors.TextColor
                )
            },
            onClick = {
                onDismissRequest()
                onAddSong()
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = stringResource(id = R.string.add_song),
                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                )
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(id = R.string.favorite_lists),
                    color = AppColors.TextColor
                )
            },
            onClick = {
                onDismissRequest()
                context.startActivity(android.content.Intent(context, FavoriteListsActivity::class.java))
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_list),
                    contentDescription = stringResource(id = R.string.favorite_lists),
                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                )
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(id = R.string.settings),
                    color = AppColors.TextColor
                )
            },
            onClick = {
                onDismissRequest()
                context.startActivity(android.content.Intent(context, de.jeisfeld.songarchive.ui.settings.SettingsActivity::class.java))
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = stringResource(id = R.string.settings),
                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                )
            }
        )
        if (isNearbyConnectionPossible(context)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (DefaultNetworkConnection.fromId(defaultConnection) == DefaultNetworkConnection.NONE) {
                                showNetworkMenu = true
                            } else {
                                onDismissRequest()
                                val defaultMode = DefaultNetworkConnection.fromId(defaultConnection)
                                val networkMode = when {
                                    PeerConnectionViewModel.peerConnectionMode != PeerConnectionMode.DISABLED -> PeerConnectionMode.DISABLED
                                    defaultMode == DefaultNetworkConnection.SERVER -> PeerConnectionMode.SERVER
                                    else -> PeerConnectionMode.CLIENT
                                }
                                val clientMode = when (defaultMode) {
                                    DefaultNetworkConnection.CLIENT_LYRICS_BW -> ClientMode.LYRICS_BW
                                    DefaultNetworkConnection.CLIENT_LYRICS_WB -> ClientMode.LYRICS_WB
                                    DefaultNetworkConnection.CLIENT_CHORDS -> ClientMode.CHORDS
                                    else -> ClientMode.LYRICS_BS
                                }
                                onNetworkConnectionRequested(networkMode, clientMode)
                            }
                        },
                        onLongClick = { showNetworkMenu = true }
                    )
                    .padding(vertical = dimensionResource(id = R.dimen.spacing_vertical_medium), horizontal = 12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_wifi),
                    contentDescription = "Wi-Fi Transfer",
                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(id = R.string.network_connection),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextColor
                )
            }
        }
        if (showNetworkMenu) {
            NetworkModeMenu(
                expanded = true,
                context = context,
                selectedNetworkMode = PeerConnectionViewModel.peerConnectionMode,
                selectedClientMode = PeerConnectionViewModel.clientMode,
                onModeSelected = { networkMode, clientMode ->
                    onDismissRequest()
                    showNetworkMenu = false
                    onNetworkConnectionRequested(networkMode, clientMode)
                },
                onDismiss = { showNetworkMenu = false }
            )
        }
        if (PeerConnectionViewModel.peerConnectionMode == PeerConnectionMode.SERVER && PeerConnectionViewModel.connectedDevices > 0) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(id = R.string.share_text),
                        color = AppColors.TextColor
                    )
                },
                onClick = {
                    onDismissRequest()
                    onShareText()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_send),
                        contentDescription = "Send Lyrics",
                        modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                    )
                }
            )
        }
    }
}
