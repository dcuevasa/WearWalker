package com.example.wearwalker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wearwalker.core.DowsingFeedback
import com.example.wearwalker.core.DowsingMode
import com.example.wearwalker.core.HomeEventType
import com.example.wearwalker.core.RadarBattleAnimation
import com.example.wearwalker.core.RadarBattleAction
import com.example.wearwalker.core.RadarMode
import com.example.wearwalker.core.DeviceBinary
import com.example.wearwalker.core.DeviceEngine
import com.example.wearwalker.core.DeviceInteractionState
import com.example.wearwalker.core.DeviceLcdRenderer
import com.example.wearwalker.core.DeviceMenuItem
import com.example.wearwalker.core.DeviceOffsets
import com.example.wearwalker.core.DeviceScreen
import com.example.wearwalker.core.DeviceSettingsField
import com.example.wearwalker.core.DeviceSettingsMode
import com.example.wearwalker.core.DeviceSnapshot
import com.example.wearwalker.data.EepromFileInfo
import com.example.wearwalker.data.EepromLoadSource
import com.example.wearwalker.data.EepromStorage
import com.example.wearwalker.data.EepromValidationReport
import com.example.wearwalker.data.EepromValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs
import kotlin.random.Random

enum class EmulatorPhase {
    Loading,
    Ready,
    Error,
}

data class EmulatorUiState(
    val phase: EmulatorPhase = EmulatorPhase.Loading,
    val statusMessage: String = "Booting emulator...",
    val snapshot: DeviceSnapshot? = null,
    val validation: EepromValidationReport? = null,
    val bridgeStatus: String = "IR disabled. Wi-Fi bridge protocol planned.",
    val eepromSource: String = "Unknown",
    val eepromPath: String = "",
    val eepromExists: Boolean = false,
    val eepromSizeBytes: Long = 0,
    val eepromUserProvided: Boolean = false,
    val requiredAssetsReady: Boolean = false,
    val requiredAssetsMessage: String = "Copy eeprom.bin manually to /data/data/com.example.wearwalker/files/.",
    val importHint: String = "",
    val lcdPreviewPixels: IntArray = IntArray(0),
    val lcdSceneLabel: String = DeviceScreen.Home.label,
    val lcdHasVisualContent: Boolean = false,
    val selectedMenuLabel: String = DeviceMenuItem.Radar.label,
    val actionHint: String = "Use LEFT/RIGHT/ENTER actions to play.",
    val caughtPokemonCount: Int = 0,
    val foundItemCount: Int = 0,
    val totalActions: Int = 0,
)

