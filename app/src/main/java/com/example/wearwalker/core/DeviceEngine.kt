package com.example.wearwalker.core

data class DeviceSnapshot(
    val trainerName: String,
    val steps: Int,
    val watts: Int,
    val isRegistered: Boolean,
    val hasPokemon: Boolean,
    val protocolVersion: Int,
    val protocolSubVersion: Int,
    val lastSyncEpochSeconds: Long,
    val mirrorInSync: Boolean,
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
        val trainerName = DeviceBinary.readDeviceTextFixed(
            data = eeprom,
            offset = DeviceOffsets.IDENTITY_TRAINER_NAME_OFFSET,
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
            trainerName = trainerName,
            steps = stepCount,
            watts = watts,
            isRegistered = (flags and 0x01) != 0,
            hasPokemon = (flags and 0x02) != 0,
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

    fun recordCaughtPokemonFromRoute(routeSlot: Int): Boolean {
        val recorded = DeviceBinary.recordCaughtPokemonFromRoute(eeprom, routeSlot)
        if (recorded) {
            syncGeneralDataMirror()
        }
        return recorded
    }

    fun hasFreeCaughtPokemonSlot(): Boolean {
        return DeviceBinary.hasFreeCaughtPokemonSlot(eeprom)
    }

    fun replaceCaughtPokemonWithRoute(
        caughtSlot: Int,
        routeSlot: Int,
    ): Boolean {
        val replaced = DeviceBinary.replaceCaughtPokemonWithRoute(eeprom, caughtSlot, routeSlot)
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
