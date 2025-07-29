package de.jeisfeld.songarchive.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.AppDatabase
import de.jeisfeld.songarchive.network.DefaultConnectionType
import de.jeisfeld.songarchive.network.PeerConnectionMode
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.network.PeerConnectionService
import de.jeisfeld.songarchive.network.PeerConnectionAction
import de.jeisfeld.songarchive.network.ClientMode
import de.jeisfeld.songarchive.network.isNearbyConnectionPossible
import de.jeisfeld.songarchive.ui.NetworkModeMenu
import de.jeisfeld.songarchive.ui.theme.AppColors
import de.jeisfeld.songarchive.ui.favoritelists.FavoriteListsActivity
import de.jeisfeld.songarchive.ui.settings.SettingsActivity

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
    var defaultConnectionType by remember { mutableStateOf(DefaultConnectionType.NONE) }
    LaunchedEffect(Unit) {
        val dao = AppDatabase.getDatabase(context).appMetadataDao()
        defaultConnectionType = DefaultConnectionType.fromInt(dao.get()?.defaultConnectionType ?: 0)
    }

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
                },
                modifier = Modifier.pointerInput(defaultConnectionType) {
                    detectTapGestures(
                        onTap = {
                            if (defaultConnectionType == DefaultConnectionType.NONE) {
                                showNetworkMenu = true
                            } else {
                                val target = if (PeerConnectionViewModel.peerConnectionMode == PeerConnectionMode.DISABLED) {
                                    defaultConnectionType.toModes()
                                } else {
                                    PeerConnectionMode.DISABLED to PeerConnectionViewModel.clientMode
                                }
                                val isPeerConnectionModeChanged = target.first != PeerConnectionViewModel.peerConnectionMode
                                PeerConnectionViewModel.peerConnectionMode = target.first
                                PeerConnectionViewModel.clientMode = target.second
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
                            }
                        },
                        onLongPress = {
                            showNetworkMenu = true
                        }
                    )
                }
            )
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
