package com.wearwalker.wearwalker.core

internal object HomeViewRenderer {
    internal fun render(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        steps: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return with(DeviceLcdRenderer) {
            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val hasWalkingCompanion = DeviceBinary.readWalkingCompanionSpecies(eeprom) != 0
            val bigCompanion =
                if (hasWalkingCompanion) {
                    decodeAnimatedSprite(spriteData, BIG_COMPANION, 64, 48, animationFrame)
                } else {
                    IntArray(64 * 48) { palette[0] }
                }
            val routeImage = decodeSprite(spriteData, ROUTE_IMAGE, 32, 24)
            val bubble =
                if (!hasWalkingCompanion || state.homeEventType == null) {
                    null
                } else if (state.homeEventMusicBubble) {
                    decodeSprite(spriteData, BUBBLE_MUSIC, 24, 16)
                } else {
                    decodeSprite(spriteData, BUBBLE_EXCLAMATION, 24, 16)
                }
            val captureBall = decodeSprite(spriteData, COMPANIONBALL_8, 8, 8)
            val item = decodeSprite(spriteData, ITEM_8, 8, 8)

            val yBob = if (animationFrame % 2 == 0) 0 else 1
            blit(frame, bigCompanion, 64, 48, 32, yBob)
            blit(frame, routeImage, 32, 24, 0, 24)
            if (bubble != null && animationFrame % 4 != 3) {
                blit(frame, bubble, 24, 16, 8, 4)
            }

            repeat(state.caughtCompanionCount.coerceIn(0, 3)) { index ->
                blit(frame, captureBall, 8, 8, index * 8, HEIGHT - 8)
            }
            repeat(state.foundItemCount.coerceIn(0, 3)) { index ->
                blit(frame, item, 8, 8, 24 + index * 8, HEIGHT - 8)
            }

            drawNumberRight(frame, spriteData, steps.coerceAtLeast(0), 95, HEIGHT - 16)
            drawHorizontalLine(frame, y = 48, color = palette[3])

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(bigCompanion) || hasNonBackground(routeImage),
            )
        }
    }
}
