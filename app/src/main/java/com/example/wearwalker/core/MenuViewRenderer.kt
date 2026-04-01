package com.example.wearwalker.core

internal object MenuViewRenderer {
    internal fun render(
        spriteData: ByteArray,
        state: DeviceInteractionState,
        watts: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return with(DeviceLcdRenderer) {
            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val selected = state.selectedMenuItem()
            val selectedTitle = decodeSprite(spriteData, menuTitleOffset(selected), 80, 16)
            val arrowLeft = decodeSprite(spriteData, ARROW_8_LEFT, 8, 8)
            val arrowRight = decodeSprite(spriteData, ARROW_8_RIGHT, 8, 8)
            val arrowDown = decodeSprite(spriteData, ARROW_8_DOWN, 8, 8)

            val icons =
                listOf(
                    decodeSprite(spriteData, ICON_RADAR, 16, 16),
                    decodeSprite(spriteData, ICON_DOWSING, 16, 16),
                    decodeSprite(spriteData, ICON_CONNECT, 16, 16),
                    decodeSprite(spriteData, ICON_CARD, 16, 16),
                    decodeSprite(spriteData, ICON_PKMN, 16, 16),
                    decodeSprite(spriteData, ICON_SETTINGS, 16, 16),
                )
            val iconX = intArrayOf(0, 16, 32, 48, 64, 80)
            val iconY = intArrayOf(24, 28, 32, 32, 28, 24)

            blit(frame, arrowLeft, 8, 8, 0, 4)
            blit(frame, arrowRight, 8, 8, 88, 4)
            blit(frame, selectedTitle, 80, 16, 8, 0)

            for (index in icons.indices) {
                val x = iconX[index]
                val y = iconY[index]
                blit(frame, icons[index], 16, 16, x, y)
                if (index == state.menuIndex) {
                    blit(frame, arrowDown, 8, 8, x + 4, 16 + selectorBounceOffset(animationFrame))
                }
            }

            val cost =
                when (selected) {
                    DeviceMenuItem.Radar -> 10
                    DeviceMenuItem.Dowsing -> 3
                    else -> null
                }

            if (cost != null) {
                drawCostAndCurrentWatts(frame, spriteData, cost, watts.coerceAtLeast(0), HEIGHT - 16)
            } else {
                val wattSymbol = decodeSprite(spriteData, WATT_SYMBOL, 16, 16)
                blit(frame, wattSymbol, 16, 16, WIDTH - 16, HEIGHT - 16)
                drawNumberRight(frame, spriteData, watts.coerceAtLeast(0), WIDTH - 17, HEIGHT - 16)
            }

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(selectedTitle) || hasNonBackground(icons.first()),
            )
        }
    }
}
