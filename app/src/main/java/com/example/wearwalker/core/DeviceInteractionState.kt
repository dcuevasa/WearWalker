package com.example.wearwalker.core

enum class DeviceScreen(
    val label: String,
) {
    Home("Home"),
    Menu("Menu"),
    Radar("Radar"),
    Dowsing("Dowsing"),
    Connect("Connect"),
    Card("Trainer Card"),
    Pokemon("Pokemon"),
    Settings("Settings");
}

enum class DeviceSettingsField {
    Sound,
    Shade,
}

enum class DeviceSettingsMode {
    SelectField,
    AdjustSound,
    AdjustShade,
}

enum class DowsingFeedback {
    None,
    Near,
    Far,
    Found,
}

enum class DowsingMode {
    Search,
    Reveal,
    MissMessage,
    HintMessage,
    FoundMessage,
    Swap,
    EndMessage,
}

enum class RadarMode {
    Scan,
    BattleMenu,
    BattleMessage,
    BattleSwap,
}

enum class RadarBattleAction {
    Attack,
    Evade,
    Catch,
}

enum class RadarBattleAnimation {
    None,
    AttackHit,
    AttackCrit,
    AttackTrade,
    AttackEnemyEvade,
    EnemyAttack,
    EvadeCounter,
    EvadeStandoff,
    EvadeEnemyFlee,
    CatchThrow,
    CatchWiggle,
    CatchSuccess,
    CatchFail,
}

enum class HomeEventType {
    Watts10,
    Watts20,
    Watts50,
    Item,
}

enum class DeviceMenuItem(
    val label: String,
) {
    Radar("Poke Radar"),
    Dowsing("Dowsing"),
    Connect("Connect"),
    Card("Trainer Card"),
    Pokemon("Pokemon"),
    Settings("Settings");
}

data class DeviceInteractionState(
    val screen: DeviceScreen = DeviceScreen.Home,
    val homeEventType: HomeEventType? = null,
    val homeEventMusicBubble: Boolean = false,
    val homeEventTicksRemaining: Int = 0,
    val homeEventItemIndex: Int = 0,
    val menuIdleTicksRemaining: Int = 0,
    val settingsIdleTicksRemaining: Int = 0,
    val dowsingIdleTicksRemaining: Int = 0,
    val menuIndex: Int = 0,
    val radarCursor: Int = 0,
    val radarMode: RadarMode = RadarMode.Scan,
    val radarBattleAction: RadarBattleAction = RadarBattleAction.Attack,
    val radarBattleAnimation: RadarBattleAnimation = RadarBattleAnimation.None,
    val radarBattleEnemyResponded: Boolean = false,
    val radarBattleMessageUsesEnemyName: Boolean = false,
    val radarBattlePendingEnemyCounterAttack: Boolean = false,
    val radarBattlePendingCriticalMessage: Boolean = false,
    val radarBattleAnimTicksRemaining: Int = 0,
    val radarBattlePokemonSlot: Int = 0,
    val radarSwapCursor: Int = 0,
    val radarBattleMessageOffset: Int? = null,
    val radarBattleReturnToMenu: Boolean = false,
    val radarPendingCatchSuccess: Boolean? = null,
    val radarPendingCatchRouteSlot: Int? = null,
    val radarChainProgress: Int = 0,
    val radarChainTarget: Int = 1,
    val radarSignalLevel: Int = 1,
    val radarSignalRouteSlot: Int = 0,
    val radarPlayerHp: Int = 4,
    val radarEnemyHp: Int = 4,
    val dowsingCursor: Int = 0,
    val radarOutcome: Boolean? = null,
    val dowsingOutcome: Boolean? = null,
    val dowsingGameOver: Boolean = false,
    val dowsingWon: Boolean? = null,
    val dowsingMode: DowsingMode = DowsingMode.Search,
    val dowsingRevealCursor: Int = -1,
    val dowsingSwapCursor: Int = 0,
    val dowsingRevealTicksRemaining: Int = 0,
    val dowsingPendingFound: Boolean = false,
    val dowsingPendingNear: Boolean = false,
    val dowsingPendingStored: Boolean = false,
    val dowsingCheckedMask: Int = 0,
    val dowsingResultItemIndex: Int? = null,
    val radarSignalCursor: Int? = null,
    val radarResolvedCursor: Int? = null,
    val radarSignalTicksRemaining: Int = 0,
    val dowsingTargetCursor: Int = 0,
    val dowsingTargetItemIndex: Int = 0,
    val dowsingAttemptsRemaining: Int = 0,
    val dowsingFeedback: DowsingFeedback = DowsingFeedback.None,
    val caughtPokemonCount: Int = 0,
    val foundItemCount: Int = 0,
    val soundLevel: Int = 1,
    val shadeLevel: Int = 1,
    val settingsField: DeviceSettingsField = DeviceSettingsField.Sound,
    val settingsMode: DeviceSettingsMode = DeviceSettingsMode.SelectField,
    val cardPageIndex: Int = 0,
    val pokemonIndex: Int = 0,
) {
    fun selectedMenuItem(): DeviceMenuItem =
        DeviceMenuItem.entries[menuIndex.mod(DeviceMenuItem.entries.size)]
}
