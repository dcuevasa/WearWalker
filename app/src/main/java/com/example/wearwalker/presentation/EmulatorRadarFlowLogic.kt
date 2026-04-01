package com.example.wearwalker.presentation

import com.example.wearwalker.core.DeviceInteractionState
import com.example.wearwalker.core.DeviceOffsets
import com.example.wearwalker.core.RadarBattleAnimation
import com.example.wearwalker.core.RadarBattleAction
import com.example.wearwalker.core.RadarMode

internal sealed interface RadarScanEnterResolution {
    data class ResetToHome(
        val statusMessage: String,
        val outcome: Boolean?,
    ) : RadarScanEnterResolution

    data class UpdateState(
        val state: DeviceInteractionState,
        val statusMessage: String,
    ) : RadarScanEnterResolution
}

internal fun resolveRadarScanEnter(
    state: DeviceInteractionState,
    chainMax: Int,
    radarMaxHp: Int,
    textAppeared: Int,
    rollSignal: () -> RadarSignal,
    randomSignalCursor: (Int) -> Int,
    randomSignalWindowTicks: () -> Int,
): RadarScanEnterResolution {
    val signalCursor = state.radarSignalCursor
    val graceCursor = state.radarResolvedCursor
    if (signalCursor == null) {
        return if (state.radarOutcome == false) {
            RadarScanEnterResolution.ResetToHome(
                statusMessage = "It got away. Returning Home.",
                outcome = false,
            )
        } else {
            RadarScanEnterResolution.ResetToHome(
                statusMessage = "Signal already faded. Radar ended.",
                outcome = false,
            )
        }
    }

    val chosenCursor = state.radarCursor
    val matched = chosenCursor == signalCursor || chosenCursor == graceCursor
    if (!matched) {
        return RadarScanEnterResolution.UpdateState(
            state =
                state.copy(
                    radarOutcome = false,
                    radarResolvedCursor = signalCursor,
                    radarSignalCursor = null,
                    radarSignalTicksRemaining = 0,
                ),
            statusMessage = "It got away. Press ENTER to return Home.",
        )
    }

    val chainTarget = state.radarChainTarget.coerceIn(1, chainMax)
    val nextChainProgress = state.radarChainProgress + 1
    if (nextChainProgress < chainTarget) {
        val nextSignal = rollSignal()
        return RadarScanEnterResolution.UpdateState(
            state =
                state.copy(
                    radarChainProgress = nextChainProgress,
                    radarOutcome = true,
                    radarResolvedCursor = chosenCursor,
                    radarSignalCursor = randomSignalCursor(chosenCursor),
                    radarSignalTicksRemaining = randomSignalWindowTicks(),
                    radarSignalLevel = nextSignal.signalLevel,
                    radarSignalRouteSlot = nextSignal.routeSlot,
                ),
            statusMessage = "Rustling grass... keep tracking ($nextChainProgress/$chainTarget).",
        )
    }

    val routeSlot = state.radarSignalRouteSlot.coerceIn(0, DeviceOffsets.POKEMON_SLOT_COUNT - 1)
    return RadarScanEnterResolution.UpdateState(
        state =
            state.copy(
                radarMode = RadarMode.BattleMessage,
                radarBattleAction = RadarBattleAction.Attack,
                radarBattleAnimation = RadarBattleAnimation.None,
                radarBattleEnemyResponded = false,
                radarBattleMessageUsesEnemyName = false,
                radarBattlePendingEnemyCounterAttack = false,
                radarBattlePendingCriticalMessage = false,
                radarBattleAnimTicksRemaining = 0,
                radarBattlePokemonSlot = routeSlot,
                radarBattleMessageOffset = textAppeared,
                radarBattleReturnToMenu = false,
                radarPendingCatchSuccess = null,
                radarChainProgress = nextChainProgress,
                radarPlayerHp = radarMaxHp,
                radarEnemyHp = radarMaxHp,
                radarOutcome = true,
                radarSignalCursor = null,
                radarResolvedCursor = signalCursor,
                radarSignalTicksRemaining = 0,
            ),
        statusMessage = "Radar lock confirmed. Battle started.",
    )
}

