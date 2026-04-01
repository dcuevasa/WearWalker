package com.example.wearwalker.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.wearwalker.R
import com.example.wearwalker.core.DeviceLcdRenderer

@Composable
fun EmulatorScreen(
    uiState: EmulatorUiState,
    onAddSteps: () -> Unit,
    onAddWatts: () -> Unit,
    onSave: () -> Unit,
    onRefreshStatus: () -> Unit,
    onLeftAction: () -> Unit,
    onEnterAction: () -> Unit,
    onRightAction: () -> Unit,
) {
    var showDebugView by rememberSaveable { mutableStateOf(false) }

    if (showDebugView) {
        EmulatorDebugScreen(
            uiState = uiState,
            onAddSteps = onAddSteps,
            onAddWatts = onAddWatts,
            onSave = onSave,
            onRefreshStatus = onRefreshStatus,
            onCloseDebug = { showDebugView = false },
        )
        return
    }

    EmulatorMainScreen(
        uiState = uiState,
        onLeftAction = onLeftAction,
        onEnterAction = onEnterAction,
        onRightAction = onRightAction,
        onOpenDebug = { showDebugView = true },
    )
}

@Composable
private fun EmulatorMainScreen(
    uiState: EmulatorUiState,
    onLeftAction: () -> Unit,
    onEnterAction: () -> Unit,
    onRightAction: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LcdPreview(
                uiState = uiState,
                modifier =
                    Modifier
                        .fillMaxWidth(0.86f)
                        .widthIn(max = 112.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.95f)
                        .widthIn(max = 220.dp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionButton(label = stringResource(R.string.btn_left_short), onClick = onLeftAction)
                    ActionButton(label = stringResource(R.string.btn_enter_short), onClick = onEnterAction)
                    ActionButton(label = stringResource(R.string.btn_right_short), onClick = onRightAction)
                }

                Button(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(30.dp),
                    onClick = onOpenDebug,
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Text(text = stringResource(R.string.btn_settings_gear))
                }
            }

            if (!uiState.requiredAssetsReady) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.required_assets_missing_short),
                    style = MaterialTheme.typography.caption3,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.size(34.dp),
        onClick = onClick,
        colors = ButtonDefaults.primaryButtonColors(),
    ) {
        Text(text = label)
    }
}

@Composable
private fun EmulatorDebugScreen(
    uiState: EmulatorUiState,
    onAddSteps: () -> Unit,
    onAddWatts: () -> Unit,
    onSave: () -> Unit,
    onRefreshStatus: () -> Unit,
    onCloseDebug: () -> Unit,
) {
    val snapshot = uiState.snapshot
    val mirrorText =
        if (snapshot?.mirrorInSync == true) {
            stringResource(R.string.mirror_in_sync)
        } else {
            stringResource(R.string.mirror_out_of_sync)
        }

    val lines = buildList {
        add("Status: ${uiState.phase.name}")
        add(uiState.statusMessage)
        add(uiState.bridgeStatus)
        add("Screen: ${uiState.lcdSceneLabel}")
        add("Menu selection: ${uiState.selectedMenuLabel}")
        add("Action hint: ${uiState.actionHint}")
        add("Total actions: ${uiState.totalActions}")
        add(
            if (uiState.lcdHasVisualContent) {
                "Visual assets detected in current EEPROM."
            } else {
                "No visible sprite data found for this scene (blank or incomplete EEPROM)."
            },
        )
        add("Caught Pokemon (session): ${uiState.caughtPokemonCount}")
        add("Found items (session): ${uiState.foundItemCount}")
        add("EEPROM source: ${uiState.eepromSource}")
        add("EEPROM exists: ${uiState.eepromExists}")
        add("EEPROM size: ${uiState.eepromSizeBytes} bytes")
        add("EEPROM user-provided: ${uiState.eepromUserProvided}")
        add("EEPROM path: ${uiState.eepromPath}")
        add("Required assets ready: ${uiState.requiredAssetsReady}")
        add(uiState.requiredAssetsMessage)
        add(uiState.importHint)
        add("Copy eeprom.bin manually to the path above.")

        if (snapshot != null) {
            add("${stringResource(R.string.trainer_label)}: ${snapshot.trainerName}")
            add("${stringResource(R.string.steps_label)}: ${snapshot.steps}")
            add("${stringResource(R.string.watts_label)}: ${snapshot.watts}")
            add(
                "${stringResource(R.string.flags_label)}: " +
                    "registered=${snapshot.isRegistered}, hasPokemon=${snapshot.hasPokemon}",
            )
            add(
                "Protocol: ${snapshot.protocolVersion}.${snapshot.protocolSubVersion} " +
                    "Sync=${snapshot.lastSyncEpochSeconds}",
            )
            add("${stringResource(R.string.mirror_label)}: $mirrorText")
        }

        uiState.validation?.notes?.forEach { add(it) }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 10.dp),
    ) {
        item {
            TimeText(modifier = Modifier.fillMaxWidth())
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCloseDebug,
                label = { Text(stringResource(R.string.btn_back_to_emulator)) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }

        items(lines) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddSteps,
                label = { Text(stringResource(R.string.btn_add_steps)) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddWatts,
                label = { Text(stringResource(R.string.btn_add_watts)) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSave,
                label = { Text(stringResource(R.string.btn_save)) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRefreshStatus,
                label = { Text(stringResource(R.string.btn_refresh_status)) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}

@Composable
private fun LcdPreview(
    uiState: EmulatorUiState,
    modifier: Modifier = Modifier,
) {
    val pixels = uiState.lcdPreviewPixels
    val expectedPixels = DeviceLcdRenderer.WIDTH * DeviceLcdRenderer.HEIGHT
    if (pixels.size != expectedPixels) {
        Text(
            text = "LCD preview unavailable",
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val bitmap =
        remember(pixels) {
            Bitmap.createBitmap(
                pixels,
                DeviceLcdRenderer.WIDTH,
                DeviceLcdRenderer.HEIGHT,
                Bitmap.Config.ARGB_8888,
            )
        }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Device LCD preview",
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(DeviceLcdRenderer.WIDTH.toFloat() / DeviceLcdRenderer.HEIGHT),
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
    )
}
