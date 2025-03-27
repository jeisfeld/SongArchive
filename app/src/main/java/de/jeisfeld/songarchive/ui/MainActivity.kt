package de.jeisfeld.songarchive.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.audio.isInternetAvailable
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.network.DisplayStyle
import de.jeisfeld.songarchive.network.PeerConnectionAction
import de.jeisfeld.songarchive.network.PeerConnectionMode
import de.jeisfeld.songarchive.network.PeerConnectionService
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors
import de.jeisfeld.songarchive.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[SongViewModel::class.java]
        setContent {
            AppTheme {
                MainScreen(viewModel)
            }
        }
        @Suppress("DEPRECATION")
        val song: Song? = intent.getParcelableExtra("SONG")
        song?.let {
            val searchString = if (song.id.length > 4) song.id.substring(0, 4) else song.id
            viewModel.initState.observe(this) { initState ->
                if (initState == 1) {
                    viewModel.initState.postValue(2)
                    viewModel.searchQuery.value = searchString
                    viewModel.searchSongs(searchString)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SongViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isWideScreen = LocalConfiguration.current.screenWidthDp > 600
    var isSyncing by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    val isConnectedState = remember { mutableStateOf(isInternetAvailable(context)) }

    var showShareLyricsDialog by remember { mutableStateOf(false) }
    var sharedLyricsText by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.all { it.value }) {
            PeerConnectionViewModel.startPeerConnectionService(context)
        }
    }

    // If DB is empty on startup, then synchronize data
    LaunchedEffect(Unit) {
        viewModel.isDatabaseEmpty { isEmpty ->
            if (isEmpty) {
                isSyncing = true
                viewModel.synchronizeDatabaseAndImages(true) { success ->
                    isSyncing = false
                }
            }
            else {
                viewModel.checkForSongUpdates { needsUpdate ->
                    if (needsUpdate) {
                        showSyncDialog = true
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isConnectedState.value = isInternetAvailable(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground), // App icon
                                        contentDescription = null,
                                        modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_medium))
                                    )
                                    Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                                    Text(
                                        text = stringResource(id = R.string.app_name),
                                        color = AppColors.TextColor,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground), // App icon
                                        contentDescription = null,
                                        modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_medium))
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = AppColors.Background,
                            titleContentColor = AppColors.TextColor
                        ),
                        actions = {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_menu),
                                    contentDescription = "Menu",
                                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(AppColors.BackgroundShaded) // Set menu background color
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(id = R.string.sync),
                                            color = AppColors.TextColor // Ensure text is colored
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        isSyncing = true
                                        viewModel.synchronizeDatabaseAndImages(true) { success ->
                                            isSyncing = false
                                            viewModel.searchSongs(viewModel.searchQuery.value)
                                        }
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
                                            stringResource(id = R.string.network_connection),
                                            color = AppColors.TextColor // Ensure text is colored
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
                                if (showNetworkDialog) {
                                    NetworkModeDialog(
                                        context = context,
                                        selectedNetworkMode = PeerConnectionViewModel.peerConnectionMode,
                                        selectedClientMode = PeerConnectionViewModel.clientMode,
                                        onModeSelected = { networkMode, clientMode ->
                                            PeerConnectionViewModel.peerConnectionMode = networkMode
                                            PeerConnectionViewModel.clientMode = clientMode
                                            showMenu = false
                                            showNetworkDialog = false
                                            val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                arrayOf(
                                                    Manifest.permission.NEARBY_WIFI_DEVICES,
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.BLUETOOTH,
                                                    Manifest.permission.BLUETOOTH_SCAN,
                                                    Manifest.permission.BLUETOOTH_ADMIN,
                                                    Manifest.permission.BLUETOOTH_CONNECT,
                                                    Manifest.permission.BLUETOOTH_ADVERTISE
                                                )
                                            }
                                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.BLUETOOTH,
                                                    Manifest.permission.BLUETOOTH_SCAN,
                                                    Manifest.permission.BLUETOOTH_ADMIN,
                                                    Manifest.permission.BLUETOOTH_CONNECT,
                                                    Manifest.permission.BLUETOOTH_ADVERTISE
                                                )
                                            }
                                            else {
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.BLUETOOTH,
                                                    Manifest.permission.BLUETOOTH_ADMIN,
                                                )
                                            }
                                            val missingPermissions = requiredPermissions.filter {
                                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                            }
                                            if (missingPermissions.isNotEmpty()) {
                                                permissionLauncher.launch(missingPermissions.toTypedArray())
                                            }
                                            else {
                                                PeerConnectionViewModel.startPeerConnectionService(context)
                                            }
                                        },
                                        onDismiss = { showNetworkDialog = false }
                                    )
                                }
                                if (PeerConnectionViewModel.peerConnectionMode == PeerConnectionMode.SERVER && PeerConnectionViewModel.connectedDevices > 0) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(id = R.string.share_lyrics),
                                                color = AppColors.TextColor
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showShareLyricsDialog = true
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
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensionResource(id = R.dimen.spacing_medium))
                            .offset(y = -12.dp) // Move up slightly to overlap
                    ) {
                        SearchBar(viewModel)
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Background)
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    SongTable(viewModel, viewModel.songs.collectAsState().value, isWideScreen, isConnectedState.value)
                }
            }
        }
        if (isSyncing) {
            ProgressOverlay()
        }
        if (showSyncDialog) {
            AlertDialog(
                onDismissRequest = { showSyncDialog = false },
                title = { Text(context.getString(R.string.update_available_title)) },
                text = { Text(context.getString(R.string.update_available_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        showSyncDialog = false
                        isSyncing = true
                        viewModel.synchronizeDatabaseAndImages(false) { success ->
                            isSyncing = false
                            viewModel.searchSongs(viewModel.searchQuery.value)
                        }
                    }) {
                        Text(context.getString(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSyncDialog = false }) {
                        Text(context.getString(R.string.cancel))
                    }
                }
            )
        }
        if (showShareLyricsDialog) {
            AlertDialog(
                onDismissRequest = { showShareLyricsDialog = false },
                title = { Text(stringResource(id = R.string.share_lyrics)) },
                text = {
                    OutlinedTextField(
                        value = sharedLyricsText,
                        onValueChange = { sharedLyricsText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text(stringResource(id = R.string.enter_lyrics_for_sharing)) },
                        singleLine = false,
                        maxLines = 20
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                            setAction(PeerConnectionAction.DISPLAY_LYRICS.toString())
                            putExtra("ACTION", PeerConnectionAction.DISPLAY_LYRICS)
                            putExtra("STYLE", DisplayStyle.REMOTE_DEFAULT)
                            putExtra("LYRICS", sharedLyricsText)
                        }
                        context.startService(serviceIntent)
                    }) {
                        Text(stringResource(id = R.string.share_lyrics))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                            setAction(PeerConnectionAction.DISPLAY_LYRICS.toString())
                            putExtra("ACTION", PeerConnectionAction.DISPLAY_LYRICS)
                            putExtra("STYLE", DisplayStyle.REMOTE_BLACK)
                            putExtra("LYRICS", " ")
                        }
                        context.startService(serviceIntent)
                        showShareLyricsDialog = false
                    }) {
                        Text(stringResource(id = R.string.stop_share_lyrics))
                    }
                }
            )
        }
    }
}

@Composable
fun ProgressOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)), // Semi-transparent background
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White) // Spinning loader
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.spacing_heading_vertical)))
            Text(text = stringResource(id = R.string.syncing), color = Color.White)
        }
    }
}

@Composable
fun SearchBar(viewModel: SongViewModel) {
    OutlinedTextField(
        value = viewModel.searchQuery.value,
        onValueChange = {
            viewModel.searchQuery.value = it
            viewModel.searchSongs(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = dimensionResource(id = R.dimen.spacing_medium), end = dimensionResource(id = R.dimen.spacing_medium)),
        singleLine = true,
        label = { Text(stringResource(id = R.string.search)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.Transparent,
            focusedBorderColor = AppColors.TextColorLight,
            unfocusedBorderColor = AppColors.TextColorLight,
            cursorColor = AppColors.TextColor
        ),
        trailingIcon = {
            if (viewModel.searchQuery.value.isNotEmpty()) {
                IconButton(onClick = {
                    viewModel.searchQuery.value = ""
                    viewModel.searchSongs("")
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = Color.Gray,
                        modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                    )
                }
            }
        }
    )
}
