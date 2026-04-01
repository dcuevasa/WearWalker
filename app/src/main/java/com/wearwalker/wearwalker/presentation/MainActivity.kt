package com.wearwalker.wearwalker.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import com.wearwalker.wearwalker.data.EepromStorage
import com.wearwalker.wearwalker.presentation.theme.WearWalkerTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val ACTIVITY_RECOGNITION_REQUEST_CODE = 1101
    }

    private val emulatorViewModel: EmulatorViewModel by viewModels {
        EmulatorViewModel.Factory(
            appContext = applicationContext,
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

        syncActivityRecognitionState()
        ensureActivityRecognitionPermission()
    }

    override fun onResume() {
        super.onResume()
        syncActivityRecognitionState()
        emulatorViewModel.onHostResumed()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ACTIVITY_RECOGNITION_REQUEST_CODE) {
            syncActivityRecognitionState()
        }
    }

    private fun ensureActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        requestPermissions(
            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
            ACTIVITY_RECOGNITION_REQUEST_CODE,
        )
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }
        return checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    private fun syncActivityRecognitionState() {
        emulatorViewModel.onActivityRecognitionPermissionChanged(hasActivityRecognitionPermission())
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