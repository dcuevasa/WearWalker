package com.example.wearwalker.presentation

import com.example.wearwalker.core.DeviceBinary
import com.example.wearwalker.core.DeviceInteractionState
import com.example.wearwalker.core.DeviceOffsets
import com.example.wearwalker.core.DeviceScreen
import com.example.wearwalker.core.DeviceSettingsField
import com.example.wearwalker.core.DeviceSettingsMode
import com.example.wearwalker.core.DowsingMode
import com.example.wearwalker.core.HomeEventType
import com.example.wearwalker.core.RadarMode
import com.example.wearwalker.data.EepromFileInfo
import com.example.wearwalker.data.EepromValidationReport
import kotlin.random.Random

internal fun nextHomeInteractionState(
    state: DeviceInteractionState,
    hasWalkingPokemon: Boolean,
    spawnChancePercent: Int,
    minEventTicks: Int,
    maxEventTicks: Int,
    randomEventType: () -> HomeEventType,
    randomItemIndex: () -> Int,
): DeviceInteractionState {
    if (!hasWalkingPokemon) {
        return state.copy(
            homeEventType = null,
            homeEventMusicBubble = false,
            homeEventTicksRemaining = 0,
            homeEventItemIndex = 0,
        )
    }

    if (state.homeEventType != null) {
        val remaining = state.homeEventTicksRemaining - 1
        if (remaining <= 0) {
            return state.copy(
                homeEventType = null,
                homeEventMusicBubble = false,
                homeEventTicksRemaining = 0,
                homeEventItemIndex = 0,
            )
        }
        return state.copy(homeEventTicksRemaining = remaining)
    }

    if (Random.nextInt(100) >= spawnChancePercent) {
        return state
    }

    val eventType = randomEventType()
    val itemIndex = if (eventType == HomeEventType.Item) randomItemIndex() else 0

    return state.copy(
        homeEventType = eventType,
        homeEventMusicBubble = Random.nextBoolean(),
        homeEventTicksRemaining = Random.nextInt(minEventTicks, maxEventTicks + 1),
        homeEventItemIndex = itemIndex,
    )
}

internal fun buildActionHintForScreen(
    state: DeviceInteractionState,
    hasWalkingPokemon: Boolean,
    radarAnimationActive: Boolean,
): String {
    return when (state.screen) {
        DeviceScreen.Home -> {
            if (!hasWalkingPokemon) {
                "No Pokemon held. ENTER: menu (Connect)"
            } else if (state.homeEventType != null) {
                "ENTER: collect event"
            } else {
                "ENTER: menu"
            }
        }

        DeviceScreen.Menu -> "LEFT/RIGHT: navigate, ENTER: open, idle: Home"
        DeviceScreen.Radar ->
            when (state.radarMode) {
                RadarMode.Scan ->
                    if (state.radarOutcome == false && state.radarSignalCursor == null) {
                        "It got away. ENTER: return Home"
                    } else if (state.radarSignalCursor == null) {
                        "Signal ended"
                    } else {
                        "Signal active (${state.radarChainProgress}/${state.radarChainTarget}): align cursor and ENTER quickly"
                    }

                RadarMode.BattleMenu ->
                    "LEFT: ATTACK, ENTER: CATCH, RIGHT: EVADE (HP ${state.radarPlayerHp}/${state.radarEnemyHp})"

                RadarMode.BattleMessage ->
                    if (radarAnimationActive) {
                        "Battle animation..."
                    } else {
                        "LEFT/ENTER/RIGHT: continue"
                    }

                RadarMode.BattleSwap ->
                    "LEFT/RIGHT choose slot, LEFT at first cancels, ENTER confirm"
            }

        DeviceScreen.Dowsing -> {
            if (
                state.dowsingMode == DowsingMode.FoundMessage &&
                !state.dowsingPendingStored
            ) {
                "ENTER: choose item to replace"
            } else if (
                state.dowsingMode == DowsingMode.FoundMessage ||
                state.dowsingMode == DowsingMode.EndMessage
            ) {
                "ENTER: return Home"
            } else if (state.dowsingMode == DowsingMode.Swap) {
                "LEFT/RIGHT choose slot, LEFT at first cancels, ENTER confirm"
            } else if (state.dowsingMode == DowsingMode.Reveal) {
                "Scanning..."
            } else if (state.dowsingMode == DowsingMode.MissMessage) {
                "ENTER: continue"
            } else if (state.dowsingMode == DowsingMode.HintMessage) {
                "ENTER: continue"
            } else {
                "LEFT/RIGHT move, ENTER search, attempts=${state.dowsingAttemptsRemaining}"
            }
        }

        DeviceScreen.Connect -> "ENTER: return menu"
        DeviceScreen.Card -> "RIGHT: older day, LEFT: newer day (at page 1 exits), ENTER: return menu"
        DeviceScreen.Pokemon -> "LEFT: previous (first exits), RIGHT: next, ENTER: return menu"
        DeviceScreen.Settings ->
            when (state.settingsMode) {
                DeviceSettingsMode.SelectField -> "LEFT at Sound: back, RIGHT: move selector, ENTER: adjust"
                DeviceSettingsMode.AdjustSound -> "LEFT/RIGHT volume level, ENTER confirm and return Home"
                DeviceSettingsMode.AdjustShade -> "LEFT/RIGHT shade level (10 steps), ENTER confirm and return Home"
            }
    }
}

internal fun countPokemonEntriesFromEeprom(eeprom: ByteArray): Int {
    val walking = if (DeviceBinary.readWalkingPokemonSpecies(eeprom) != 0) 1 else 0
    val caught = DeviceBinary.countCaughtPokemon(eeprom)
    var items = 0
    for (slot in 0 until DeviceOffsets.DOWSED_ITEM_COUNT) {
        if (DeviceBinary.readDowsedItemId(eeprom, slot) != 0) {
            items += 1
        }
    }
    return (walking + caught + items).coerceAtLeast(1)
}

internal fun maxCardPageIndexFromEeprom(eeprom: ByteArray): Int {
    return DeviceOffsets.STEP_HISTORY_DAYS
}

internal fun previousSettingsFieldValue(field: DeviceSettingsField): DeviceSettingsField {
    return when (field) {
        DeviceSettingsField.Sound -> DeviceSettingsField.Shade
        DeviceSettingsField.Shade -> DeviceSettingsField.Sound
    }
}

internal fun nextSettingsFieldValue(field: DeviceSettingsField): DeviceSettingsField {
    return when (field) {
        DeviceSettingsField.Sound -> DeviceSettingsField.Shade
        DeviceSettingsField.Shade -> DeviceSettingsField.Sound
    }
}

internal fun computeRequiredAssetsStatus(
    validation: EepromValidationReport,
    eepromInfo: EepromFileInfo,
    manualDirectory: String,
    expectedSizeBytes: Int,
): Pair<Boolean, String> {
    val issues = mutableListOf<String>()

    if (!eepromInfo.exists) {
        issues += "Copy eeprom.bin to $manualDirectory"
    }

    if (!validation.isValidSize) {
        issues += "Current EEPROM size is invalid (expected $expectedSizeBytes bytes)."
    }

    if (issues.isEmpty()) {
        val signatureWarning =
            if (validation.hasNintendoSignature) {
                ""
            } else {
                " (signature mismatch; using raw EEPROM)"
            }
        return true to "Required assets ready: eeprom.bin found in $manualDirectory$signatureWarning"
    }

    return false to issues.joinToString(" ")
}
