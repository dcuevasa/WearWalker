package com.example.wearwalker.presentation

import com.example.wearwalker.core.DeviceInteractionState
import com.example.wearwalker.core.DowsingFeedback
import com.example.wearwalker.core.DowsingMode

internal fun resetDowsingStateToHome(state: DeviceInteractionState): DeviceInteractionState {
    return state.copy(
        screen = com.example.wearwalker.core.DeviceScreen.Home,
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
        dowsingIdleTicksRemaining = 0,
    )
}

internal fun startDowsingReveal(
    state: DeviceInteractionState,
    selectedCursor: Int,
    found: Boolean,
    near: Boolean,
    stored: Boolean,
    resolvedItemIndex: Int,
    checkedMask: Int,
    remainingAttempts: Int,
    revealTicks: Int,
): DeviceInteractionState {
    return state.copy(
        dowsingMode = DowsingMode.Reveal,
        dowsingRevealCursor = selectedCursor,
        dowsingRevealTicksRemaining = revealTicks,
        dowsingPendingFound = found,
        dowsingPendingNear = near,
        dowsingPendingStored = stored,
        dowsingTargetItemIndex = resolvedItemIndex,
        dowsingCheckedMask = checkedMask,
        dowsingAttemptsRemaining = remainingAttempts,
        dowsingFeedback = DowsingFeedback.None,
    )
}

internal fun enterDowsingHintState(state: DeviceInteractionState): DeviceInteractionState {
    return state.copy(
        dowsingMode = DowsingMode.HintMessage,
        dowsingFeedback = if (state.dowsingPendingNear) DowsingFeedback.Near else DowsingFeedback.Far,
    )
}

internal fun enterDowsingSearchState(state: DeviceInteractionState): DeviceInteractionState {
    return state.copy(
        dowsingMode = DowsingMode.Search,
        dowsingRevealCursor = -1,
        dowsingRevealTicksRemaining = 0,
        dowsingPendingFound = false,
        dowsingPendingNear = false,
        dowsingPendingStored = false,
        dowsingFeedback = DowsingFeedback.None,
    )
}

internal fun enterDowsingSwapState(
    state: DeviceInteractionState,
    pendingItemIndex: Int,
): DeviceInteractionState {
    return state.copy(
        dowsingMode = DowsingMode.Swap,
        dowsingSwapCursor = 0,
        dowsingRevealCursor = -1,
        dowsingRevealTicksRemaining = 0,
        dowsingResultItemIndex = pendingItemIndex,
    )
}
