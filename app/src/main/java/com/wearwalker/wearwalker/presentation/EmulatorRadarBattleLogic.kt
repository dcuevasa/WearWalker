package com.wearwalker.wearwalker.presentation

import com.wearwalker.wearwalker.core.DeviceInteractionState
import com.wearwalker.wearwalker.core.RadarBattleAnimation
import com.wearwalker.wearwalker.core.RadarMode

internal data class RadarBattleMessageOffsets(
    val criticalHit: Int,
    val attacked: Int,
    val evaded: Int,
    val wasTooStrong: Int,
    val blank: Int,
    val caught: Int,
    val fled: Int,
)

internal fun radarBattleAnimationTickCount(
    animation: RadarBattleAnimation,
    shortTicks: Int,
    mediumTicks: Int,
    wiggleTicks: Int,
    longTicks: Int,
): Int {
    return when (animation) {
        RadarBattleAnimation.None -> 0
        RadarBattleAnimation.AttackTrade,
        RadarBattleAnimation.EnemyAttack,
        RadarBattleAnimation.EvadeCounter,
        RadarBattleAnimation.EvadeStandoff,
            -> shortTicks

        RadarBattleAnimation.AttackHit,
        RadarBattleAnimation.AttackCrit,
        RadarBattleAnimation.EvadeEnemyFlee,
        RadarBattleAnimation.AttackEnemyEvade,
        RadarBattleAnimation.CatchThrow,
        RadarBattleAnimation.CatchSuccess,
            -> mediumTicks

        RadarBattleAnimation.CatchFail -> longTicks

        RadarBattleAnimation.CatchWiggle -> wiggleTicks
    }
}

