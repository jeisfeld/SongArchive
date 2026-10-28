package de.jeisfeld.songarchive.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.network.ClientMode
import de.jeisfeld.songarchive.network.PeerConnectionMode
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.network.isNearbyConnectionEnabled
import de.jeisfeld.songarchive.network.missingNearbyPermissions
import de.jeisfeld.songarchive.network.requiredNearbyPermissions

private enum class NetworkAccessDialog { PERMISSIONS, DENIED, RADIOS }

/** Shared by the default shortcut and the mode picker. Pending choices survive activity recreation. */
@Composable
fun rememberNetworkConnectionRequest(context: Context): (PeerConnectionMode, ClientMode) -> Unit {
    var pendingMode by rememberSaveable { mutableStateOf<PeerConnectionMode?>(null) }
    var pendingClientMode by rememberSaveable { mutableStateOf(ClientMode.LYRICS_BS) }
    var dialog by rememberSaveable { mutableStateOf<NetworkAccessDialog?>(null) }

    fun cancelRequest() {
        pendingMode = null
        dialog = null
    }

    fun finishRequest() {
        val mode = pendingMode ?: return
        when {
            missingNearbyPermissions(context).isNotEmpty() -> dialog = NetworkAccessDialog.DENIED
            !isNearbyConnectionEnabled(context) -> dialog = NetworkAccessDialog.RADIOS
            else -> {
                PeerConnectionViewModel.peerConnectionMode = mode
                PeerConnectionViewModel.clientMode = pendingClientMode
                PeerConnectionViewModel.startPeerConnectionService(context)
                cancelRequest()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Check the complete set, including previously granted permissions and cancelled requests.
        finishRequest()
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { finishRequest() }

    dialog?.let { currentDialog ->
        AlertDialog(
            onDismissRequest = ::cancelRequest,
            title = { Text(stringResource(R.string.network_connection)) },
            text = {
                Text(stringResource(when (currentDialog) {
                    NetworkAccessDialog.PERMISSIONS -> R.string.network_permissions_explanation
                    NetworkAccessDialog.DENIED -> R.string.network_permissions_denied
                    NetworkAccessDialog.RADIOS -> R.string.network_radios_required
                }))
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    when (currentDialog) {
                        NetworkAccessDialog.PERMISSIONS -> {
                            val missingPermissions = missingNearbyPermissions(context)
                            if (missingPermissions.isEmpty()) {
                                finishRequest()
                            } else {
                                // Include coarse location even if already granted: Android 12+ requires
                                // both location permissions together when upgrading to precise access.
                                // Already granted permissions do not display another system prompt.
                                permissionLauncher.launch(requiredNearbyPermissions(Build.VERSION.SDK_INT).toTypedArray())
                            }
                        }
                        NetworkAccessDialog.DENIED -> settingsLauncher.launch(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:${context.packageName}".toUri())
                        )
                        NetworkAccessDialog.RADIOS -> settingsLauncher.launch(
                            Intent(Settings.ACTION_SETTINGS)
                        )
                    }
                }) {
                    Text(stringResource(if (currentDialog == NetworkAccessDialog.PERMISSIONS) {
                        R.string.network_grant_permissions
                    } else {
                        R.string.settings
                    }))
                }
            },
            dismissButton = {
                TextButton(onClick = ::cancelRequest) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    return { mode, clientMode ->
        if (mode == PeerConnectionMode.DISABLED) {
            cancelRequest()
            PeerConnectionViewModel.peerConnectionMode = mode
            PeerConnectionViewModel.startPeerConnectionService(context)
        } else {
            pendingMode = mode
            pendingClientMode = clientMode
            if (missingNearbyPermissions(context).isNotEmpty()) {
                dialog = NetworkAccessDialog.PERMISSIONS
            } else {
                finishRequest()
            }
        }
    }
}
