package com.wearwalker.wearwalker.core

internal object SettingsViewRenderer {
    internal fun render(
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return with(DeviceLcdRenderer) {
            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val arrowReturn = decodeSprite(spriteData, ARROW_RETURN, 8, 16)
            val menuSettings = decodeSprite(spriteData, MENU_SETTINGS, 80, 16)
            val arrowRight = decodeSprite(spriteData, ARROW_8_RIGHT, 8, 8)
            val arrowDown = decodeSprite(spriteData, ARROW_8_DOWN, 8, 8)
            val settingsSound = decodeSprite(spriteData, SETTINGS_SOUND, 40, 16)
            val settingsShade = decodeSprite(spriteData, SETTINGS_SHADE, 40, 16)
            val contrastBar = decodeSprite(spriteData, SETTINGS_CONTRAST_BAR, 8, 16)

            val soundOptionSprites =
                arrayOf(
                    decodeSprite(spriteData, SETTINGS_SOUND_OFF, 24, 16),
                    decodeSprite(spriteData, SETTINGS_SOUND_LOW, 24, 16),
                    decodeSprite(spriteData, SETTINGS_SOUND_HIGH, 24, 16),
                )

            blit(frame, arrowReturn, 8, 16, 0, 0)
            blit(frame, menuSettings, 80, 16, 8, 0)
            drawHorizontalLine(frame, y = 16, color = palette[3])

            val soundX = 12
            val shadeX = 52
            val optionY = 22
            val selectedFieldX = if (state.settingsField == DeviceSettingsField.Sound) soundX else shadeX
            val topSelectorX = selectedFieldX - 8
            val topSelectorY = optionY + 4
            val selectorBob = selectorBounceOffset(animationFrame)

            blit(frame, settingsSound, 40, 16, soundX, optionY)
            blit(frame, settingsShade, 40, 16, shadeX, optionY)

            if (state.settingsMode == DeviceSettingsMode.SelectField) {
                blit(frame, arrowRight, 8, 8, topSelectorX, topSelectorY + selectorBob)
            } else {
                drawRightOutlineSelector(frame, topSelectorX, topSelectorY)
            }

            if (state.settingsMode == DeviceSettingsMode.AdjustSound) {
                val optionSpacing = 26
                val optionsY = 44
                val firstOptionX = 12

                soundOptionSprites.forEachIndexed { index, sprite ->
                    val x = firstOptionX + index * optionSpacing
                    blit(frame, sprite, 24, 16, x, optionsY)
                    if (index == state.soundLevel.coerceIn(0, 2)) {
                        blit(frame, arrowRight, 8, 8, x - 8, optionsY + 4 + selectorBob)
                    }
                }
            }

            if (state.settingsMode == DeviceSettingsMode.AdjustShade) {
                val barStartX = 8
                val barY = 40
                val barHasPixels = hasNonBackground(contrastBar)

                for (segment in 0 until 10) {
                    val x = barStartX + segment * 8
                    if (barHasPixels) {
                        blit(frame, contrastBar, 8, 16, x, barY)
                    } else {
                        drawVerticalLine(frame, x, palette[3], barY, barY + 15)
                        drawVerticalLine(frame, x + 7, palette[3], barY, barY + 15)
                        drawHorizontalLineSegment(frame, x, x + 7, barY, palette[3])
                        drawHorizontalLineSegment(frame, x, x + 7, barY + 15, palette[3])
                    }
                }

                val selectedShade = state.shadeLevel.coerceIn(0, 9)
                val markerX = barStartX + selectedShade * 8
                blit(frame, arrowDown, 8, 8, markerX, barY - 8 + selectorBob)
            }

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(menuSettings) || hasNonBackground(settingsSound),
            )
        }
    }

    private fun drawRightOutlineSelector(
        frame: IntArray,
        x: Int,
        y: Int,
    ) {
        fun pixel(px: Int, py: Int) {
            if (px !in 0 until DeviceLcdRenderer.WIDTH || py !in 0 until DeviceLcdRenderer.HEIGHT) return
            frame[py * DeviceLcdRenderer.WIDTH + px] = DeviceLcdRenderer.palette[3]
        }

        for (row in 0 until 8) {
            val halfWidth = if (row <= 3) row else 7 - row
            pixel(x, y + row)
            pixel(x + halfWidth, y + row)
        }
    }
}
