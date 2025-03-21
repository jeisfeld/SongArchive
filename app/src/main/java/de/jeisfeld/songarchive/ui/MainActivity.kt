package de.jeisfeld.songarchive.ui

import android.Manifest
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors
import de.jeisfeld.songarchive.ui.theme.AppTheme
import de.jeisfeld.songarchive.wifi.WifiMode
import de.jeisfeld.songarchive.wifi.WifiViewModel

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
    val isWideScreen = LocalConfiguration.current.screenWidthDp > 600
    var isSyncing by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showWifiDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.all { it.value }) {
            WifiViewModel.startWifiDirectService(context)
        }
    }

    // If DB is empty on startup, then synchronize data
    LaunchedEffect(Unit) {
        viewModel.isDatabaseEmpty { isEmpty ->
            if (isEmpty) {
                isSyncing = true
                viewModel.synchronizeDatabaseAndImages { success ->
                    isSyncing = false
                }
            }
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
                                        viewModel.synchronizeDatabaseAndImages { success ->
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
                                            stringResource(id = R.string.wifi_transfer),
                                            color = AppColors.TextColor // Ensure text is colored
                                        )
                                    },
                                    onClick = {
                                        showWifiDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_wifi),
                                            contentDescription = "Wi-Fi Transfer",
                                            modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                                        )
                                    }
                                )
                                if (showWifiDialog) {
                                    WifiTransferDialog(
                                        selectedMode = WifiViewModel.wifiTransferMode,
                                        onModeSelected = { mode ->
                                            WifiViewModel.wifiTransferMode = mode
                                            showMenu = false
                                            showWifiDialog = false
                                            val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                arrayOf(
                                                    Manifest.permission.NEARBY_WIFI_DEVICES,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            } else {
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            }
                                            val missingPermissions = requiredPermissions.filter {
                                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                            }
                                            if (missingPermissions.isNotEmpty()) {
                                                permissionLauncher.launch(missingPermissions.toTypedArray())
                                            }
                                            else {
                                                WifiViewModel.startWifiDirectService(context)
                                            }
                                        },
                                        onDismiss = { showWifiDialog = false }
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
                    SongTable(viewModel, viewModel.songs.collectAsState().value, isWideScreen)
                }
            }
        }
        if (isSyncing) {
            ProgressOverlay()
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

@Composable
fun WifiTransferDialog(
    selectedMode: WifiMode,
    onModeSelected: (WifiMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(WifiMode.DISABLED, WifiMode.SERVER, WifiMode.CLIENT)
    var selectedOption by remember { mutableStateOf(selectedMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi Transfer Mode") },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (selectedOption == option),
                            onClick = { selectedOption = option }
                        )
                        Text(stringArrayResource(R.array.wifi_modes)[index])
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onModeSelected(selectedOption) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

