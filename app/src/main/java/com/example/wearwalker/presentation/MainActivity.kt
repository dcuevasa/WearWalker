package com.example.wearwalker.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import com.example.wearwalker.data.EepromStorage
import com.example.wearwalker.presentation.theme.WearWalkerTheme

class MainActivity : ComponentActivity() {
    private val emulatorViewModel: EmulatorViewModel by viewModels {
        EmulatorViewModel.Factory(
            eepromStorage = EepromStorage(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(emulatorViewModel)
        }
    }
}

@Composable
fun WearApp(viewModel: EmulatorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    WearWalkerTheme {
        EmulatorScreen(
            uiState = uiState,
            onAddSteps = { viewModel.addSteps() },
            onAddWatts = { viewModel.addWatts() },
            onSave = { viewModel.saveNow() },
            onRefreshStatus = { viewModel.refreshStorageStatus() },
            onLeftAction = { viewModel.onLeftAction() },
            onEnterAction = { viewModel.onEnterAction() },
            onRightAction = { viewModel.onRightAction() },
        )
    }
}