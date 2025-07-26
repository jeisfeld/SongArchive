package de.jeisfeld.songarchive.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.network.PeerConnectionMode
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.network.isNearbyConnectionPossible
import de.jeisfeld.songarchive.ui.theme.AppColors
import de.jeisfeld.songarchive.ui.FavoriteListsActivity

@Composable
fun MainDropdownMenu(
    showMenu: Boolean,
    onDismissRequest: () -> Unit,
    context: Context,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onShareText: () -> Unit,
    onSync: () -> Unit,
) {
    var showNetworkDialog by remember { mutableStateOf(false) }

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
        if (isNearbyConnectionPossible(context)) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(id = R.string.network_connection),
                        color = AppColors.TextColor
                    )
                },
                onClick = {
                    showNetworkDialog = true
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wifi),
                        contentDescription = "Wi-Fi Transfer",
                        modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                    )
                }
            )
        }
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
        if (showNetworkDialog) {
            NetworkModeDialog(
                context = context,
                selectedNetworkMode = PeerConnectionViewModel.peerConnectionMode,
                selectedClientMode = PeerConnectionViewModel.clientMode,
                onModeSelected = { networkMode, clientMode ->
                    val isPeerConnectionModeChanged = networkMode != PeerConnectionViewModel.peerConnectionMode
                    PeerConnectionViewModel.peerConnectionMode = networkMode
                    PeerConnectionViewModel.clientMode = clientMode
                    onDismissRequest()
                    showNetworkDialog = false
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
                onDismiss = { showNetworkDialog = false }
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
