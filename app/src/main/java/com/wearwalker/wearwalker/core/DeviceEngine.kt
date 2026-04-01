package com.wearwalker.wearwalker.core

data class DeviceSnapshot(
    val ownerName: String,
    val steps: Int,
    val watts: Int,
    val isRegistered: Boolean,
    val hasCompanion: Boolean,
    val protocolVersion: Int,
    val protocolSubVersion: Int,
    val lastSyncEpochSeconds: Long,
    val mirrorInSync: Boolean,
)

data class StepMutationResult(
    val steps: Int,
    val watts: Int,
    val generatedWatts: Int,
    val wattRemainder: Int,
    val friendshipGain: Int,
    val deferredExpSteps: Int,
)

data class DailyRolloverResult(
    val rolledDays: Int,
    val totalDays: Int,
)

class DeviceEngine(initialEeprom: ByteArray) {
    private var eeprom: ByteArray = initialEeprom.copyOf()

    init {
        require(eeprom.size == DeviceOffsets.EEPROM_SIZE) {
            "Expected EEPROM size ${DeviceOffsets.EEPROM_SIZE}, got ${eeprom.size}"
        }
    }

    fun replaceEeprom(newEeprom: ByteArray) {
        require(newEeprom.size == DeviceOffsets.EEPROM_SIZE) {
            "Expected EEPROM size ${DeviceOffsets.EEPROM_SIZE}, got ${newEeprom.size}"
        }
        eeprom = newEeprom.copyOf()
    }

    fun exportEeprom(): ByteArray = eeprom.copyOf()

    fun snapshot(mirrorInSync: Boolean): DeviceSnapshot {
        val ownerName = DeviceBinary.readDeviceTextFixed(
            data = eeprom,
            offset = DeviceOffsets.IDENTITY_OWNER_NAME_OFFSET,
            maxChars = 8,
        ).ifBlank {
            "Unknown"
        }

        val flags = eeprom[DeviceOffsets.IDENTITY_FLAGS_OFFSET].toInt() and 0xFF
        val identityStepCount =
            DeviceBinary.readU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET)
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
        val todaySteps = DeviceBinary.readHealthTodaySteps(eeprom)
        val lifetimeSteps = DeviceBinary.readHealthLifetimeSteps(eeprom)
        val stepCount =
            when {
                todaySteps > 0 -> todaySteps
                identityStepCount > 0 -> identityStepCount
                else -> lifetimeSteps
            }
        val watts = DeviceBinary.readCurrentWatts(eeprom)

