package com.wearwalker.wearwalker.presentation

import com.wearwalker.wearwalker.core.DeviceBinary
import com.wearwalker.wearwalker.core.DeviceOffsets
import com.wearwalker.wearwalker.core.HomeEventType
import kotlin.random.Random

internal data class RadarSignal(
    val routeSlot: Int,
    val signalLevel: Int,
)

internal fun randomTicksInRange(
    minTicks: Int,
    maxTicks: Int,
): Int {
    return Random.nextInt(minTicks, maxTicks + 1)
}

internal fun chooseRandomHomeEventType(): HomeEventType {
    val roll = Random.nextInt(100)
    return when {
        roll < 35 -> HomeEventType.Watts10
        roll < 65 -> HomeEventType.Watts20
        roll < 80 -> HomeEventType.Watts50
        else -> HomeEventType.Item
    }
}

internal fun randomRouteItemIndexFromEeprom(eeprom: ByteArray): Int {
    val availableRouteItems =
        (0 until DeviceOffsets.ROUTE_ITEM_COUNT)
            .filter { index ->
                DeviceBinary.readRouteItemId(eeprom, index) != 0
            }

    return if (availableRouteItems.isEmpty()) {
        Random.nextInt(DeviceOffsets.ROUTE_ITEM_COUNT)
    } else {
        availableRouteItems.random()
    }
}

internal fun computeCurrentWalkStepCount(eeprom: ByteArray): Int {
    val todaySteps = DeviceBinary.readHealthTodaySteps(eeprom)
    if (todaySteps > 0) {
        return todaySteps
    }

    val identitySteps =
        DeviceBinary.readU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
    if (identitySteps > 0) {
        return identitySteps
    }

    return DeviceBinary.readHealthLifetimeSteps(eeprom)
}

internal fun hasWalkingCompanionInEeprom(eeprom: ByteArray): Boolean {
    return DeviceBinary.readWalkingCompanionSpecies(eeprom) != 0
}

internal fun rollDowsingRouteItemIndexFromEeprom(eeprom: ByteArray): Int {
    val stepCount = computeCurrentWalkStepCount(eeprom)

    var fallbackIndex = -1

    for (index in 0 until DeviceOffsets.ROUTE_ITEM_COUNT) {
        val itemId = DeviceBinary.readRouteItemId(eeprom, index)
        if (itemId == 0) {
            continue
        }

        fallbackIndex = index

        val minSteps = DeviceBinary.readRouteItemMinSteps(eeprom, index).coerceAtLeast(0)
        if (stepCount < minSteps) {
            continue
        }

        val chance = DeviceBinary.readRouteItemChance(eeprom, index).coerceIn(0, 100)
        if (chance <= 0) {
            continue
        }

        if (Random.nextInt(100) < chance) {
            return index
        }
    }

    return if (fallbackIndex >= 0) {
        fallbackIndex
    } else {
        randomRouteItemIndexFromEeprom(eeprom)
    }
}

internal fun radarSignalLevelForRouteSlot(
    eeprom: ByteArray,
    slot: Int,
): Int {
    val available =
        (0 until DeviceOffsets.COMPANION_SLOT_COUNT)
            .mapNotNull { candidate ->
                val species = DeviceBinary.readRouteCompanionSpecies(eeprom, candidate)
                if (species == 0) {
                    null
                } else {
                    val chance = DeviceBinary.readRouteCompanionChance(eeprom, candidate).coerceIn(0, 100)
                    candidate to chance
                }
            }
            .sortedByDescending { (_, chance) -> chance }

    val rank = available.indexOfFirst { (candidate, _) -> candidate == slot }
    return when (rank) {
        0 -> 1
        1 -> 2
        2 -> 3
        else -> (slot + 1).coerceIn(1, 3)
    }
}

internal fun rollRadarRouteSlotFromEeprom(eeprom: ByteArray): Int {
    val stepCount = computeCurrentWalkStepCount(eeprom)
    var fallbackSlot = -1
    val weighted = mutableListOf<Pair<Int, Int>>()

    for (slot in 0 until DeviceOffsets.COMPANION_SLOT_COUNT) {
        val species = DeviceBinary.readRouteCompanionSpecies(eeprom, slot)
        if (species == 0) {
            continue
        }

        if (fallbackSlot < 0) {
            fallbackSlot = slot
        }

        val minSteps = DeviceBinary.readRouteCompanionMinSteps(eeprom, slot).coerceAtLeast(0)
        if (stepCount < minSteps) {
            continue
        }

        val chance = DeviceBinary.readRouteCompanionChance(eeprom, slot).coerceIn(0, 100)
        if (chance <= 0) {
            continue
        }

        weighted += slot to chance
    }

    if (weighted.isEmpty()) {
        return if (fallbackSlot >= 0) fallbackSlot else 0
    }

    val totalWeight = weighted.sumOf { it.second }.coerceAtLeast(1)
    var roll = Random.nextInt(totalWeight)
    for ((slot, weight) in weighted) {
        roll -= weight
        if (roll < 0) {
            return slot
        }
    }

    return weighted.last().first
}

internal fun rollRadarSignalFromEeprom(eeprom: ByteArray): RadarSignal {
    val slot = rollRadarRouteSlotFromEeprom(eeprom)
    return RadarSignal(
        routeSlot = slot,
        signalLevel = radarSignalLevelForRouteSlot(eeprom, slot),
    )
}

internal fun randomRadarSignalCursor(exclude: Int): Int {
    val options = (0..3).filter { candidate -> candidate != exclude }
    return if (options.isEmpty()) {
        Random.nextInt(4)
    } else {
        options.random()
    }
}

internal fun radarCatchChanceByEnemyHp(enemyHp: Int): Int {
    return when (enemyHp.coerceIn(0, 4)) {
        4 -> 25
        3 -> 45
        2 -> 70
        1 -> 90
        else -> 98
    }
}

internal fun wrapCircularIndex(
    value: Int,
    size: Int,
): Int {
    if (size <= 0) return 0
    val mod = value % size
    return if (mod < 0) mod + size else mod
}