class EmulatorViewModel(
    private val eepromStorage: EepromStorage,
) : ViewModel() {
    companion object {
        private const val RADAR_COST_WATTS = 10
        private const val DOWSING_COST_WATTS = 3
        private const val DOWSING_ATTEMPTS = 2
        private const val RADAR_CHAIN_MAX = 4
        private const val RADAR_MAX_HP = 4
        private const val RADAR_EXTRA_LOSS_PENALTY_WATTS = 10
        private const val DOWSING_REVEAL_TICKS = 2

        private const val RADAR_ATTACK_ENEMY_EVADE_CHANCE = 30
        private const val RADAR_ATTACK_CRIT_CHANCE = 20

        private const val RADAR_EVADE_COUNTER_CHANCE = 55
        private const val RADAR_EVADE_STARE_CHANCE = 30

        private const val RADAR_ANIM_TICKS_SHORT = 2
        private const val RADAR_ANIM_TICKS_MEDIUM = 3
        private const val RADAR_ANIM_TICKS_WIGGLE = 3
        private const val RADAR_ANIM_TICKS_LONG = 4

        private const val RADAR_TEXT_FOUND_SOMETHING = 0x50B0
        private const val RADAR_TEXT_APPEARED = 0x53B0
        private const val RADAR_TEXT_ATTACKED = 0x59B0
        private const val RADAR_TEXT_EVADED = 0x5B30
        private const val RADAR_TEXT_CRITICAL_HIT = 0x5CB0
        private const val RADAR_TEXT_BLANK = 0x5E30
        private const val RADAR_TEXT_THROW_POKEBALL = 0x5FB0
        private const val RADAR_TEXT_CAUGHT = 0x5530
        private const val RADAR_TEXT_FLED = 0x56B0
        private const val RADAR_TEXT_GOT_AWAY = 0x5230
        private const val RADAR_TEXT_WAS_TOO_STRONG = 0x5830

        private const val HOME_EVENT_MIN_TICKS = 8
        private const val HOME_EVENT_MAX_TICKS = 16
        private const val HOME_EVENT_SPAWN_CHANCE_PERCENT = 10

        private const val MENU_IDLE_MIN_TICKS = 8
        private const val MENU_IDLE_MAX_TICKS = 11
        private const val SETTINGS_IDLE_MIN_TICKS = 8
        private const val SETTINGS_IDLE_MAX_TICKS = 11
        private const val DOWSING_IDLE_MIN_TICKS = 8
        private const val DOWSING_IDLE_MAX_TICKS = 11
        private const val RADAR_SIGNAL_MIN_TICKS = 3
        private const val RADAR_SIGNAL_MAX_TICKS = 5

        private const val MANUAL_FILES_DIRECTORY = "/data/data/com.example.wearwalker/files/"
    }

    private val _uiState = MutableStateFlow(EmulatorUiState())
    val uiState: StateFlow<EmulatorUiState> = _uiState.asStateFlow()

    private var engine: DeviceEngine? = null
    private var eepromSource: String = "Unknown"
    private var interactionState = DeviceInteractionState()
    private var animationFrame = 0
    private var totalActions = 0

    init {
        loadFromStorage()
        startAnimationLoop()
    }

    fun loadFromStorage() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = EmulatorPhase.Loading,
                    statusMessage = "Loading EEPROM from local storage...",
                )
            }

            runCatching {
                val eepromLoaded = eepromStorage.loadOrCreate()

                if (engine == null) {
                    engine = DeviceEngine(eepromLoaded.eeprom)
                } else {
                    engine?.replaceEeprom(eepromLoaded.eeprom)
                }

                interactionState = interactionState.copy(screen = DeviceScreen.Home)

                val eepromStatus =
                    when (eepromLoaded.source) {
                        EepromLoadSource.Existing -> {
                            eepromSource = "Stored local EEPROM"
                            eepromLoaded.detail
                        }
                        EepromLoadSource.CreatedBlank -> {
                            eepromSource = "Auto-created blank EEPROM"
                            "${eepromLoaded.detail} Copy your real eeprom.bin to the shown path."
                        }
                        EepromLoadSource.RecreatedFromInvalid -> {
                            eepromSource = "Recreated blank EEPROM from invalid file"
                            "${eepromLoaded.detail} Copy your real eeprom.bin to the shown path."
                        }
                    }

                refreshState(eepromStatus)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        phase = EmulatorPhase.Error,
                        statusMessage = "Failed to load storage: ${error.message}",
                    )
                }
            }
        }
    }

    fun refreshStorageStatus() {
        loadFromStorage()
    }

    fun onLeftAction() {
        totalActions += 1

        if (interactionState.screen == DeviceScreen.Radar) {
            when (interactionState.radarMode) {
                RadarMode.BattleMenu -> {
                    handleRadarBattleActionChoice(RadarBattleAction.Attack)
                    return
                }
                RadarMode.BattleMessage -> {
                    advanceRadarBattleMessageByAnyButton()
                    return
                }
                RadarMode.BattleSwap -> {
                    if (interactionState.radarSwapCursor == 0) {
                        cancelRadarBattleSwap()
                        return
                    }
                    interactionState =
                        interactionState.copy(
                            radarSwapCursor = (interactionState.radarSwapCursor - 1).coerceAtLeast(0),
                        )
                    refreshState("Choose which Pokemon to replace.")
                    return
                }
                RadarMode.Scan -> {
                    // Continue with default branch behavior below.
                }
            }
        }

        if (interactionState.screen == DeviceScreen.Dowsing && interactionState.dowsingMode == DowsingMode.Swap) {
            if (interactionState.dowsingSwapCursor == 0) {
                cancelDowsingSwap()
                return
            }
            interactionState =
                interactionState.copy(
                    dowsingSwapCursor = (interactionState.dowsingSwapCursor - 1).coerceAtLeast(0),
                )
            refreshState("Choose which item to replace.")
            return
        }

        interactionState =
            when (interactionState.screen) {
                DeviceScreen.Home -> interactionState
                DeviceScreen.Menu ->
                    interactionState.copy(
                        menuIndex = wrapCircularIndex(interactionState.menuIndex - 1, DeviceMenuItem.entries.size),
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                    )
                DeviceScreen.Radar ->
                    when (interactionState.radarMode) {
                        RadarMode.Scan ->
                            interactionState.copy(radarCursor = (interactionState.radarCursor - 1).coerceAtLeast(0))
                        RadarMode.BattleMenu -> interactionState
                        RadarMode.BattleMessage -> interactionState
                        RadarMode.BattleSwap -> interactionState
                    }
                DeviceScreen.Dowsing ->
                    if (
                        interactionState.dowsingMode != DowsingMode.Search ||
                        interactionState.dowsingAttemptsRemaining <= 0 ||
                        interactionState.dowsingOutcome == true
                    ) {
                        interactionState
                    } else {
                        interactionState.copy(
                            dowsingCursor = (interactionState.dowsingCursor - 1).coerceAtLeast(0),
                            dowsingIdleTicksRemaining = randomDowsingIdleTicks(),
                        )
                    }
                DeviceScreen.Connect ->
                    interactionState.copy(
                        screen = DeviceScreen.Menu,
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                    )
                DeviceScreen.Card ->
                    if (interactionState.cardPageIndex > 0) {
                        interactionState.copy(cardPageIndex = interactionState.cardPageIndex - 1)
                    } else {
                        interactionState.copy(
                            screen = DeviceScreen.Menu,
                            menuIdleTicksRemaining = randomMenuIdleTicks(),
                        )
                    }
                DeviceScreen.Pokemon ->
                    if (interactionState.pokemonIndex <= 0) {
                        interactionState.copy(
                            screen = DeviceScreen.Menu,
                            menuIndex = DeviceMenuItem.Pokemon.ordinal,
                            menuIdleTicksRemaining = randomMenuIdleTicks(),
                        )
                    } else {
                        interactionState.copy(pokemonIndex = interactionState.pokemonIndex - 1)
                    }
                DeviceScreen.Settings ->
                    onLeftInSettings().copy(settingsIdleTicksRemaining = randomSettingsIdleTicks())
            }
        refreshState("LEFT action.")
    }

    fun onRightAction() {
        totalActions += 1

        if (interactionState.screen == DeviceScreen.Radar) {
            when (interactionState.radarMode) {
                RadarMode.BattleMenu -> {
                    handleRadarBattleActionChoice(RadarBattleAction.Evade)
                    return
                }
                RadarMode.BattleMessage -> {
                    advanceRadarBattleMessageByAnyButton()
                    return
                }
                RadarMode.BattleSwap -> {
                    interactionState =
                        interactionState.copy(
                            radarSwapCursor =
                                (interactionState.radarSwapCursor + 1).coerceAtMost(DeviceOffsets.POKEMON_SLOT_COUNT - 1),
                        )
                    refreshState("Choose which Pokemon to replace.")
                    return
                }
                RadarMode.Scan -> {
                    // Continue with default branch behavior below.
                }
            }
        }

        if (interactionState.screen == DeviceScreen.Dowsing && interactionState.dowsingMode == DowsingMode.Swap) {
            interactionState =
                interactionState.copy(
                    dowsingSwapCursor =
                        (interactionState.dowsingSwapCursor + 1).coerceAtMost(DeviceOffsets.DOWSED_ITEM_COUNT - 1),
                )
            refreshState("Choose which item to replace.")
            return
        }

        interactionState =
            when (interactionState.screen) {
                DeviceScreen.Home -> interactionState
                DeviceScreen.Menu ->
                    interactionState.copy(
                        menuIndex = wrapCircularIndex(interactionState.menuIndex + 1, DeviceMenuItem.entries.size),
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                    )
                DeviceScreen.Radar ->
                    when (interactionState.radarMode) {
                        RadarMode.Scan ->
                            interactionState.copy(radarCursor = (interactionState.radarCursor + 1).coerceAtMost(3))
                        RadarMode.BattleMenu -> interactionState
                        RadarMode.BattleMessage -> interactionState
                        RadarMode.BattleSwap -> interactionState
                    }
                DeviceScreen.Dowsing ->
                    if (
                        interactionState.dowsingMode != DowsingMode.Search ||
                        interactionState.dowsingAttemptsRemaining <= 0 ||
                        interactionState.dowsingOutcome == true
                    ) {
                        interactionState
                    } else {
                        interactionState.copy(
                            dowsingCursor = (interactionState.dowsingCursor + 1).coerceAtMost(5),
                            dowsingIdleTicksRemaining = randomDowsingIdleTicks(),
                        )
                    }
                DeviceScreen.Connect ->
                    interactionState.copy(
                        screen = DeviceScreen.Menu,
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                    )
                DeviceScreen.Card ->
                    interactionState.copy(
                        cardPageIndex = (interactionState.cardPageIndex + 1).coerceAtMost(maxCardPageIndex()),
                    )
                DeviceScreen.Pokemon ->
                    interactionState.copy(
                        pokemonIndex = (interactionState.pokemonIndex + 1).coerceAtMost(pokemonEntryCount() - 1),
                    )
                DeviceScreen.Settings ->
                    onRightInSettings().copy(settingsIdleTicksRemaining = randomSettingsIdleTicks())
            }
        refreshState("RIGHT action.")
    }

    fun onEnterAction() {
        totalActions += 1
        when (interactionState.screen) {
            DeviceScreen.Home -> {
                if (!ensureRequiredAssets()) {
                    return
                }

                if (interactionState.homeEventType != null) {
                    collectHomeEvent()
                    return
                }

                interactionState =
                    interactionState.copy(
                        screen = DeviceScreen.Menu,
                        menuIndex = 0,
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                    )
                refreshState("ENTER action: opened main menu.")
            }
            DeviceScreen.Menu -> onEnterFromMenu()
            DeviceScreen.Radar -> {
                if (interactionState.radarMode == RadarMode.BattleMenu) {
                    handleRadarBattleActionChoice(RadarBattleAction.Catch)
                } else {
                    handleRadarAction()
                }
            }
            DeviceScreen.Dowsing -> performDowsingAction()
            DeviceScreen.Connect -> {
                interactionState =
                    interactionState.copy(
                        screen = DeviceScreen.Menu,
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                    )
                refreshState("Connect: returning to menu.")
            }
            DeviceScreen.Card -> {
                interactionState =
                    interactionState.copy(
                        screen = DeviceScreen.Menu,
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                    )
                refreshState("Trainer Card: returning to menu.")
            }
            DeviceScreen.Pokemon -> {
                interactionState =
                    interactionState.copy(
                        screen = DeviceScreen.Menu,
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                    )
                refreshState("Pokemon list: returning to menu.")
            }
            DeviceScreen.Settings -> onEnterInSettings()
        }
    }

    private fun collectHomeEvent() {
        viewModelScope.launch {
            val currentEngine = engine ?: return@launch
            val eventType = interactionState.homeEventType ?: return@launch

            val message =
                when (eventType) {
                    HomeEventType.Watts10 -> {
                        currentEngine.addWatts(10)
                        "Found 10W from a Home event."
                    }

                    HomeEventType.Watts20 -> {
                        currentEngine.addWatts(20)
                        "Found 20W from a Home event."
                    }

                    HomeEventType.Watts50 -> {
                        currentEngine.addWatts(50)
                        "Found 50W from a Home event."
                    }

                    HomeEventType.Item -> {
                        val found = currentEngine.recordDowsedItemFromRoute(interactionState.homeEventItemIndex)
                        if (found) {
                            "Found a free item from a Home event."
                        } else {
                            "Found an item event, but no free item slot was available."
                        }
                    }
                }

            currentEngine.setLastSyncNow(Instant.now().epochSecond)
            interactionState =
                interactionState.copy(
                    homeEventType = null,
                    homeEventMusicBubble = false,
                    homeEventTicksRemaining = 0,
                    homeEventItemIndex = 0,
                )
            persistAndRefresh(message)
        }
    }

    fun addSteps(delta: Int = 100) {
        viewModelScope.launch {
            val currentEngine = engine ?: return@launch
            currentEngine.addSteps(delta)
            currentEngine.setLastSyncNow(Instant.now().epochSecond)
            persistAndRefresh("Added $delta steps.")
        }
    }

    fun addWatts(delta: Int = 10) {
        viewModelScope.launch {
            val currentEngine = engine ?: return@launch
            currentEngine.addWatts(delta)
            currentEngine.setLastSyncNow(Instant.now().epochSecond)
            persistAndRefresh("Added $delta watts.")
        }
    }

    fun saveNow() {
        viewModelScope.launch {
            persistAndRefresh("EEPROM saved.")
        }
    }

    private suspend fun persistAndRefresh(message: String) {
        val currentEngine = engine ?: return
        eepromStorage.save(currentEngine.exportEeprom())
        refreshState(message)
    }

    private fun onEnterFromMenu() {
        when (interactionState.selectedMenuItem()) {
            DeviceMenuItem.Radar -> {
                if (!ensureRequiredAssets()) {
                    return
                }

                val currentEngine = engine ?: return
                if (!hasWalkingPokemonInEeprom(currentEngine.exportEeprom())) {
                    refreshState("No Pokemon held. Use Connect to send one first.")
                    return
                }
                if (!currentEngine.spendWatts(RADAR_COST_WATTS)) {
                    refreshState("Not enough watts for Poke Radar (requires ${RADAR_COST_WATTS}W).")
                    return
                }

                val initialSignal = rollRadarSignal()

                currentEngine.setLastSyncNow(Instant.now().epochSecond)
                interactionState =
                    interactionState.copy(
                        screen = DeviceScreen.Radar,
                        radarCursor = 0,
                        radarMode = RadarMode.Scan,
                        radarBattleAction = RadarBattleAction.Attack,
                        radarBattleAnimation = RadarBattleAnimation.None,
                        radarBattleEnemyResponded = false,
                        radarBattleAnimTicksRemaining = 0,
                        radarBattlePokemonSlot = 0,
                        radarBattleMessageOffset = null,
                        radarBattleReturnToMenu = false,
                        radarPendingCatchSuccess = null,
                        radarChainProgress = 0,
                        radarChainTarget = randomRadarChainTarget(),
                        radarSignalLevel = initialSignal.signalLevel,
                        radarSignalRouteSlot = initialSignal.routeSlot,
                        radarPlayerHp = RADAR_MAX_HP,
                        radarEnemyHp = RADAR_MAX_HP,
                        radarOutcome = null,
                        radarSignalCursor = Random.nextInt(4),
                        radarResolvedCursor = null,
                        radarSignalTicksRemaining = randomRadarSignalWindowTicks(),
                        radarSwapCursor = 0,
                        radarPendingCatchRouteSlot = null,
                    )
                viewModelScope.launch {
                    persistAndRefresh("Opened Poke Radar (-${RADAR_COST_WATTS}W).")
                }
            }
            DeviceMenuItem.Dowsing -> {
                if (!ensureRequiredAssets()) {
                    return
                }

                val currentEngine = engine ?: return
                if (!hasWalkingPokemonInEeprom(currentEngine.exportEeprom())) {
                    refreshState("No Pokemon held. Use Connect to send one first.")
                    return
                }
                if (!currentEngine.spendWatts(DOWSING_COST_WATTS)) {
                    refreshState("Not enough watts for Dowsing (requires ${DOWSING_COST_WATTS}W).")
                    return
                }

                currentEngine.setLastSyncNow(Instant.now().epochSecond)
                interactionState =
                    interactionState.copy(
                        screen = DeviceScreen.Dowsing,
                        dowsingCursor = 0,
                        dowsingOutcome = null,
                        dowsingGameOver = false,
                        dowsingWon = null,
                        dowsingMode = DowsingMode.Search,
                        dowsingRevealCursor = -1,
                        dowsingSwapCursor = 0,
                        dowsingRevealTicksRemaining = 0,
                        dowsingPendingFound = false,
                        dowsingPendingNear = false,
                        dowsingPendingStored = false,
                        dowsingCheckedMask = 0,
                        dowsingResultItemIndex = null,
                        dowsingFeedback = DowsingFeedback.None,
                        dowsingAttemptsRemaining = DOWSING_ATTEMPTS,
                        dowsingTargetCursor = Random.nextInt(6),
                        dowsingTargetItemIndex = rollDowsingRouteItemIndex(),
                        dowsingIdleTicksRemaining = randomDowsingIdleTicks(),
                    )
                viewModelScope.launch {
                    persistAndRefresh("Opened Dowsing (-${DOWSING_COST_WATTS}W).")
                }
            }
            DeviceMenuItem.Connect -> {
                interactionState = interactionState.copy(screen = DeviceScreen.Connect)
                refreshState("Opened Connect screen.")
            }
            DeviceMenuItem.Card -> {
                interactionState =
                    interactionState.copy(
                        screen = DeviceScreen.Card,
                        cardPageIndex = 0,
                    )
                refreshState("Opened Trainer Card.")
            }
            DeviceMenuItem.Pokemon -> {
                interactionState =
                    interactionState.copy(
                        screen = DeviceScreen.Pokemon,
                        pokemonIndex = 0,
                    )
                refreshState("Opened Pokemon list.")
            }
            DeviceMenuItem.Settings -> {
                interactionState =
                    interactionState.copy(
                        screen = DeviceScreen.Settings,
                        settingsField = DeviceSettingsField.Sound,
                        settingsMode = DeviceSettingsMode.SelectField,
                        settingsIdleTicksRemaining = randomSettingsIdleTicks(),
                    )
                refreshState("Opened settings.")
            }
        }
    }

    private fun onLeftInSettings(): DeviceInteractionState {
        return when (interactionState.settingsMode) {
            DeviceSettingsMode.SelectField ->
                interactionState.copy(settingsField = previousSettingsField(interactionState.settingsField))
            DeviceSettingsMode.AdjustSound ->
                interactionState.copy(soundLevel = (interactionState.soundLevel - 1).coerceAtLeast(0))
            DeviceSettingsMode.AdjustShade ->
                interactionState.copy(shadeLevel = (interactionState.shadeLevel - 1).coerceAtLeast(0))
        }
    }

    private fun onRightInSettings(): DeviceInteractionState {
        return when (interactionState.settingsMode) {
            DeviceSettingsMode.SelectField ->
                interactionState.copy(settingsField = nextSettingsField(interactionState.settingsField))
            DeviceSettingsMode.AdjustSound ->
                interactionState.copy(soundLevel = (interactionState.soundLevel + 1).coerceAtMost(2))
            DeviceSettingsMode.AdjustShade ->
                interactionState.copy(shadeLevel = (interactionState.shadeLevel + 1).coerceAtMost(3))
        }
    }

    private fun onEnterInSettings() {
        interactionState =
            when (interactionState.settingsMode) {
                DeviceSettingsMode.SelectField -> {
                    when (interactionState.settingsField) {
                        DeviceSettingsField.Sound ->
                            interactionState.copy(
                                settingsMode = DeviceSettingsMode.AdjustSound,
                                settingsIdleTicksRemaining = randomSettingsIdleTicks(),
                            )
                        DeviceSettingsField.Shade ->
                            interactionState.copy(
                                settingsMode = DeviceSettingsMode.AdjustShade,
                                settingsIdleTicksRemaining = randomSettingsIdleTicks(),
                            )
                    }
                }
                DeviceSettingsMode.AdjustSound ->
                    interactionState.copy(
                        screen = DeviceScreen.Menu,
                        settingsMode = DeviceSettingsMode.SelectField,
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                        settingsIdleTicksRemaining = 0,
                    )
                DeviceSettingsMode.AdjustShade ->
                    interactionState.copy(
                        screen = DeviceScreen.Menu,
                        settingsMode = DeviceSettingsMode.SelectField,
                        menuIdleTicksRemaining = randomMenuIdleTicks(),
                        settingsIdleTicksRemaining = 0,
                    )
            }

        val status =
            when (interactionState.screen) {
                DeviceScreen.Menu -> "Settings confirmed: returning to menu."
                else ->
                    when (interactionState.settingsMode) {
                        DeviceSettingsMode.SelectField -> "Settings: select Sound or Shade."
                        DeviceSettingsMode.AdjustSound -> "Settings: adjust sound."
                        DeviceSettingsMode.AdjustShade -> "Settings: adjust shade."
                    }
            }
        refreshState(status)
    }

    private fun handleRadarAction() {
        if (!ensureRequiredAssets()) {
            return
        }

        if (!hasWalkingPokemon()) {
            resetRadarToHome("No Pokemon held. Returning Home.", outcome = false)
            return
        }

        when (interactionState.radarMode) {
            RadarMode.Scan -> handleRadarScanEnter()
            RadarMode.BattleMenu -> handleRadarBattleMenuEnter()
            RadarMode.BattleMessage -> handleRadarBattleMessageEnter()
            RadarMode.BattleSwap -> handleRadarBattleSwapEnter()
        }
    }

    private fun handleRadarBattleActionChoice(action: RadarBattleAction) {
        interactionState = interactionState.copy(radarBattleAction = action)
        handleRadarBattleMenuEnter()
    }

    private fun handleRadarScanEnter() {
        when (
            val resolution =
                resolveRadarScanEnter(
                    state = interactionState,
                    chainMax = RADAR_CHAIN_MAX,
                    radarMaxHp = RADAR_MAX_HP,
                    textAppeared = RADAR_TEXT_APPEARED,
                    rollSignal = ::rollRadarSignal,
                    randomSignalCursor = ::randomRadarSignalCursor,
                    randomSignalWindowTicks = ::randomRadarSignalWindowTicks,
                )
        ) {
            is RadarScanEnterResolution.ResetToHome -> {
                resetRadarToHome(resolution.statusMessage, outcome = resolution.outcome)
            }

            is RadarScanEnterResolution.UpdateState -> {
                interactionState = resolution.state
                refreshState(resolution.statusMessage)
            }
        }
    }

    private fun handleRadarBattleMenuEnter() {
        val currentEngine = engine ?: return
        var playerHp = interactionState.radarPlayerHp.coerceIn(0, RADAR_MAX_HP)
        var enemyHp = interactionState.radarEnemyHp.coerceIn(0, RADAR_MAX_HP)
        var enemyResponded = false
        var messageUsesEnemyName = false
        var pendingEnemyCounterAttack = false
        var pendingCriticalMessage = false
        var messageOffset = RADAR_TEXT_ATTACKED
        var returnHome = false
        var persistMessage: String? = null
        var shouldPersist = false
        var battleAnimation = RadarBattleAnimation.None

        when (interactionState.radarBattleAction) {
            RadarBattleAction.Attack -> {
                val turn =
                    resolveRadarAttackTurn(
                        state = interactionState,
                        radarMaxHp = RADAR_MAX_HP,
                        enemyEvadeChancePercent = RADAR_ATTACK_ENEMY_EVADE_CHANCE,
                        criticalChancePercent = RADAR_ATTACK_CRIT_CHANCE,
                        textAttacked = RADAR_TEXT_ATTACKED,
                        textTooStrong = RADAR_TEXT_WAS_TOO_STRONG,
                        enemyEvadeRoll = Random.nextInt(100),
                        criticalRoll = Random.nextInt(100),
                    )

                if (turn.applyLossPenalty) {
                    applyRadarLossPenalty(currentEngine)
                }
                if (turn.touchLastSyncNow) {
                    currentEngine.setLastSyncNow(Instant.now().epochSecond)
                }

                playerHp = turn.playerHp
                enemyHp = turn.enemyHp
                enemyResponded = turn.enemyResponded
                messageUsesEnemyName = turn.messageUsesEnemyName
                pendingEnemyCounterAttack = turn.pendingEnemyCounterAttack
                pendingCriticalMessage = turn.pendingCriticalMessage
                messageOffset = turn.messageOffset
                returnHome = turn.returnHome
                persistMessage = turn.persistMessage
                shouldPersist = turn.shouldPersist
                battleAnimation = turn.battleAnimation
            }

            RadarBattleAction.Evade -> {
                val turn =
                    resolveRadarEvadeTurn(
                        state = interactionState,
                        counterChancePercent = RADAR_EVADE_COUNTER_CHANCE,
                        stareChancePercent = RADAR_EVADE_STARE_CHANCE,
                        textEvaded = RADAR_TEXT_EVADED,
                        textGotAway = RADAR_TEXT_GOT_AWAY,
                        roll = Random.nextInt(100),
                        radarMaxHp = RADAR_MAX_HP,
                    )

                playerHp = turn.playerHp
                enemyHp = turn.enemyHp
                enemyResponded = turn.enemyResponded
                messageUsesEnemyName = turn.messageUsesEnemyName
                pendingEnemyCounterAttack = turn.pendingEnemyCounterAttack
                pendingCriticalMessage = turn.pendingCriticalMessage
                messageOffset = turn.messageOffset
                returnHome = turn.returnHome
                persistMessage = turn.persistMessage
                shouldPersist = turn.shouldPersist
                battleAnimation = turn.battleAnimation
            }

            RadarBattleAction.Catch -> {
                val throwSucceeded = Random.nextInt(100) < radarCatchChanceByEnemyHp(enemyHp)
                var caught = false
                var catchOverflowRouteSlot: Int? = null

                if (throwSucceeded) {
                    if (currentEngine.hasFreeCaughtPokemonSlot()) {
                        caught = currentEngine.recordCaughtPokemonFromRoute(interactionState.radarBattlePokemonSlot)
                    } else {
                        // Catch succeeded, but storage is full: user must choose a slot to release.
                        caught = true
                        catchOverflowRouteSlot = interactionState.radarBattlePokemonSlot
                    }
                }

                if (caught && catchOverflowRouteSlot == null) {
                    currentEngine.setLastSyncNow(Instant.now().epochSecond)
                    shouldPersist = true
                }

                interactionState =
                    interactionState.copy(
                        radarPlayerHp = playerHp,
                        radarEnemyHp = enemyHp,
                        radarMode = RadarMode.BattleMessage,
                        radarBattleMessageOffset = RADAR_TEXT_THROW_POKEBALL,
                        radarBattleReturnToMenu = false,
                        radarBattleEnemyResponded = false,
                        radarBattleMessageUsesEnemyName = false,
                        radarBattlePendingEnemyCounterAttack = false,
                        radarBattlePendingCriticalMessage = false,
                        radarPendingCatchSuccess = caught,
                        radarPendingCatchRouteSlot = catchOverflowRouteSlot,
                        radarSwapCursor = 1,
                        radarBattleAnimation = RadarBattleAnimation.CatchThrow,
                        radarBattleAnimTicksRemaining =
                            radarBattleAnimationTicks(RadarBattleAnimation.CatchThrow),
                    )
                if (shouldPersist) {
                    viewModelScope.launch {
                        persistAndRefresh("Threw Pokeball.")
                    }
                } else {
                    refreshState("Threw Pokeball.")
                }
                return
            }
        }

        interactionState =
            interactionState.copy(
                radarPlayerHp = playerHp,
                radarEnemyHp = enemyHp,
                radarMode = RadarMode.BattleMessage,
                radarBattleMessageOffset = messageOffset,
                radarBattleReturnToMenu = returnHome,
                radarBattleEnemyResponded = enemyResponded,
                radarBattleMessageUsesEnemyName = messageUsesEnemyName,
                radarBattlePendingEnemyCounterAttack = pendingEnemyCounterAttack,
                radarBattlePendingCriticalMessage = pendingCriticalMessage,
                radarPendingCatchSuccess = null,
                radarPendingCatchRouteSlot = null,
                radarBattleAnimation = battleAnimation,
                radarBattleAnimTicksRemaining = radarBattleAnimationTicks(battleAnimation),
            )

        val finalMessage = persistMessage ?: "Radar turn resolved."
        if (shouldPersist) {
            viewModelScope.launch {
                persistAndRefresh(finalMessage)
            }
        } else {
            refreshState(finalMessage)
        }
    }

    private fun handleRadarBattleMessageEnter() {
        when (
            val resolution =
                resolveRadarBattleMessageEnter(
                    state = interactionState,
                    isBattleAnimationActive = isRadarBattleAnimationActive(),
                    offsets =
                        RadarMessageEnterOffsets(
                            foundSomething = RADAR_TEXT_FOUND_SOMETHING,
                            appeared = RADAR_TEXT_APPEARED,
                            throwPokeball = RADAR_TEXT_THROW_POKEBALL,
                            gotAway = RADAR_TEXT_GOT_AWAY,
                            fled = RADAR_TEXT_FLED,
                            wasTooStrong = RADAR_TEXT_WAS_TOO_STRONG,
                            criticalHit = RADAR_TEXT_CRITICAL_HIT,
                            attacked = RADAR_TEXT_ATTACKED,
                        ),
                    enemyAttackAnimationTicks = radarBattleAnimationTicks(RadarBattleAnimation.EnemyAttack),
                )
        ) {
            is RadarBattleMessageEnterResolution.ResetToHome -> {
                resetRadarToHome(resolution.statusMessage, outcome = resolution.outcome)
            }

            is RadarBattleMessageEnterResolution.RefreshOnly -> {
                refreshState(resolution.statusMessage)
            }

            is RadarBattleMessageEnterResolution.UpdateState -> {
                interactionState = resolution.state
                refreshState(resolution.statusMessage)
            }
        }
    }

    private fun advanceRadarBattleMessageByAnyButton() {
        handleRadarBattleMessageEnter()
    }

    private fun cancelRadarBattleSwap() {
        interactionState =
            interactionState.copy(
                screen = DeviceScreen.Home,
                radarMode = RadarMode.Scan,
                radarPendingCatchSuccess = null,
                radarPendingCatchRouteSlot = null,
                radarSwapCursor = 0,
                radarBattleEnemyResponded = false,
                radarBattleMessageUsesEnemyName = false,
                radarBattlePendingEnemyCounterAttack = false,
                radarBattlePendingCriticalMessage = false,
                radarBattleAnimation = RadarBattleAnimation.None,
                radarBattleAnimTicksRemaining = 0,
            )
        refreshState("Cancelled catch and kept existing Pokemon.")
    }

    private fun cancelDowsingSwap() {
        interactionState = resetDowsingStateToHome(interactionState)
        refreshState("Cancelled item swap and kept current items.")
    }

    private fun handleRadarBattleSwapEnter() {
        val currentEngine = engine ?: return
        val routeSlot = interactionState.radarPendingCatchRouteSlot
        if (routeSlot == null) {
            resetRadarToHome("No pending caught Pokemon to swap.", outcome = false)
            return
        }

        val replaceSlot = interactionState.radarSwapCursor.coerceIn(0, DeviceOffsets.POKEMON_SLOT_COUNT - 1)
        val replaced = currentEngine.replaceCaughtPokemonWithRoute(replaceSlot, routeSlot)
        if (!replaced) {
            resetRadarToHome("Could not swap caught Pokemon. Returning Home.", outcome = false)
            return
        }

        currentEngine.setLastSyncNow(Instant.now().epochSecond)
        interactionState =
            interactionState.copy(
                screen = DeviceScreen.Home,
                radarMode = RadarMode.Scan,
                radarPendingCatchSuccess = null,
                radarPendingCatchRouteSlot = null,
                radarSwapCursor = 0,
                radarBattleEnemyResponded = false,
                radarBattleMessageUsesEnemyName = false,
                radarBattlePendingEnemyCounterAttack = false,
                radarBattlePendingCriticalMessage = false,
                radarBattleAnimation = RadarBattleAnimation.None,
                radarBattleAnimTicksRemaining = 0,
            )

        viewModelScope.launch {
            persistAndRefresh("Swapped a caught Pokemon and kept the new one.")
        }
    }

    private fun performDowsingAction() {
        if (!ensureRequiredAssets()) {
            return
        }

        if (!hasWalkingPokemon()) {
            interactionState = resetDowsingStateToHome(interactionState)
            refreshState("No Pokemon held. Returning Home.")
            return
        }

        val currentEngine = engine ?: return

        when (interactionState.dowsingMode) {
            DowsingMode.Search -> {
                if (interactionState.dowsingAttemptsRemaining <= 0) {
                    interactionState =
                        interactionState.copy(
                            dowsingGameOver = true,
                            dowsingWon = false,
                            dowsingMode = DowsingMode.EndMessage,
                            dowsingResultItemIndex = interactionState.dowsingTargetItemIndex,
                            dowsingFeedback = DowsingFeedback.Far,
                        )
                    refreshState("Dowsing ended. ENTER to return Home.")
                    return
                }

                val selected = interactionState.dowsingCursor.coerceIn(0, 5)
                val distance = abs(selected - interactionState.dowsingTargetCursor)
                val found = distance == 0
                val near = !found && distance <= 1
                val checkedMask = interactionState.dowsingCheckedMask or (1 shl selected)
                val remaining = (interactionState.dowsingAttemptsRemaining - 1).coerceAtLeast(0)
                val resolvedItemIndex = if (found) rollDowsingRouteItemIndex() else interactionState.dowsingTargetItemIndex
                val stored =
                    if (found && currentEngine.hasFreeDowsedItemSlot()) {
                        currentEngine.recordDowsedItemFromRoute(resolvedItemIndex)
                    } else {
                        false
                    }

                interactionState =
                    startDowsingReveal(
                        state = interactionState,
                        selectedCursor = selected,
                        found = found,
                        near = near,
                        stored = stored,
                        resolvedItemIndex = resolvedItemIndex,
                        checkedMask = checkedMask,
                        remainingAttempts = remaining,
                        revealTicks = DOWSING_REVEAL_TICKS,
                    )
                refreshState("Dowsing scan...")
            }

            DowsingMode.MissMessage -> {
                interactionState = enterDowsingHintState(interactionState)
                refreshState("Hint shown. ENTER to continue.")
            }

            DowsingMode.HintMessage -> {
                interactionState = enterDowsingSearchState(interactionState)
                refreshState("Discover an item!")
            }

            DowsingMode.Reveal -> {
                refreshState("Dowsing scan...")
            }

            DowsingMode.FoundMessage -> {
                if (!interactionState.dowsingPendingStored) {
                    val pendingItemIndex = interactionState.dowsingResultItemIndex
                    if (pendingItemIndex == null) {
                        interactionState = resetDowsingStateToHome(interactionState)
                        refreshState("No found item to swap. Returning Home.")
                        return
                    }

                    interactionState = enterDowsingSwapState(interactionState, pendingItemIndex)
                    refreshState("Bag is full. Choose which item to replace.")
                    return
                }

                currentEngine.setLastSyncNow(Instant.now().epochSecond)
                interactionState = resetDowsingStateToHome(interactionState)

                viewModelScope.launch {
                    persistAndRefresh("Dowsing finished: item stored.")
                }
            }

            DowsingMode.Swap -> {
                val pendingItemIndex = interactionState.dowsingResultItemIndex
                if (pendingItemIndex == null) {
                    interactionState = resetDowsingStateToHome(interactionState)
                    refreshState("No found item to swap. Returning Home.")
                    return
                }

                val replaceSlot = interactionState.dowsingSwapCursor.coerceIn(0, DeviceOffsets.DOWSED_ITEM_COUNT - 1)
                val replaced = currentEngine.replaceDowsedItemWithRoute(replaceSlot, pendingItemIndex)
                if (!replaced) {
                    interactionState = resetDowsingStateToHome(interactionState)
                    refreshState("Could not swap item. Returning Home.")
                    return
                }

                currentEngine.setLastSyncNow(Instant.now().epochSecond)
                interactionState = resetDowsingStateToHome(interactionState)

                viewModelScope.launch {
                    persistAndRefresh("Swapped found item and kept the new one.")
                }
            }

            DowsingMode.EndMessage -> {
                interactionState = resetDowsingStateToHome(interactionState)
                refreshState("Dowsing finished: returning Home.")
            }
        }
    }

    private fun refreshState(statusMessage: String? = null) {
        val currentEngine = engine ?: return
        val eeprom = currentEngine.exportEeprom()
        val validation = EepromValidator.validate(eeprom)
        val phase = if (validation.isValidSize) EmulatorPhase.Ready else EmulatorPhase.Error

        val eepromInfo = eepromStorage.getFileInfo()

        val snapshot = currentEngine.snapshot(validation.mirrorInSync)
        val assetsStatus = currentRequiredAssetsStatus(validation, eepromInfo)
        val caughtCount = DeviceBinary.countCaughtPokemon(eeprom)
        val foundItemsCount = DeviceBinary.countFoundItems(eeprom)

        interactionState =
            interactionState.copy(
                caughtPokemonCount = caughtCount,
                foundItemCount = foundItemsCount,
            )

        val spriteSource = eeprom

        val preview =
            DeviceLcdRenderer.render(
                eeprom = eeprom,
                spriteData = spriteSource,
                state = interactionState,
                steps = snapshot.steps,
                watts = snapshot.watts,
                animationFrame = animationFrame,
            )

        val importHint = "Manual folder: $MANUAL_FILES_DIRECTORY (copy eeprom.bin)"

        val message = statusMessage ?: _uiState.value.statusMessage
        val selectedMenu = interactionState.selectedMenuItem().label

        _uiState.value =
            EmulatorUiState(
                phase = phase,
                statusMessage = message,
                snapshot = snapshot,
                validation = validation,
                eepromSource = eepromSource,
                eepromPath = eepromInfo.absolutePath,
                eepromExists = eepromInfo.exists,
                eepromSizeBytes = eepromInfo.sizeBytes,
                eepromUserProvided = eepromInfo.userProvided,
                requiredAssetsReady = assetsStatus.ready,
                requiredAssetsMessage = assetsStatus.message,
                importHint = importHint,
                lcdPreviewPixels = preview.pixels,
                lcdSceneLabel = interactionState.screen.label,
                lcdHasVisualContent = preview.hasVisualContent,
                selectedMenuLabel = selectedMenu,
                actionHint =
                    buildActionHintForScreen(
                        state = interactionState,
                        hasWalkingPokemon = hasWalkingPokemon(),
                        radarAnimationActive = isRadarBattleAnimationActive(),
                    ),
                caughtPokemonCount = caughtCount,
                foundItemCount = foundItemsCount,
                totalActions = totalActions,
            )
    }

    private fun ensureRequiredAssets(): Boolean {
        val status = currentRequiredAssetsStatus()
        if (!status.ready) {
            refreshState(status.message)
            return false
        }
        return true
    }

    private fun currentRequiredAssetsStatus(): RequiredAssetsStatus {
        val currentEngine = engine ?: return RequiredAssetsStatus(false, "EEPROM engine is not ready.")
        val eeprom = currentEngine.exportEeprom()
        val validation = EepromValidator.validate(eeprom)
        val eepromInfo = eepromStorage.getFileInfo()
        return currentRequiredAssetsStatus(validation, eepromInfo)
    }

    private fun currentRequiredAssetsStatus(
        validation: EepromValidationReport,
        eepromInfo: EepromFileInfo,
    ): RequiredAssetsStatus {
        val (ready, message) =
            computeRequiredAssetsStatus(
                validation = validation,
                eepromInfo = eepromInfo,
                manualDirectory = MANUAL_FILES_DIRECTORY,
                expectedSizeBytes = DeviceOffsets.EEPROM_SIZE,
            )
        return RequiredAssetsStatus(
            ready = ready,
            message = message,
        )
    }

    private fun pokemonEntryCount(): Int {
        val currentEngine = engine ?: return 1
        return countPokemonEntriesFromEeprom(currentEngine.exportEeprom())
    }

    private fun maxCardPageIndex(): Int {
        val currentEngine = engine ?: return 0
        return maxCardPageIndexFromEeprom(currentEngine.exportEeprom())
    }

    private fun previousSettingsField(field: DeviceSettingsField): DeviceSettingsField {
        return previousSettingsFieldValue(field)
    }

    private fun nextSettingsField(field: DeviceSettingsField): DeviceSettingsField {
        return nextSettingsFieldValue(field)
    }

    private fun startAnimationLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(650)
                animationFrame = (animationFrame + 1) % 8
                tickInteractionState()
                if (engine != null) {
                    refreshState()
                }
            }
        }
    }

    private fun tickInteractionState() {
        interactionState =
            when (interactionState.screen) {
                DeviceScreen.Home -> tickHomeInteraction(interactionState)
                DeviceScreen.Menu -> {
                    val nextTicks = interactionState.menuIdleTicksRemaining - 1
                    if (nextTicks <= 0) {
                        interactionState.copy(
                            screen = DeviceScreen.Home,
                            menuIdleTicksRemaining = 0,
                        )
                    } else {
                        interactionState.copy(menuIdleTicksRemaining = nextTicks)
                    }
                }
                DeviceScreen.Radar ->
                    if (
                        interactionState.radarMode == RadarMode.BattleMessage &&
                        interactionState.radarBattleAnimTicksRemaining > 0
                    ) {
                        val nextTicks = interactionState.radarBattleAnimTicksRemaining - 1
                        if (nextTicks > 0) {
                            interactionState.copy(radarBattleAnimTicksRemaining = nextTicks)
                        } else {
                            onRadarBattleAnimationFinished(interactionState)
                        }
                    } else if (
                        interactionState.radarMode == RadarMode.BattleMessage &&
                        interactionState.radarBattleMessageOffset == RADAR_TEXT_THROW_POKEBALL &&
                        interactionState.radarPendingCatchSuccess != null
                    ) {
                        interactionState.copy(
                            radarBattleAnimation = RadarBattleAnimation.CatchThrow,
                            radarBattleAnimTicksRemaining =
                                radarBattleAnimationTicks(RadarBattleAnimation.CatchThrow),
                        )
                    } else if (interactionState.radarMode != RadarMode.Scan || interactionState.radarSignalCursor == null) {
                        interactionState
                    } else {
                        val nextTicks = interactionState.radarSignalTicksRemaining - 1
                        if (nextTicks <= 0) {
                            val currentSignal = interactionState.radarSignalCursor ?: 0
                            val nextSignal = rollRadarSignal()
                            interactionState.copy(
                                radarResolvedCursor = currentSignal,
                                radarSignalCursor = randomRadarSignalCursor(currentSignal),
                                radarSignalTicksRemaining = randomRadarSignalWindowTicks(),
                                radarSignalLevel = nextSignal.signalLevel,
                                radarSignalRouteSlot = nextSignal.routeSlot,
                                radarOutcome = null,
                            )
                        } else {
                            interactionState.copy(radarSignalTicksRemaining = nextTicks)
                        }
                    }
                DeviceScreen.Dowsing -> {
                    if (interactionState.dowsingMode != DowsingMode.Reveal) {
                        interactionState
                    } else {
                        val nextTicks = interactionState.dowsingRevealTicksRemaining - 1
                        if (nextTicks > 0) {
                            interactionState.copy(dowsingRevealTicksRemaining = nextTicks)
                        } else {
                            val found = interactionState.dowsingPendingFound
                            val remaining = interactionState.dowsingAttemptsRemaining
                            if (found) {
                                interactionState.copy(
                                    dowsingOutcome = true,
                                    dowsingGameOver = true,
                                    dowsingWon = true,
                                    dowsingMode = DowsingMode.FoundMessage,
                                    dowsingResultItemIndex = interactionState.dowsingTargetItemIndex,
                                    dowsingRevealTicksRemaining = 0,
                                    dowsingFeedback = DowsingFeedback.Found,
                                )
                            } else if (remaining <= 0) {
                                interactionState.copy(
                                    dowsingOutcome = false,
                                    dowsingGameOver = true,
                                    dowsingWon = false,
                                    dowsingMode = DowsingMode.EndMessage,
                                    dowsingResultItemIndex = interactionState.dowsingTargetItemIndex,
                                    dowsingRevealTicksRemaining = 0,
                                    dowsingFeedback = DowsingFeedback.Far,
                                )
                            } else {
                                interactionState.copy(
                                    dowsingMode = DowsingMode.MissMessage,
                                    dowsingRevealTicksRemaining = 0,
                                    dowsingFeedback = DowsingFeedback.None,
                                )
                            }
                        }
                    }
                }
                DeviceScreen.Settings -> {
                    val nextTicks = interactionState.settingsIdleTicksRemaining - 1
                    if (nextTicks <= 0) {
                        interactionState.copy(
                            screen = DeviceScreen.Menu,
                            settingsMode = DeviceSettingsMode.SelectField,
                            menuIdleTicksRemaining = randomMenuIdleTicks(),
                            settingsIdleTicksRemaining = 0,
                        )
                    } else {
                        interactionState.copy(settingsIdleTicksRemaining = nextTicks)
                    }
                }
                else -> interactionState
            }
    }

    private fun tickHomeInteraction(state: DeviceInteractionState): DeviceInteractionState {
        return nextHomeInteractionState(
            state = state,
            hasWalkingPokemon = hasWalkingPokemon(),
            spawnChancePercent = HOME_EVENT_SPAWN_CHANCE_PERCENT,
            minEventTicks = HOME_EVENT_MIN_TICKS,
            maxEventTicks = HOME_EVENT_MAX_TICKS,
            randomEventType = ::chooseRandomHomeEventType,
            randomItemIndex = ::rollDowsingRouteItemIndex,
        )
    }

    private fun randomAvailableRouteItemIndex(): Int {
        val currentEngine = engine ?: return Random.nextInt(DeviceOffsets.ROUTE_ITEM_COUNT)
        return randomRouteItemIndexFromEeprom(currentEngine.exportEeprom())
    }

    private fun rollDowsingRouteItemIndex(): Int {
        val currentEngine = engine ?: return randomAvailableRouteItemIndex()
        return rollDowsingRouteItemIndexFromEeprom(currentEngine.exportEeprom())
    }

    private fun hasWalkingPokemon(): Boolean {
        val currentEngine = engine ?: return false
        return hasWalkingPokemonInEeprom(currentEngine.exportEeprom())
    }

    private fun randomMenuIdleTicks(): Int {
        return randomTicksInRange(MENU_IDLE_MIN_TICKS, MENU_IDLE_MAX_TICKS)
    }

    private fun randomSettingsIdleTicks(): Int {
        return randomTicksInRange(SETTINGS_IDLE_MIN_TICKS, SETTINGS_IDLE_MAX_TICKS)
    }

    private fun randomDowsingIdleTicks(): Int {
        return randomTicksInRange(DOWSING_IDLE_MIN_TICKS, DOWSING_IDLE_MAX_TICKS)
    }

    private fun randomRadarSignalWindowTicks(): Int {
        return randomTicksInRange(RADAR_SIGNAL_MIN_TICKS, RADAR_SIGNAL_MAX_TICKS)
    }

    private fun randomRadarChainTarget(): Int {
        return 1
    }

    private fun rollRadarSignal(): RadarSignal {
        val currentEngine = engine ?: return RadarSignal(routeSlot = 0, signalLevel = 1)
        return rollRadarSignalFromEeprom(currentEngine.exportEeprom())
    }

    private fun onRadarBattleAnimationFinished(state: DeviceInteractionState): DeviceInteractionState {
        return resolveRadarBattleAnimationFinished(
            state = state,
            messages =
                RadarBattleMessageOffsets(
                    criticalHit = RADAR_TEXT_CRITICAL_HIT,
                    attacked = RADAR_TEXT_ATTACKED,
                    evaded = RADAR_TEXT_EVADED,
                    wasTooStrong = RADAR_TEXT_WAS_TOO_STRONG,
                    blank = RADAR_TEXT_BLANK,
                    caught = RADAR_TEXT_CAUGHT,
                    fled = RADAR_TEXT_FLED,
                ),
            tickCountForAnimation = ::radarBattleAnimationTicks,
        )
    }

    private fun radarBattleAnimationTicks(animation: RadarBattleAnimation): Int {
        return radarBattleAnimationTickCount(
            animation = animation,
            shortTicks = RADAR_ANIM_TICKS_SHORT,
            mediumTicks = RADAR_ANIM_TICKS_MEDIUM,
            wiggleTicks = RADAR_ANIM_TICKS_WIGGLE,
            longTicks = RADAR_ANIM_TICKS_LONG,
        )
    }

    private fun isRadarBattleAnimationActive(): Boolean {
        return interactionState.screen == DeviceScreen.Radar &&
            interactionState.radarMode == RadarMode.BattleMessage &&
            interactionState.radarBattleAnimation != RadarBattleAnimation.None &&
            interactionState.radarBattleAnimTicksRemaining > 0
    }

    private fun applyRadarLossPenalty(currentEngine: DeviceEngine) {
        val current = currentEngine.currentWattsValue()
        val penalty = minOf(current, RADAR_EXTRA_LOSS_PENALTY_WATTS)
        if (penalty > 0) {
            currentEngine.spendWatts(penalty)
        }
    }

    private fun resetRadarToHome(
        statusMessage: String,
        outcome: Boolean? = null,
    ) {
        interactionState =
            interactionState.copy(
                screen = DeviceScreen.Home,
                radarCursor = 0,
                radarMode = RadarMode.Scan,
                radarBattleAction = RadarBattleAction.Attack,
                radarBattleAnimation = RadarBattleAnimation.None,
                radarBattleEnemyResponded = false,
                radarBattleMessageUsesEnemyName = false,
                radarBattlePendingEnemyCounterAttack = false,
                radarBattlePendingCriticalMessage = false,
                radarBattleAnimTicksRemaining = 0,
                radarBattlePokemonSlot = 0,
                radarSwapCursor = 0,
                radarBattleMessageOffset = null,
                radarBattleReturnToMenu = false,
                radarPendingCatchSuccess = null,
                radarPendingCatchRouteSlot = null,
                radarChainProgress = 0,
                radarChainTarget = 1,
                radarSignalLevel = 1,
                radarPlayerHp = RADAR_MAX_HP,
                radarEnemyHp = RADAR_MAX_HP,
                radarSignalCursor = null,
                radarResolvedCursor = null,
                radarSignalTicksRemaining = 0,
                radarOutcome = outcome,
            )
        refreshState(statusMessage)
    }

    private data class RequiredAssetsStatus(
        val ready: Boolean,
        val message: String,
    )

    class Factory(
        private val eepromStorage: EepromStorage,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EmulatorViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return EmulatorViewModel(eepromStorage) as T
        }
    }
}