        return DeviceSnapshot(
            ownerName = ownerName,
            steps = stepCount,
            watts = watts,
            isRegistered = (flags and 0x01) != 0,
            hasCompanion = (flags and 0x02) != 0,
            protocolVersion = eeprom[DeviceOffsets.IDENTITY_PROTOCOL_VERSION_OFFSET].toInt() and 0xFF,
            protocolSubVersion = eeprom[DeviceOffsets.IDENTITY_PROTOCOL_SUB_VERSION_OFFSET].toInt() and 0xFF,
            lastSyncEpochSeconds = DeviceBinary.readLastSyncSeconds(eeprom),
            mirrorInSync = mirrorInSync,
        )
    }

    fun addSteps(delta: Int): Int {
        if (delta <= 0) return currentSteps()

        val current = DeviceBinary.readU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET)
        val next = (current + delta.toLong()).coerceAtMost(0xFFFF_FFFFL)
        DeviceBinary.writeU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET, next)

        syncGeneralDataMirror()
        return next.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun applyStepDelta(
        delta: Int,
        wattRemainder: Int,
    ): StepMutationResult {
        val normalizedRemainder = wattRemainder.coerceIn(0, 19)
        if (delta <= 0) {
            return StepMutationResult(
                steps = currentSteps(),
                watts = currentWatts(),
                generatedWatts = 0,
                wattRemainder = normalizedRemainder,
                friendshipGain = 0,
                deferredExpSteps = 0,
            )
        }

        val boundedDelta = delta.coerceAtLeast(0)

        val currentIdentitySteps =
            DeviceBinary.readU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET)
                .coerceAtMost(0xFFFF_FFFFL)
                .toInt()
        val nextIdentitySteps =
            (currentIdentitySteps.toLong() + boundedDelta.toLong())
                .coerceAtMost(0xFFFF_FFFFL)
                .toInt()
        DeviceBinary.writeU32BE(
            data = eeprom,
            offset = DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET,
            value = nextIdentitySteps.toLong(),
        )

        val currentTodaySteps = DeviceBinary.readHealthTodaySteps(eeprom)
        val nextTodaySteps =
            (currentTodaySteps.toLong() + boundedDelta.toLong())
                .coerceAtMost(0xFFFF_FFFFL)
                .toInt()
        DeviceBinary.writeHealthTodaySteps(eeprom, nextTodaySteps)

        val currentLifetimeSteps = DeviceBinary.readHealthLifetimeSteps(eeprom)
        val nextLifetimeSteps =
            (currentLifetimeSteps.toLong() + boundedDelta.toLong())
                .coerceAtMost(0xFFFF_FFFFL)
                .toInt()
        DeviceBinary.writeHealthLifetimeSteps(eeprom, nextLifetimeSteps)

        val convertibleSteps = normalizedRemainder + boundedDelta
        val generatedWatts = convertibleSteps / 20
        val nextRemainder = convertibleSteps % 20
        if (generatedWatts > 0) {
            val nextWatts = (currentWatts() + generatedWatts).coerceAtMost(0xFFFF)
            DeviceBinary.writeCurrentWatts(eeprom, nextWatts)
        }

        val friendshipGain = incrementWalkingCompanionFriendship(boundedDelta)
        syncGeneralDataMirror()

        return StepMutationResult(
            steps = nextTodaySteps,
            watts = currentWatts(),
            generatedWatts = generatedWatts,
            wattRemainder = nextRemainder,
            friendshipGain = friendshipGain,
            deferredExpSteps = boundedDelta,
        )
    }

    fun applyDailyRollover(daysElapsed: Int): DailyRolloverResult {
        if (daysElapsed <= 0) {
            return DailyRolloverResult(
                rolledDays = 0,
                totalDays = DeviceBinary.readHealthTotalDays(eeprom),
            )
        }

        val boundedDays = daysElapsed.coerceAtMost(365)
        repeat(boundedDays) {
            val todaySteps = DeviceBinary.readHealthTodaySteps(eeprom)
            for (day in DeviceOffsets.STEP_HISTORY_DAYS - 1 downTo 1) {
                val previousSteps = DeviceBinary.readStepHistoryForDay(eeprom, day - 1)
                DeviceBinary.writeStepHistoryForDay(eeprom, day, previousSteps)
            }
            DeviceBinary.writeStepHistoryForDay(eeprom, 0, todaySteps)
            DeviceBinary.writeHealthTodaySteps(eeprom, 0)

            val totalDays = (DeviceBinary.readHealthTotalDays(eeprom) + 1).coerceAtMost(0xFFFF)
            DeviceBinary.writeHealthTotalDays(eeprom, totalDays)
        }

        syncGeneralDataMirror()
        return DailyRolloverResult(
            rolledDays = boundedDays,
            totalDays = DeviceBinary.readHealthTotalDays(eeprom),
        )
    }

    fun addWatts(delta: Int): Int {
        if (delta <= 0) return currentWatts()

        val current = currentWatts()
        val next = (current + delta).coerceAtMost(0xFFFF)
        DeviceBinary.writeCurrentWatts(eeprom, next)
        syncGeneralDataMirror()
        return next
    }

    fun currentWattsValue(): Int {
        return currentWatts()
    }

    fun spendWatts(cost: Int): Boolean {
        if (cost <= 0) return true

        val current = currentWatts()
        if (current < cost) {
            return false
        }

        DeviceBinary.writeCurrentWatts(eeprom, current - cost)
        syncGeneralDataMirror()
        return true
    }

    fun recordCaughtCompanionFromRoute(routeSlot: Int): Boolean {
        val recorded = DeviceBinary.recordCaughtCompanionFromRoute(eeprom, routeSlot)
        if (recorded) {
            syncGeneralDataMirror()
        }
        return recorded
    }

    fun hasFreeCaughtCompanionSlot(): Boolean {
        return DeviceBinary.hasFreeCaughtCompanionSlot(eeprom)
    }

    fun replaceCaughtCompanionWithRoute(
        caughtSlot: Int,
        routeSlot: Int,
    ): Boolean {
        val replaced = DeviceBinary.replaceCaughtCompanionWithRoute(eeprom, caughtSlot, routeSlot)
        if (replaced) {
            syncGeneralDataMirror()
        }
        return replaced
    }

    fun recordDowsedItemFromRoute(itemIndex: Int): Boolean {
        val recorded = DeviceBinary.recordDowsedItemFromRoute(eeprom, itemIndex)
        if (recorded) {
            syncGeneralDataMirror()
        }
        return recorded
    }

    fun hasFreeDowsedItemSlot(): Boolean {
        return DeviceBinary.hasFreeDowsedItemSlot(eeprom)
    }

    fun replaceDowsedItemWithRoute(
        dowsedSlot: Int,
        routeItemIndex: Int,
    ): Boolean {
        val replaced = DeviceBinary.replaceDowsedItemWithRoute(eeprom, dowsedSlot, routeItemIndex)
        if (replaced) {
            syncGeneralDataMirror()
        }
        return replaced
    }

    fun setLastSyncNow(epochSeconds: Long) {
        DeviceBinary.writeU32BE(
            data = eeprom,
            offset = DeviceOffsets.IDENTITY_LAST_SYNC_OFFSET,
            value = epochSeconds.coerceIn(0L, 0xFFFF_FFFFL),
        )
        syncGeneralDataMirror()
    }

    private fun currentSteps(): Int {
        return DeviceBinary.readU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun currentWatts(): Int {
        return DeviceBinary.readCurrentWatts(eeprom)
    }

    private fun incrementWalkingCompanionFriendship(delta: Int): Int {
        if (delta <= 0) {
            return 0
        }

        val hasWalkingCompanion = DeviceBinary.readWalkingCompanionSpecies(eeprom) != 0
        if (!hasWalkingCompanion) {
            return 0
        }

        val currentFriendship = DeviceBinary.readWalkingCompanionFriendship(eeprom)
        val nextFriendship = (currentFriendship + delta).coerceIn(0, 0xFF)
        DeviceBinary.writeWalkingCompanionFriendship(eeprom, nextFriendship)
        return nextFriendship - currentFriendship
    }

    private fun syncGeneralDataMirror() {
        System.arraycopy(
            eeprom,
            DeviceOffsets.GENERAL_DATA_PRIMARY_OFFSET,
            eeprom,
            DeviceOffsets.GENERAL_DATA_MIRROR_OFFSET,
            DeviceOffsets.GENERAL_DATA_LENGTH,
        )
    }
}