internal data class RadarBattleTurnResolution(
    val playerHp: Int,
    val enemyHp: Int,
    val enemyResponded: Boolean,
    val messageUsesEnemyName: Boolean,
    val pendingEnemyCounterAttack: Boolean,
    val pendingCriticalMessage: Boolean,
    val messageOffset: Int,
    val returnHome: Boolean,
    val persistMessage: String,
    val shouldPersist: Boolean,
    val battleAnimation: RadarBattleAnimation,
    val applyLossPenalty: Boolean,
    val touchLastSyncNow: Boolean,
)

private fun attackPersistMessage(
    isCritical: Boolean,
    enemyResponded: Boolean,
    enemyHp: Int,
    radarMaxHp: Int,
): String {
    return if (isCritical && enemyResponded) {
        "Critical hit landed. Enemy struck back. Enemy HP: $enemyHp/$radarMaxHp."
    } else if (isCritical) {
        "Critical hit landed. Enemy HP: $enemyHp/$radarMaxHp."
    } else if (enemyResponded) {
        "Attack landed. Enemy struck back. Enemy HP: $enemyHp/$radarMaxHp."
    } else {
        "Attack landed. Enemy HP: $enemyHp/$radarMaxHp."
    }
}

internal fun resolveRadarAttackTurn(
    state: DeviceInteractionState,
    radarMaxHp: Int,
    enemyEvadeChancePercent: Int,
    criticalChancePercent: Int,
    textAttacked: Int,
    textTooStrong: Int,
    enemyEvadeRoll: Int,
    criticalRoll: Int,
): RadarBattleTurnResolution {
    var playerHp = state.radarPlayerHp.coerceIn(0, radarMaxHp)
    var enemyHp = state.radarEnemyHp.coerceIn(0, radarMaxHp)
    var enemyResponded = false
    var pendingEnemyCounterAttack = false
    var pendingCriticalMessage = false
    var messageOffset = textAttacked
    var returnHome = false
    var persistMessage = "Radar turn resolved."
    var shouldPersist = false
    var battleAnimation = RadarBattleAnimation.None
    var applyLossPenalty = false
    var touchLastSyncNow = false

    val enemyEvades = enemyEvadeRoll < enemyEvadeChancePercent
    if (enemyEvades) {
        battleAnimation = RadarBattleAnimation.AttackEnemyEvade
        enemyResponded = true
        pendingEnemyCounterAttack = true
        pendingCriticalMessage = false
        persistMessage = "Attack missed. Wild Pokemon evaded and is counterattacking."

        if (playerHp <= 1) {
            applyLossPenalty = true
            shouldPersist = true
        }

        return RadarBattleTurnResolution(
            playerHp = playerHp,
            enemyHp = enemyHp,
            enemyResponded = enemyResponded,
            messageUsesEnemyName = false,
            pendingEnemyCounterAttack = pendingEnemyCounterAttack,
            pendingCriticalMessage = pendingCriticalMessage,
            messageOffset = messageOffset,
            returnHome = returnHome,
            persistMessage = persistMessage,
            shouldPersist = shouldPersist,
            battleAnimation = battleAnimation,
            applyLossPenalty = applyLossPenalty,
            touchLastSyncNow = touchLastSyncNow,
        )
    }

    val isCritical = criticalRoll < criticalChancePercent
    val damage = if (isCritical) 2 else 1
    enemyHp = (enemyHp - damage).coerceAtLeast(0)
    enemyResponded = enemyHp > 0
    pendingCriticalMessage = isCritical

    if (enemyResponded) {
        playerHp = (playerHp - 1).coerceAtLeast(0)
    }
    pendingEnemyCounterAttack = enemyResponded && playerHp > 0

    battleAnimation = if (isCritical) RadarBattleAnimation.AttackCrit else RadarBattleAnimation.AttackHit

    if (playerHp <= 0) {
        messageOffset = textTooStrong
        returnHome = true
        pendingEnemyCounterAttack = false
        pendingCriticalMessage = false
        applyLossPenalty = true
        shouldPersist = true
        persistMessage = "Your Pokemon was overpowered."
    } else {
        messageOffset = textAttacked
        persistMessage = attackPersistMessage(isCritical, enemyResponded, enemyHp, radarMaxHp)
    }

    if (enemyHp <= 0) {
        returnHome = true
        shouldPersist = true
        pendingEnemyCounterAttack = false
        touchLastSyncNow = true
        persistMessage =
            if (enemyResponded) {
                "Enemy was defeated after trading blows."
            } else {
                "Enemy was defeated."
            }
    }

    if (!returnHome) {
        persistMessage = attackPersistMessage(isCritical, enemyResponded, enemyHp, radarMaxHp)
    }

    return RadarBattleTurnResolution(
        playerHp = playerHp,
        enemyHp = enemyHp,
        enemyResponded = enemyResponded,
        messageUsesEnemyName = false,
        pendingEnemyCounterAttack = pendingEnemyCounterAttack,
        pendingCriticalMessage = pendingCriticalMessage,
        messageOffset = messageOffset,
        returnHome = returnHome,
        persistMessage = persistMessage,
        shouldPersist = shouldPersist,
        battleAnimation = battleAnimation,
        applyLossPenalty = applyLossPenalty,
        touchLastSyncNow = touchLastSyncNow,
    )
}

