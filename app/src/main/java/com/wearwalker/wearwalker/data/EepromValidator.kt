package com.wearwalker.wearwalker.data

import com.wearwalker.wearwalker.core.DeviceOffsets

data class EepromValidationReport(
    val isValidSize: Boolean,
    val hasNintendoSignature: Boolean,
    val mirrorInSync: Boolean,
    val mirrorMismatchCount: Int,
    val protocolVersion: Int,
    val protocolSubVersion: Int,
    val notes: List<String>,
) {
    val isValid: Boolean
        get() = isValidSize && hasNintendoSignature
}

object EepromValidator {
    fun validate(eeprom: ByteArray): EepromValidationReport {
        if (eeprom.size != DeviceOffsets.EEPROM_SIZE) {
            return EepromValidationReport(
                isValidSize = false,
                hasNintendoSignature = false,
                mirrorInSync = false,
                mirrorMismatchCount = 0,
                protocolVersion = 0,
                protocolSubVersion = 0,
                notes =
                    listOf(
                        "Invalid EEPROM size. expected=${DeviceOffsets.EEPROM_SIZE} actual=${eeprom.size}",
                    ),
            )
        }

        val signatureMatches = DeviceOffsets.SIGNATURE.indices.all { index ->
            eeprom[DeviceOffsets.SIGNATURE_OFFSET + index] == DeviceOffsets.SIGNATURE[index]
        }

        var mismatches = 0
        for (index in 0 until DeviceOffsets.GENERAL_DATA_LENGTH) {
            if (
                eeprom[DeviceOffsets.GENERAL_DATA_PRIMARY_OFFSET + index] !=
                    eeprom[DeviceOffsets.GENERAL_DATA_MIRROR_OFFSET + index]
            ) {
                mismatches += 1
            }
        }

        val notes = buildList {
            if (!signatureMatches) {
                add("Missing or invalid Nintendo signature at 0x0000.")
            }
            if (mismatches > 0) {
                add("General-data mirror mismatch detected ($mismatches bytes).")
            }
            if (signatureMatches && mismatches == 0) {
                add("EEPROM integrity checks passed.")
            }
        }

        return EepromValidationReport(
            isValidSize = true,
            hasNintendoSignature = signatureMatches,
            mirrorInSync = mismatches == 0,
            mirrorMismatchCount = mismatches,
            protocolVersion = eeprom[DeviceOffsets.IDENTITY_PROTOCOL_VERSION_OFFSET].toInt() and 0xFF,
            protocolSubVersion = eeprom[DeviceOffsets.IDENTITY_PROTOCOL_SUB_VERSION_OFFSET].toInt() and 0xFF,
            notes = notes,
        )
    }
}
