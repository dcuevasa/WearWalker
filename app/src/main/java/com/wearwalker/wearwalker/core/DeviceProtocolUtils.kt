package com.wearwalker.wearwalker.core

object DeviceProtocolUtils {
    const val CRC_START = 0x0002

    fun crcAlgorithm(data: ByteArray, seed: Int = CRC_START): Int {
        var crc = seed
        data.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            crc += if ((index and 1) != 0) value else value shl 8
        }

        while ((crc ushr 16) != 0) {
            crc = (crc and 0xFFFF) + (crc ushr 16)
        }

        return crc and 0xFFFF
    }

    fun lz77Decompress(input: ByteArray): ByteArray {
        require(input.size >= 4) { "LZ77 payload is too short." }

        val type = input[0].toInt() and 0xFF
        require(type == 0x10) { "Unsupported LZ77 type: 0x${type.toString(16)}" }

        val targetSize =
            (input[1].toInt() and 0xFF) or
                ((input[2].toInt() and 0xFF) shl 8) or
                ((input[3].toInt() and 0xFF) shl 16)

        val output = ArrayList<Byte>(targetSize)
        var cursor = 4

        while (cursor < input.size && output.size < targetSize) {
            val header = input[cursor].toInt() and 0xFF
            cursor += 1

            for (packet in 0 until 8) {
                if (output.size >= targetSize) {
                    break
                }

                val isBackReference = ((header shl packet) and 0x80) != 0
                if (!isBackReference) {
                    require(cursor < input.size) { "Truncated LZ77 literal block." }
                    output.add(input[cursor])
                    cursor += 1
                    continue
                }

                require(cursor + 1 < input.size) { "Truncated LZ77 back-reference block." }
                val first = input[cursor].toInt() and 0xFF
                val second = input[cursor + 1].toInt() and 0xFF
                cursor += 2

                val backReference = (((first and 0x0F) shl 8) or second) + 1
                val copyLength = ((first and 0xF0) ushr 4) + 3
                val offset = output.size - backReference
                require(offset >= 0) { "Invalid LZ77 back-reference offset." }

                for (index in 0 until copyLength) {
                    if (output.size >= targetSize) {
                        break
                    }
                    output.add(output[offset + index])
                }
            }
        }

        require(output.size == targetSize) {
            "Decompressed length mismatch. expected=$targetSize actual=${output.size}"
        }

        return output.toByteArray()
    }
}
