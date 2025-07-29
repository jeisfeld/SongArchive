package de.jeisfeld.songarchive.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SongViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    titleContentColor = AppColors.TextColor
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close",
                            modifier = Modifier.padding(dimensionResource(id = R.dimen.spacing_small))
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(horizontal = dimensionResource(id = R.dimen.spacing_medium))
        ) {
            Text(
                text = stringResource(id = R.string.settings),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.padding(dimensionResource(id = R.dimen.spacing_small)))
            Text(
                text = stringResource(id = R.string.app_language),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.spacing_small)))
            val lang = viewModel.appLanguage.value
            val options = listOf("system", "en", "de")
            val labels = listOf(
                R.string.language_system,
                R.string.language_english,
                R.string.language_german
            )
            options.forEachIndexed { index, option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setAppLanguage(option) }
                        .padding(vertical = dimensionResource(id = R.dimen.spacing_vertical_small))
                ) {
                    RadioButton(
                        selected = lang == option,
                        onClick = { viewModel.setAppLanguage(option) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AppColors.TextColor,
                            unselectedColor = AppColors.TextColorLight
                        )
                    )
                    Text(
                        text = stringResource(id = labels[index]),
                        modifier = Modifier.padding(start = dimensionResource(id = R.dimen.spacing_medium)),
                        color = AppColors.TextColor
                    )
                }
            }
            Spacer(modifier = Modifier.padding(dimensionResource(id = R.dimen.spacing_small)))
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }) {
                Text(stringResource(id = R.string.configure_battery_optimizations))
            }
        }
    }
}