internal fun resolveRadarEvadeTurn(
    state: DeviceInteractionState,
    counterChancePercent: Int,
    stareChancePercent: Int,
    textEvaded: Int,
    textGotAway: Int,
    roll: Int,
    radarMaxHp: Int,
): RadarBattleTurnResolution {
    val playerHp = state.radarPlayerHp.coerceIn(0, radarMaxHp)
    var enemyHp = state.radarEnemyHp.coerceIn(0, radarMaxHp)

    var messageOffset: Int
    var returnHome = false
    var battleAnimation: RadarBattleAnimation
    val persistMessage: String

    when {
        roll < counterChancePercent -> {
            enemyHp = (enemyHp - 1).coerceAtLeast(0)
            messageOffset = textEvaded
            battleAnimation = RadarBattleAnimation.EvadeCounter
            persistMessage = "Evaded and countered. Enemy HP: $enemyHp/$radarMaxHp."
        }

        roll < counterChancePercent + stareChancePercent -> {
            messageOffset = textEvaded
            battleAnimation = RadarBattleAnimation.EvadeStandoff
            persistMessage = "Both Pokemon hesitated."
        }

        else -> {
            messageOffset = textGotAway
            returnHome = true
            battleAnimation = RadarBattleAnimation.EvadeEnemyFlee
            persistMessage = "Wild Pokemon ran away."
        }
    }

    return RadarBattleTurnResolution(
        playerHp = playerHp,
        enemyHp = enemyHp,
        enemyResponded = false,
        messageUsesEnemyName = false,
        pendingEnemyCounterAttack = false,
        pendingCriticalMessage = false,
        messageOffset = messageOffset,
        returnHome = returnHome,
        persistMessage = persistMessage,
        shouldPersist = false,
        battleAnimation = battleAnimation,
        applyLossPenalty = false,
        touchLastSyncNow = false,
    )
}

internal data class RadarMessageEnterOffsets(
    val foundSomething: Int,
    val appeared: Int,
    val throwPokeball: Int,
    val gotAway: Int,
    val fled: Int,
    val wasTooStrong: Int,
    val criticalHit: Int,
    val attacked: Int,
)

internal sealed interface RadarBattleMessageEnterResolution {
    data class ResetToHome(
        val statusMessage: String,
        val outcome: Boolean?,
    ) : RadarBattleMessageEnterResolution

    data class RefreshOnly(
        val statusMessage: String,
    ) : RadarBattleMessageEnterResolution

