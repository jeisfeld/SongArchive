package de.jeisfeld.songarchive.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
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
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onShareText: () -> Unit,
    onSync: () -> Unit,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (DefaultNetworkConnection.fromId(defaultConnection) == DefaultNetworkConnection.NONE) {
                                showNetworkMenu = true
                            } else {
                                onDismissRequest()
                                if (PeerConnectionViewModel.peerConnectionMode == PeerConnectionMode.DISABLED) {
                                    when (DefaultNetworkConnection.fromId(defaultConnection)) {
                                        DefaultNetworkConnection.SERVER -> {
                                            PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.SERVER
                                        }
                                        DefaultNetworkConnection.CLIENT_LYRICS_BS -> {
                                            PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.CLIENT
                                            PeerConnectionViewModel.clientMode = ClientMode.LYRICS_BS
                                        }
                                        DefaultNetworkConnection.CLIENT_LYRICS_BW -> {
                                            PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.CLIENT
                                            PeerConnectionViewModel.clientMode = ClientMode.LYRICS_BW
                                        }
                                        DefaultNetworkConnection.CLIENT_LYRICS_WB -> {
                                            PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.CLIENT
                                            PeerConnectionViewModel.clientMode = ClientMode.LYRICS_WB
                                        }
                                        DefaultNetworkConnection.CLIENT_CHORDS -> {
                                            PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.CLIENT
                                            PeerConnectionViewModel.clientMode = ClientMode.CHORDS
                                        }
                                        else -> {}
                                    }
                                } else {
                                    PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.DISABLED
                                }
                                PeerConnectionViewModel.startPeerConnectionService(context)
                            }
                        },
                        onLongClick = { showNetworkMenu = true }
                    )
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(id = R.string.network_connection),
                            color = AppColors.TextColor
                        )
                    },
                    onClick = {},
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_wifi),
                            contentDescription = "Wi-Fi Transfer",
                            modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                        )
                    }
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
                    val isPeerConnectionModeChanged = networkMode != PeerConnectionViewModel.peerConnectionMode
                    PeerConnectionViewModel.peerConnectionMode = networkMode
                    PeerConnectionViewModel.clientMode = clientMode
                    onDismissRequest()
                    showNetworkMenu = false
                    val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            Manifest.permission.NEARBY_WIFI_DEVICES,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_ADVERTISE
                        )
                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_ADVERTISE
                        )
                    } else {
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                        )
                    }
                    val missingPermissions = requiredPermissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (isPeerConnectionModeChanged) {
                        if (missingPermissions.isNotEmpty()) {
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        } else {
                            PeerConnectionViewModel.startPeerConnectionService(context)
                        }
                    }
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
