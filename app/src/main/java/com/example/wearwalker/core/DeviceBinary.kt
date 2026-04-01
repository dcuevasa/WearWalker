package com.example.wearwalker.core

object DeviceBinary {
    fun readU16LE(data: ByteArray, offset: Int): Int {
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        return b0 or (b1 shl 8)
    }

    fun writeU16LE(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    fun readU16BE(data: ByteArray, offset: Int): Int {
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        return (b0 shl 8) or b1
    }

    fun readU32BE(data: ByteArray, offset: Int): Long {
        val b0 = data[offset].toLong() and 0xFF
        val b1 = data[offset + 1].toLong() and 0xFF
        val b2 = data[offset + 2].toLong() and 0xFF
        val b3 = data[offset + 3].toLong() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readU32LE(data: ByteArray, offset: Int): Long {
        val b0 = data[offset].toLong() and 0xFF
        val b1 = data[offset + 1].toLong() and 0xFF
        val b2 = data[offset + 2].toLong() and 0xFF
        val b3 = data[offset + 3].toLong() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun writeU16BE(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }

    fun writeU32BE(data: ByteArray, offset: Int, value: Long) {
        data[offset] = ((value ushr 24) and 0xFF).toByte()
        data[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        data[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }

    fun readUtf16LeFixed(data: ByteArray, offset: Int, maxChars: Int): String {
        val builder = StringBuilder(maxChars)
        for (index in 0 until maxChars) {
            val codeUnit = readU16LE(data, offset + index * 2)
            if (codeUnit == 0x0000 || codeUnit == 0xFFFF) {
                break
            }
            if (codeUnit in 0x20..0xFFFD) {
                builder.append(codeUnit.toChar())
            }
        }
        return builder.toString().trim()
    }

    fun readDeviceTextFixed(data: ByteArray, offset: Int, maxChars: Int): String {
        val builder = StringBuilder(maxChars)
        for (index in 0 until maxChars) {
            val codeUnit = readU16LE(data, offset + index * 2)
            val decoded = decodeDeviceCodeUnit(codeUnit) ?: break
            builder.append(decoded)
        }
        return builder.toString().trim()
    }

    fun readCurrentWatts(eeprom: ByteArray): Int {
        val healthWatts = readU16BE(eeprom, DeviceOffsets.HEALTH_CUR_WATTS_OFFSET)
        val generalWatts = readU16BE(eeprom, DeviceOffsets.GENERAL_WATTS_OFFSET)
        val sessionWatts = readU16BE(eeprom, DeviceOffsets.SESSION_WATTS_OFFSET)

        val values =
            listOf(sessionWatts, healthWatts, generalWatts)
                .filter { candidate ->
                    candidate > 0 && candidate <= 0xFFFF && candidate != 0x07FF && candidate != 0xFFFF
                }

        if (values.isEmpty()) {
            return 0
        }

        val grouped = values.groupingBy { it }.eachCount()
        val repeated = grouped.entries.firstOrNull { it.value >= 2 }?.key
        if (repeated != null) {
            return repeated
        }

        if (sessionWatts in values && sessionWatts <= 9_999) {
            return sessionWatts
        }

        return values.minOrNull() ?: 0
    }

    fun writeCurrentWatts(eeprom: ByteArray, watts: Int) {
        val value = watts.coerceIn(0, 0xFFFF)
        writeU16BE(eeprom, DeviceOffsets.HEALTH_CUR_WATTS_OFFSET, value)
        writeU16BE(eeprom, DeviceOffsets.GENERAL_WATTS_OFFSET, value)
        writeU16BE(eeprom, DeviceOffsets.SESSION_WATTS_OFFSET, value)
    }

    fun readLastSyncSeconds(eeprom: ByteArray): Long {
        val healthValue = readU32BE(eeprom, DeviceOffsets.HEALTH_LAST_SYNC_OFFSET)
        val generalValue = readU32BE(eeprom, DeviceOffsets.GENERAL_LAST_SYNC_OFFSET)
        val identityValue = readU32BE(eeprom, DeviceOffsets.IDENTITY_LAST_SYNC_OFFSET)
        return when {
            healthValue != 0L -> healthValue
            generalValue != 0L -> generalValue
            else -> identityValue
        }
    }

    fun readHealthTodaySteps(eeprom: ByteArray): Int {
        return readU32BE(eeprom, DeviceOffsets.HEALTH_TODAY_STEPS_OFFSET)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun readHealthLifetimeSteps(eeprom: ByteArray): Int {
        return readU32BE(eeprom, DeviceOffsets.HEALTH_LIFETIME_STEPS_OFFSET)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun readHealthTotalDays(eeprom: ByteArray): Int {
        return readU16BE(eeprom, DeviceOffsets.HEALTH_TOTAL_DAYS_OFFSET)
    }

    fun countCaughtPokemon(eeprom: ByteArray): Int {
        var count = 0
        for (slot in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
            if (readCaughtPokemonSpecies(eeprom, slot) != 0) {
                count += 1
            }
        }
        return count
    }

    fun countFoundItems(eeprom: ByteArray): Int {
        var count = 0
        for (slot in 0 until DeviceOffsets.DOWSED_ITEM_COUNT) {
            if (readDowsedItemId(eeprom, slot) != 0) {
                count += 1
            }
        }
        for (slot in 0 until DeviceOffsets.GIFTED_ITEM_COUNT) {
            if (readGiftedItemId(eeprom, slot) != 0) {
                count += 1
            }
        }
        return count
    }

    fun readWalkingPokemonSpecies(eeprom: ByteArray): Int {
        return readU16LE(eeprom, DeviceOffsets.ROUTE_INFO_OFFSET)
    }

    fun readCaughtPokemonSpecies(eeprom: ByteArray, slot: Int): Int {
        if (slot !in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) return 0
        val offset = DeviceOffsets.CAUGHT_POKEMON_OFFSET + slot * DeviceOffsets.POKEMON_SUMMARY_SIZE
        return readU16LE(eeprom, offset)
    }

    fun readRoutePokemonSpecies(eeprom: ByteArray, slot: Int): Int {
        if (slot !in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) return 0
        val offset = DeviceOffsets.ROUTE_POKEMON_LIST_OFFSET + slot * DeviceOffsets.POKEMON_SUMMARY_SIZE
        return readU16LE(eeprom, offset)
    }

    fun readRoutePokemonMinSteps(eeprom: ByteArray, slot: Int): Int {
        if (slot !in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) return 0
        val offset = DeviceOffsets.ROUTE_INFO_OFFSET + 0x82 + slot * 2
        return readU16LE(eeprom, offset)
    }

    fun readRoutePokemonChance(eeprom: ByteArray, slot: Int): Int {
        if (slot !in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) return 0
        val offset = DeviceOffsets.ROUTE_INFO_OFFSET + 0x88 + slot
        return readUnsignedByte(eeprom, offset)
    }

    fun findRouteSlotForSpecies(eeprom: ByteArray, species: Int): Int? {
        if (species == 0) return null
        for (slot in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
            if (readRoutePokemonSpecies(eeprom, slot) == species) {
                return slot
            }
        }
        return null
    }

    fun recordCaughtPokemonFromRoute(eeprom: ByteArray, routeSlot: Int): Boolean {
        if (routeSlot !in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
            return false
        }

        val sourceOffset =
            DeviceOffsets.ROUTE_POKEMON_LIST_OFFSET + routeSlot * DeviceOffsets.POKEMON_SUMMARY_SIZE
        val species = readU16LE(eeprom, sourceOffset)
        if (species == 0) {
            return false
        }

        val targetSlot = firstEmptyCaughtSlot(eeprom) ?: return false
        val targetOffset =
            DeviceOffsets.CAUGHT_POKEMON_OFFSET + targetSlot * DeviceOffsets.POKEMON_SUMMARY_SIZE
        System.arraycopy(eeprom, sourceOffset, eeprom, targetOffset, DeviceOffsets.POKEMON_SUMMARY_SIZE)
        return true
    }

    fun hasFreeCaughtPokemonSlot(eeprom: ByteArray): Boolean {
        return firstEmptyCaughtSlot(eeprom) != null
    }

    fun replaceCaughtPokemonWithRoute(
        eeprom: ByteArray,
        caughtSlot: Int,
        routeSlot: Int,
    ): Boolean {
        if (caughtSlot !in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
            return false
        }
        if (routeSlot !in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
            return false
        }

        val sourceOffset =
            DeviceOffsets.ROUTE_POKEMON_LIST_OFFSET + routeSlot * DeviceOffsets.POKEMON_SUMMARY_SIZE
        val species = readU16LE(eeprom, sourceOffset)
        if (species == 0) {
            return false
        }

        val targetOffset =
            DeviceOffsets.CAUGHT_POKEMON_OFFSET + caughtSlot * DeviceOffsets.POKEMON_SUMMARY_SIZE
        System.arraycopy(eeprom, sourceOffset, eeprom, targetOffset, DeviceOffsets.POKEMON_SUMMARY_SIZE)
        return true
    }

    fun readRouteItemId(eeprom: ByteArray, itemIndex: Int): Int {
        if (itemIndex !in 0 until DeviceOffsets.ROUTE_ITEM_COUNT) return 0
        val offset = DeviceOffsets.ROUTE_ITEM_LIST_OFFSET + itemIndex * 2
        return readU16LE(eeprom, offset)
    }

    fun readRouteItemMinSteps(eeprom: ByteArray, itemIndex: Int): Int {
        if (itemIndex !in 0 until DeviceOffsets.ROUTE_ITEM_COUNT) return 0
        val offset = DeviceOffsets.ROUTE_INFO_OFFSET + 0xA0 + itemIndex * 2
        return readU16LE(eeprom, offset)
    }

    fun readRouteItemChance(eeprom: ByteArray, itemIndex: Int): Int {
        if (itemIndex !in 0 until DeviceOffsets.ROUTE_ITEM_COUNT) return 0
        val offset = DeviceOffsets.ROUTE_INFO_OFFSET + 0xB4 + itemIndex
        return readUnsignedByte(eeprom, offset)
    }

    fun readDowsedItemId(eeprom: ByteArray, slot: Int): Int {
        if (slot !in 0 until DeviceOffsets.DOWSED_ITEM_COUNT) return 0
        val offset = DeviceOffsets.DOWSED_ITEMS_OFFSET + slot * DeviceOffsets.ITEM_DATA_SIZE
        return readU16LE(eeprom, offset)
    }

    fun readGiftedItemId(eeprom: ByteArray, slot: Int): Int {
        if (slot !in 0 until DeviceOffsets.GIFTED_ITEM_COUNT) return 0
        val offset = DeviceOffsets.GIFTED_ITEMS_OFFSET + slot * DeviceOffsets.ITEM_DATA_SIZE
        return readU16LE(eeprom, offset)
    }

    fun findRouteItemIndexForItemId(eeprom: ByteArray, itemId: Int): Int? {
        if (itemId == 0) return null
        for (index in 0 until DeviceOffsets.ROUTE_ITEM_COUNT) {
            if (readRouteItemId(eeprom, index) == itemId) {
                return index
            }
        }
        return null
    }

    fun readStepHistoryForDay(eeprom: ByteArray, dayIndex: Int): Int {
        if (dayIndex !in 0 until DeviceOffsets.STEP_HISTORY_DAYS) return 0
        val offset = DeviceOffsets.STEP_HISTORY_OFFSET + dayIndex * 4
        return readU32BE(eeprom, offset).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    fun stepHistoryCount(eeprom: ByteArray): Int {
        var count = 0
        for (day in 0 until DeviceOffsets.STEP_HISTORY_DAYS) {
            if (readStepHistoryForDay(eeprom, day) > 0) {
                count += 1
            }
        }
        return count
    }

    fun recordDowsedItemFromRoute(eeprom: ByteArray, itemIndex: Int): Boolean {
        val itemId = readRouteItemId(eeprom, itemIndex)
        if (itemId == 0) {
            return false
        }

        val targetSlot = firstEmptyDowsedItemSlot(eeprom) ?: return false
        val offset = DeviceOffsets.DOWSED_ITEMS_OFFSET + targetSlot * DeviceOffsets.ITEM_DATA_SIZE
        writeU16LE(eeprom, offset, itemId)
        writeU16LE(eeprom, offset + 2, 0)
        return true
    }

    fun hasFreeDowsedItemSlot(eeprom: ByteArray): Boolean {
        return firstEmptyDowsedItemSlot(eeprom) != null
    }

    fun replaceDowsedItemWithRoute(
        eeprom: ByteArray,
        dowsedSlot: Int,
        routeItemIndex: Int,
    ): Boolean {
        if (dowsedSlot !in 0 until DeviceOffsets.DOWSED_ITEM_COUNT) {
            return false
        }

        val itemId = readRouteItemId(eeprom, routeItemIndex)
        if (itemId == 0) {
            return false
        }

        val offset = DeviceOffsets.DOWSED_ITEMS_OFFSET + dowsedSlot * DeviceOffsets.ITEM_DATA_SIZE
        writeU16LE(eeprom, offset, itemId)
        writeU16LE(eeprom, offset + 2, 0)
        return true
    }

    private fun firstEmptyCaughtSlot(eeprom: ByteArray): Int? {
        for (slot in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
            if (readCaughtPokemonSpecies(eeprom, slot) == 0) {
                return slot
            }
        }
        return null
    }

    private fun firstEmptyDowsedItemSlot(eeprom: ByteArray): Int? {
        for (slot in 0 until DeviceOffsets.DOWSED_ITEM_COUNT) {
            if (readDowsedItemId(eeprom, slot) == 0) {
                return slot
            }
        }
        return null
    }

    private fun decodeDeviceCodeUnit(codeUnit: Int): Char? {
        if (codeUnit == 0x0000 || codeUnit == 0xFFFF) {
            return null
        }

        return when {
            codeUnit in 0x0121..0x012A -> ('0'.code + (codeUnit - 0x0121)).toChar()
            codeUnit in 0x012B..0x0144 -> ('A'.code + (codeUnit - 0x012B)).toChar()
            codeUnit in 0x0145..0x015E -> ('a'.code + (codeUnit - 0x0145)).toChar()
            codeUnit == 0x00E1 -> '!'
            codeUnit == 0x00E2 -> '?'
            codeUnit == 0x00E6 -> '*'
            codeUnit == 0x00E7 -> '/'
            codeUnit == 0x00F0 -> '+'
            codeUnit == 0x00F1 -> '-'
            codeUnit == 0x00F4 -> '='
            codeUnit == 0x00F8 -> '.'
            codeUnit == 0x00F9 -> ','
            codeUnit in 0x20..0x7E -> codeUnit.toChar()
            else -> '?'
        }
    }

    private fun readUnsignedByte(
        data: ByteArray,
        offset: Int,
    ): Int {
        if (offset !in data.indices) {
            return 0
        }
        return data[offset].toInt() and 0xFF
    }
}