    data class UpdateState(
        val state: DeviceInteractionState,
        val statusMessage: String,
    ) : RadarBattleMessageEnterResolution
}

internal fun resolveRadarBattleMessageEnter(
    state: DeviceInteractionState,
    isBattleAnimationActive: Boolean,
    offsets: RadarMessageEnterOffsets,
    enemyAttackAnimationTicks: Int,
): RadarBattleMessageEnterResolution {
    if (isBattleAnimationActive) {
        return RadarBattleMessageEnterResolution.RefreshOnly("Battle animation in progress.")
    }

    if (
        state.radarBattleMessageOffset == offsets.gotAway ||
        state.radarBattleMessageOffset == offsets.fled
    ) {
        return RadarBattleMessageEnterResolution.ResetToHome(
            statusMessage = "Wild Pokemon got away. Returning Home.",
            outcome = false,
        )
    }

    if (state.radarBattleMessageOffset == offsets.wasTooStrong) {
        return RadarBattleMessageEnterResolution.ResetToHome(
            statusMessage = "Your Pokemon was overpowered. Returning Home.",
            outcome = false,
        )
    }

    when (state.radarBattleMessageOffset) {
        offsets.foundSomething -> {
            return RadarBattleMessageEnterResolution.UpdateState(
                state =
                    state.copy(
                        radarMode = RadarMode.BattleMessage,
                        radarBattleMessageOffset = offsets.appeared,
                        radarBattleAnimation = RadarBattleAnimation.None,
                        radarBattleAnimTicksRemaining = 0,
                    ),
                statusMessage = "A wild Pokemon appeared.",
            )
        }

        offsets.throwPokeball -> {
            return RadarBattleMessageEnterResolution.RefreshOnly("Pokeball sequence in progress.")
        }

        offsets.appeared -> {
            return RadarBattleMessageEnterResolution.UpdateState(
                state =
                    state.copy(
                        radarMode = RadarMode.BattleMenu,
                        radarBattleMessageOffset = null,
                        radarBattleEnemyResponded = false,
                        radarBattleMessageUsesEnemyName = false,
                        radarBattlePendingEnemyCounterAttack = false,
                        radarBattlePendingCriticalMessage = false,
                        radarBattleAnimation = RadarBattleAnimation.None,
                        radarBattleAnimTicksRemaining = 0,
                    ),
                statusMessage = "Choose ATTACK, ENTER to CATCH, or EVADE.",
            )
        }
    }

    if (
        state.radarBattleMessageOffset == offsets.criticalHit &&
        state.radarBattlePendingEnemyCounterAttack
    ) {
        return RadarBattleMessageEnterResolution.UpdateState(
            state =
                state.copy(
                    radarBattleMessageOffset = offsets.attacked,
                    radarBattleMessageUsesEnemyName = true,
                    radarBattlePendingEnemyCounterAttack = false,
                    radarBattlePendingCriticalMessage = false,
                    radarBattleAnimation = RadarBattleAnimation.EnemyAttack,
                    radarBattleAnimTicksRemaining = enemyAttackAnimationTicks,
                ),
            statusMessage = "Enemy counterattack.",
        )
    }

    if (state.radarBattleReturnToMenu) {
        return RadarBattleMessageEnterResolution.ResetToHome(
            statusMessage = "Radar battle finished. Returning Home.",
            outcome = null,
        )
    }

    return RadarBattleMessageEnterResolution.UpdateState(
        state =
            state.copy(
                radarMode = RadarMode.BattleMenu,
                radarBattleMessageOffset = null,
                radarBattleEnemyResponded = false,
                radarBattleMessageUsesEnemyName = false,
                radarBattlePendingEnemyCounterAttack = false,
                radarBattlePendingCriticalMessage = false,
                radarBattleAnimation = RadarBattleAnimation.None,
                radarBattleAnimTicksRemaining = 0,
            ),
        statusMessage = "Choose ATTACK, EVADE or CATCH.",
    )
}
