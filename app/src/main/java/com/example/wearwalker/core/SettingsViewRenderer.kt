package com.example.wearwalker.core

internal object SettingsViewRenderer {
    internal fun render(
        spriteData: ByteArray,
        state: DeviceInteractionState,
    ): LcdPreviewFrame {
        return with(DeviceLcdRenderer) {
            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val menuSettings = decodeSprite(spriteData, MENU_SETTINGS, 80, 16)
            val arrowLeft = decodeSprite(spriteData, ARROW_8_LEFT, 8, 8)
            val arrowRight = decodeSprite(spriteData, ARROW_8_RIGHT, 8, 8)
            val arrowUp = decodeSprite(spriteData, ARROW_8_UP, 8, 8)
            val arrowDown = decodeSprite(spriteData, ARROW_8_DOWN, 8, 8)
            val settingsSound = decodeSprite(spriteData, SETTINGS_SOUND, 40, 16)
            val settingsShade = decodeSprite(spriteData, SETTINGS_SHADE, 40, 16)

            val soundLevelSprite =
                when (state.soundLevel.coerceIn(0, 2)) {
                    0 -> decodeSprite(spriteData, SETTINGS_SOUND_OFF, 24, 16)
                    1 -> decodeSprite(spriteData, SETTINGS_SOUND_LOW, 24, 16)
                    else -> decodeSprite(spriteData, SETTINGS_SOUND_HIGH, 24, 16)
                }

            blit(frame, arrowLeft, 8, 8, 0, 4)
            blit(frame, arrowRight, 8, 8, 88, 4)
            blit(frame, menuSettings, 80, 16, 8, 0)

            val soundX = 8
            val shadeX = 48
            val topY = 16

            blit(frame, settingsSound, 40, 16, soundX, topY)
            blit(frame, settingsShade, 40, 16, shadeX, topY)

            if (state.settingsMode == DeviceSettingsMode.SelectField) {
                val markerX = if (state.settingsField == DeviceSettingsField.Sound) soundX else shadeX
                blit(frame, arrowUp, 8, 8, markerX + 16, topY + 16)
            }

            if (state.settingsMode == DeviceSettingsMode.AdjustSound) {
                val optionSpacing = 24
                val optionsY = 32
                val firstOptionX = 12
                val soundOptionSprites =
                    arrayOf(
                        decodeSprite(spriteData, SETTINGS_SOUND_OFF, 24, 16),
                        decodeSprite(spriteData, SETTINGS_SOUND_LOW, 24, 16),
                        decodeSprite(spriteData, SETTINGS_SOUND_HIGH, 24, 16),
                    )

                soundOptionSprites.forEachIndexed { index, sprite ->
                    val x = firstOptionX + index * optionSpacing
                    blit(frame, sprite, 24, 16, x, optionsY)
                    if (index == state.soundLevel.coerceIn(0, 2)) {
                        blit(frame, arrowDown, 8, 8, x + 8, optionsY + 16)
                    }
                }
            }

            if (state.settingsMode == DeviceSettingsMode.AdjustShade) {
                val sliderY = 40
                for (segment in 0 until 4) {
                    val x = 12 + segment * 18
                    drawHorizontalLineSegment(frame, x, x + 12, sliderY, palette[3])
                }
                val selectedShade = state.shadeLevel.coerceIn(0, 3)
                val markerX = 12 + selectedShade * 18 + 2
                blit(frame, arrowUp, 8, 8, markerX, sliderY - 10)
            }

            blit(frame, soundLevelSprite, 24, 16, 72, 32)
            drawNumberRight(frame, spriteData, state.shadeLevel.coerceIn(0, 3), 95, 48)

            drawMessageBoxBorder(frame)

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(menuSettings) || hasNonBackground(settingsSound),
            )
        }
    }
}