internal fun resolveRadarBattleAnimationFinished(
    state: DeviceInteractionState,
    messages: RadarBattleMessageOffsets,
    tickCountForAnimation: (RadarBattleAnimation) -> Int,
): DeviceInteractionState {
    return when (state.radarBattleAnimation) {
        RadarBattleAnimation.AttackHit,
        RadarBattleAnimation.AttackCrit,
            -> {
                if (state.radarBattlePendingCriticalMessage) {
                    state.copy(
                        radarBattleMessageOffset = messages.criticalHit,
                        radarBattleMessageUsesEnemyName = false,
                        radarBattlePendingCriticalMessage = false,
                        radarBattleAnimation = RadarBattleAnimation.None,
                        radarBattleAnimTicksRemaining = 0,
                    )
                } else if (state.radarBattlePendingEnemyCounterAttack) {
                    state.copy(
                        radarBattleMessageOffset = messages.attacked,
                        radarBattleMessageUsesEnemyName = true,
                        radarBattlePendingEnemyCounterAttack = false,
                        radarBattlePendingCriticalMessage = false,
                        radarBattleAnimation = RadarBattleAnimation.EnemyAttack,
                        radarBattleAnimTicksRemaining =
                            tickCountForAnimation(RadarBattleAnimation.EnemyAttack),
                    )
                } else {
                    state.copy(
                        radarBattlePendingCriticalMessage = false,
                        radarBattleAnimation = RadarBattleAnimation.None,
                        radarBattleAnimTicksRemaining = 0,
                    )
                }
            }

        RadarBattleAnimation.AttackEnemyEvade -> {
            if (state.radarBattlePendingEnemyCounterAttack) {
                val nextPlayerHp = (state.radarPlayerHp - 1).coerceAtLeast(0)
                state.copy(
                    radarPlayerHp = nextPlayerHp,
                    radarBattleMessageOffset = messages.evaded,
                    radarBattleReturnToMenu = false,
                    radarBattleEnemyResponded = true,
                    radarBattleMessageUsesEnemyName = false,
                    radarBattlePendingEnemyCounterAttack = false,
                    radarBattlePendingCriticalMessage = false,
                    radarBattleAnimation = RadarBattleAnimation.EnemyAttack,
                    radarBattleAnimTicksRemaining =
                        tickCountForAnimation(RadarBattleAnimation.EnemyAttack),
                )
            } else {
                state.copy(
                    radarBattleAnimation = RadarBattleAnimation.None,
                    radarBattleAnimTicksRemaining = 0,
                )
            }
        }

        RadarBattleAnimation.EnemyAttack -> {
            if (state.radarPlayerHp <= 0) {
                state.copy(
                    radarBattleMessageOffset = messages.wasTooStrong,
                    radarBattleReturnToMenu = true,
                    radarBattleEnemyResponded = false,
                    radarBattleMessageUsesEnemyName = false,
                    radarBattlePendingEnemyCounterAttack = false,
                    radarBattlePendingCriticalMessage = false,
                    radarBattleAnimation = RadarBattleAnimation.None,
                    radarBattleAnimTicksRemaining = 0,
                )
            } else {
                state.copy(
                    radarBattlePendingCriticalMessage = false,
                    radarBattleAnimation = RadarBattleAnimation.None,
                    radarBattleAnimTicksRemaining = 0,
                )
            }
        }

        RadarBattleAnimation.CatchThrow -> {
            if (state.radarPendingCatchSuccess == true) {
                state.copy(
                    radarBattleAnimation = RadarBattleAnimation.CatchWiggle,
                    radarBattleAnimTicksRemaining =
                        tickCountForAnimation(RadarBattleAnimation.CatchWiggle),
                )
            } else {
                state.copy(
                    radarBattleMessageOffset = messages.blank,
                    radarBattleReturnToMenu = false,
                    radarBattleEnemyResponded = false,
                    radarBattleMessageUsesEnemyName = false,
                    radarBattlePendingEnemyCounterAttack = false,
                    radarBattlePendingCriticalMessage = false,
                    radarPendingCatchSuccess = null,
                    radarPendingCatchRouteSlot = null,
                    radarBattleAnimation = RadarBattleAnimation.CatchFail,
                    radarBattleAnimTicksRemaining =
                        tickCountForAnimation(RadarBattleAnimation.CatchFail),
                )
            }
        }

        RadarBattleAnimation.CatchWiggle -> {
            val overflowRouteSlot = state.radarPendingCatchRouteSlot
            if (overflowRouteSlot != null) {
                state.copy(
                    radarMode = RadarMode.BattleSwap,
                    radarSwapCursor = 1,
                    radarBattleMessageOffset = null,
                    radarBattleReturnToMenu = false,
                    radarBattleEnemyResponded = false,
                    radarBattleMessageUsesEnemyName = false,
                    radarBattlePendingEnemyCounterAttack = false,
                    radarBattlePendingCriticalMessage = false,
                    radarPendingCatchSuccess = null,
                    radarBattleAnimation = RadarBattleAnimation.None,
                    radarBattleAnimTicksRemaining = 0,
                )
            } else {
                state.copy(
                    radarBattleMessageOffset = messages.caught,
                    radarBattleReturnToMenu = true,
                    radarBattleEnemyResponded = false,
                    radarBattleMessageUsesEnemyName = false,
                    radarBattlePendingEnemyCounterAttack = false,
                    radarBattlePendingCriticalMessage = false,
                    radarPendingCatchSuccess = null,
                    radarPendingCatchRouteSlot = null,
                    radarBattleAnimation = RadarBattleAnimation.CatchSuccess,
                    radarBattleAnimTicksRemaining =
                        tickCountForAnimation(RadarBattleAnimation.CatchSuccess),
                )
            }
        }

        RadarBattleAnimation.CatchSuccess,
        RadarBattleAnimation.CatchFail,
            -> {
                if (state.radarBattleAnimation == RadarBattleAnimation.CatchFail) {
                    state.copy(
                        radarBattleMessageOffset = messages.fled,
                        radarBattleReturnToMenu = true,
                        radarBattleEnemyResponded = false,
                        radarBattleMessageUsesEnemyName = false,
                        radarBattlePendingEnemyCounterAttack = false,
                        radarBattlePendingCriticalMessage = false,
                        radarPendingCatchRouteSlot = null,
                        radarBattleAnimation = RadarBattleAnimation.None,
                        radarBattleAnimTicksRemaining = 0,
                    )
                } else {
                    state.copy(
                        radarBattleEnemyResponded = false,
                        radarBattleMessageUsesEnemyName = false,
                        radarBattlePendingEnemyCounterAttack = false,
                        radarBattlePendingCriticalMessage = false,
                        radarPendingCatchRouteSlot = null,
                        radarBattleAnimation = RadarBattleAnimation.None,
                        radarBattleAnimTicksRemaining = 0,
                    )
                }
            }

        else -> {
            state.copy(
                radarBattleAnimation = RadarBattleAnimation.None,
                radarBattleAnimTicksRemaining = 0,
            )
        }
    }
}
