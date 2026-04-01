package com.wearwalker.wearwalker.presentation

import android.media.AudioManager
import android.media.ToneGenerator

enum class DeviceButtonSoundType {
    Left,
    Right,
    Enter,
    SettingAdjust,
}

class DeviceButtonSoundPlayer {
    private var toneGenerator: ToneGenerator? = null
    private var currentSoundLevel: Int = -1

    @Synchronized
    fun play(
        soundLevel: Int,
        soundType: DeviceButtonSoundType,
    ) {
        val normalizedLevel = soundLevel.coerceIn(0, 2)
        if (normalizedLevel <= 0) {
            return
        }

        val generator = ensureGenerator(normalizedLevel) ?: return
        val (tone, durationMs) =
            when (soundType) {
                DeviceButtonSoundType.Left -> ToneGenerator.TONE_DTMF_1 to 35
                DeviceButtonSoundType.Right -> ToneGenerator.TONE_DTMF_3 to 35
                DeviceButtonSoundType.Enter -> ToneGenerator.TONE_DTMF_5 to 55
                DeviceButtonSoundType.SettingAdjust -> ToneGenerator.TONE_DTMF_8 to 40
            }
        generator.startTone(tone, durationMs)
    }

    @Synchronized
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
        currentSoundLevel = -1
    }

    @Synchronized
    private fun ensureGenerator(soundLevel: Int): ToneGenerator? {
        if (toneGenerator != null && currentSoundLevel == soundLevel) {
            return toneGenerator
        }

        toneGenerator?.release()
        toneGenerator = null

        val volume =
            when (soundLevel) {
                1 -> 35
                2 -> 85
                else -> 0
            }

        return runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, volume)
        }.getOrNull()?.also {
            toneGenerator = it
            currentSoundLevel = soundLevel
        }
    }
}
