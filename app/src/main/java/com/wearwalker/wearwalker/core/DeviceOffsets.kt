package com.wearwalker.wearwalker.core

object DeviceOffsets {
    const val EEPROM_SIZE = 0x10000

    const val SIGNATURE_OFFSET = 0x0000
    val SIGNATURE = byteArrayOf(
        'n'.code.toByte(),
        'i'.code.toByte(),
        'n'.code.toByte(),
        't'.code.toByte(),
        'e'.code.toByte(),
        'n'.code.toByte(),
        'd'.code.toByte(),
        'o'.code.toByte(),
    )

    const val GENERAL_DATA_PRIMARY_OFFSET = 0x0080
    const val GENERAL_DATA_MIRROR_OFFSET = 0x0180
    const val GENERAL_DATA_LENGTH = 0x0100

    const val IDENTITY_OFFSET = 0x00ED
    const val IDENTITY_LENGTH = 0x68
    const val IDENTITY_OWNER_NAME_OFFSET = IDENTITY_OFFSET + 72
    const val IDENTITY_FLAGS_OFFSET = IDENTITY_OFFSET + 91
    const val IDENTITY_PROTOCOL_VERSION_OFFSET = IDENTITY_OFFSET + 92
    const val IDENTITY_PROTOCOL_SUB_VERSION_OFFSET = IDENTITY_OFFSET + 94
    const val IDENTITY_LAST_SYNC_OFFSET = IDENTITY_OFFSET + 96
    const val IDENTITY_STEP_COUNT_OFFSET = IDENTITY_OFFSET + 100

    const val HEALTH_OFFSET = 0x0156
    const val HEALTH_LIFETIME_STEPS_OFFSET = HEALTH_OFFSET
    const val HEALTH_TODAY_STEPS_OFFSET = HEALTH_OFFSET + 4
    const val HEALTH_LAST_SYNC_OFFSET = HEALTH_OFFSET + 8
    const val HEALTH_TOTAL_DAYS_OFFSET = HEALTH_OFFSET + 12
    const val HEALTH_CUR_WATTS_OFFSET = HEALTH_OFFSET + 14

    // Runtime mirror used by protocol commands.
    const val SESSION_WATTS_OFFSET = 0xCE8A
    // General-data block field documented in reverse engineering notes.
    const val GENERAL_WATTS_OFFSET = 0x0164
    // Alternate location seen in EEPROM documentation.
    const val GENERAL_LAST_SYNC_OFFSET = 0x015E

    const val ROUTE_INFO_OFFSET = 0x8F00
    const val ROUTE_FRIENDSHIP_OFFSET = ROUTE_INFO_OFFSET + 38
    const val ROUTE_COMPANION_LIST_OFFSET = ROUTE_INFO_OFFSET + 82
    const val ROUTE_ITEM_LIST_OFFSET = ROUTE_INFO_OFFSET + 140

    const val AREA_SPRITE_OFFSET = 0x8FBE
    const val AREA_NAME_SPRITE_OFFSET = 0x907E
    const val WALK_COMPANION_SMALL_ANIM_OFFSET = 0x91BE
    const val WALK_COMPANION_BIG_ANIM_OFFSET = 0x933E
    const val WALK_COMPANION_NAME_SPRITE_OFFSET = 0x993E
    const val ROUTE_COMPANION_SPRITES_OFFSET = 0x9A7E
    const val ROUTE_COMPANION_NAME_SPRITES_OFFSET = 0xA4FE
    const val ROUTE_ITEM_NAME_SPRITES_OFFSET = 0xA8BE

    const val TEAM_OFFSET = 0xCC00
    const val WATTS_FOR_REMOTE_OFFSET = 0xCE8A
    const val CAUGHT_COMPANION_OFFSET = 0xCE8C
    const val DOWSED_ITEMS_OFFSET = 0xCEBC
    const val GIFTED_ITEMS_OFFSET = 0xCEC8
    const val STEP_HISTORY_OFFSET = 0xCEF0

    const val COMPANION_SUMMARY_SIZE = 16
    const val ITEM_DATA_SIZE = 4
    const val COMPANION_SLOT_COUNT = 3
    const val DOWSED_ITEM_COUNT = 3
    const val GIFTED_ITEM_COUNT = 10
    const val ROUTE_ITEM_COUNT = 10
    const val STEP_HISTORY_DAYS = 7

    const val STATIC_SPRITES_OFFSET = 0x0280
    const val DYNAMIC_REGION_OFFSET = 0x8F00
}
