package com.example.wearwalker.data

import android.content.Context
import com.example.wearwalker.core.DeviceOffsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

enum class EepromLoadSource {
    Existing,
    CreatedBlank,
    RecreatedFromInvalid,
}

data class EepromLoadResult(
    val eeprom: ByteArray,
    val source: EepromLoadSource,
    val detail: String,
    val userProvided: Boolean,
)

data class EepromFileInfo(
    val absolutePath: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val userProvided: Boolean,
)

class EepromStorage(
    private val context: Context,
) {
    companion object {
        const val EEPROM_FILE_NAME = "eeprom.bin"
        private const val EEPROM_IMPORTED_MARKER_FILE = "eeprom_user_provided.flag"
    }

    suspend fun loadOrCreate(): EepromLoadResult = withContext(Dispatchers.IO) {
        val existing = runCatching {
            context.openFileInput(EEPROM_FILE_NAME).use { it.readBytes() }
        }

        if (existing.isSuccess) {
            val bytes = existing.getOrThrow()
            if (bytes.size == DeviceOffsets.EEPROM_SIZE) {
                return@withContext EepromLoadResult(
                    eeprom = bytes,
                    source = EepromLoadSource.Existing,
                    detail = "Loaded existing EEPROM from app storage.",
                    userProvided = importedMarkerExists(),
                )
            }

            val extracted = extractEepromWindow(bytes)
            if (extracted != null) {
                writeInternal(extracted)
                return@withContext EepromLoadResult(
                    eeprom = extracted,
                    source = EepromLoadSource.Existing,
                    detail =
                        "Loaded EEPROM by extracting ${DeviceOffsets.EEPROM_SIZE} bytes " +
                            "from a ${bytes.size}-byte file.",
                    userProvided = importedMarkerExists(),
                )
            }

            val recreated = createBlankEeprom()
            writeInternal(recreated)
            clearImportedMarker()
            return@withContext EepromLoadResult(
                eeprom = recreated,
                source = EepromLoadSource.RecreatedFromInvalid,
                detail =
                    "Stored EEPROM had invalid size (${bytes.size}). " +
                        "Recreated blank EEPROM (${DeviceOffsets.EEPROM_SIZE} bytes).",
                userProvided = false,
            )
        }

        val blank = createBlankEeprom()
        writeInternal(blank)
        clearImportedMarker()
        val reason =
            when (existing.exceptionOrNull()) {
                is FileNotFoundException -> "No stored EEPROM found. Created blank EEPROM."
                else ->
                    "EEPROM could not be loaded (${existing.exceptionOrNull()?.message}). " +
                        "Created blank EEPROM."
            }
        EepromLoadResult(
            eeprom = blank,
            source = EepromLoadSource.CreatedBlank,
            detail = reason,
            userProvided = false,
        )
    }

    suspend fun save(eeprom: ByteArray) = withContext(Dispatchers.IO) {
        require(eeprom.size == DeviceOffsets.EEPROM_SIZE) {
            "Cannot save EEPROM with size ${eeprom.size}."
        }
        writeInternal(eeprom)
    }

    suspend fun replaceWithImported(eeprom: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        require(eeprom.size == DeviceOffsets.EEPROM_SIZE) {
            "Cannot import EEPROM with size ${eeprom.size}."
        }
        writeInternal(eeprom)
        writeImportedMarker()
        eeprom.copyOf()
    }

    fun getFileInfo(): EepromFileInfo {
        val file = context.getFileStreamPath(EEPROM_FILE_NAME)
        val exists = file.exists()
        val manualFilePath = "/data/data/${context.packageName}/files/$EEPROM_FILE_NAME"
        return EepromFileInfo(
            absolutePath = manualFilePath,
            exists = exists,
            sizeBytes = if (exists) file.length() else 0L,
            userProvided = importedMarkerExists(),
        )
    }

    private fun extractEepromWindow(bytes: ByteArray): ByteArray? {
        if (bytes.size < DeviceOffsets.EEPROM_SIZE) {
            return null
        }

        var offset = 0
        val maxOffset = bytes.size - DeviceOffsets.EEPROM_SIZE
        while (offset <= maxOffset) {
            if (hasNintendoSignatureAt(bytes, offset)) {
                return bytes.copyOfRange(offset, offset + DeviceOffsets.EEPROM_SIZE)
            }
            offset += 1
        }

        return null
    }

    private fun hasNintendoSignatureAt(
        data: ByteArray,
        offset: Int,
    ): Boolean {
        if (offset < 0 || offset + DeviceOffsets.SIGNATURE.size > data.size) {
            return false
        }

        return DeviceOffsets.SIGNATURE.indices.all { index ->
            data[offset + index] == DeviceOffsets.SIGNATURE[index]
        }
    }

    private fun importedMarkerExists(): Boolean {
        return context.getFileStreamPath(EEPROM_IMPORTED_MARKER_FILE).exists()
    }

    private fun writeImportedMarker() {
        context.openFileOutput(EEPROM_IMPORTED_MARKER_FILE, Context.MODE_PRIVATE).use { stream ->
            stream.write(1)
        }
    }

    private fun clearImportedMarker() {
        context.deleteFile(EEPROM_IMPORTED_MARKER_FILE)
    }

    private fun writeInternal(eeprom: ByteArray) {
        context.openFileOutput(EEPROM_FILE_NAME, Context.MODE_PRIVATE).use { stream ->
            stream.write(eeprom)
        }
    }

    private fun createBlankEeprom(): ByteArray {
        val eeprom = ByteArray(DeviceOffsets.EEPROM_SIZE)
        System.arraycopy(
            DeviceOffsets.SIGNATURE,
            0,
            eeprom,
            DeviceOffsets.SIGNATURE_OFFSET,
            DeviceOffsets.SIGNATURE.size,
        )
        System.arraycopy(
            eeprom,
            DeviceOffsets.GENERAL_DATA_PRIMARY_OFFSET,
            eeprom,
            DeviceOffsets.GENERAL_DATA_MIRROR_OFFSET,
            DeviceOffsets.GENERAL_DATA_LENGTH,
        )
        return eeprom
    }
}
