package com.wearwalker.wearwalker.core

internal object DowsingViewRenderer {
    fun renderMain(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame =
        with(DeviceLcdRenderer) {
            if (state.dowsingMode == DowsingMode.Swap) {
                return@with renderSwap(eeprom, spriteData, state, animationFrame)
            }

            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val routeImage = decodeSprite(spriteData, ROUTE_IMAGE, 32, 24)
            val dowsingLeft = decodeSprite(spriteData, DOWSING_LEFT, 32, 16)
            val bush = decodeSprite(spriteData, DOWSING_BUSH, 16, 16)
            val bushDim = dimSprite(bush)
            val bushLight = decodeSprite(spriteData, DOWSING_BUSH + 0x40, 16, 16)
            val arrowUp = decodeSprite(spriteData, ARROW_8_UP, 8, 8)
            val item = decodeSprite(spriteData, ITEM_8, 8, 8)
            val discoverText = decodeSprite(spriteData, TEXT_DISCOVER_ITEM, 96, 16)
            val foundText = decodeSprite(spriteData, TEXT_FOUND, 96, 16)
            val switchText = decodeSprite(spriteData, TEXT_SWITCH, 80, 16)
            val noMatchText = decodeSprite(spriteData, TEXT_NOTHING_FOUND, 96, 16)
            val nearText = decodeSprite(spriteData, TEXT_ITS_NEAR, 96, 16)
            val farText = decodeSprite(spriteData, TEXT_ITS_FAR, 96, 16)

            blit(frame, dowsingLeft, 32, 16, 0, 0)
            drawNumberRight(frame, spriteData, state.dowsingAttemptsRemaining.coerceIn(0, 9), 44, 0)
            blit(frame, routeImage, 32, 24, 64, 0)

            val showEndReveal = state.dowsingMode == DowsingMode.EndMessage
            val showFoundReveal = state.dowsingMode == DowsingMode.FoundMessage

            for (index in 0 until 6) {
                val x = index * 16
                val checked = ((state.dowsingCheckedMask ushr index) and 0x1) == 1
                val targetShown = (showFoundReveal || showEndReveal) && state.dowsingTargetCursor == index
                val bushSprite =
                    if (targetShown) {
                        bushLight
                    } else if (checked) {
                        bushDim
                    } else {
                        bush
                    }

                val revealJump =
                    if (state.dowsingMode == DowsingMode.Reveal && state.dowsingRevealCursor == index) {
                        if (animationFrame % 2 == 0) -1 else 0
                    } else {
                        0
                    }

                blit(frame, bushSprite, 16, 16, x, 24 + revealJump)

                if (targetShown) {
                    blit(frame, item, 8, 8, x + 4, 24)
                }

                if (index == state.dowsingCursor && state.dowsingMode == DowsingMode.Search && state.dowsingAttemptsRemaining > 0) {
                    blit(frame, arrowUp, 8, 8, x + 3, 40 - selectorBounceOffset(animationFrame))
                }
            }

            if (state.dowsingMode == DowsingMode.FoundMessage) {
                fillRect(frame, 0, 24, WIDTH - 1, HEIGHT - 1, palette[0])
                drawLargeMessageBoxBorder(frame)

                val itemIndex = state.dowsingResultItemIndex
                val itemNameOffset = itemIndex?.let { ROUTE_ITEM0_NAME + it * ROUTE_ITEM_NAME_STRIDE }
                val itemName = itemNameOffset?.let { decodeSprite(spriteData, it, 96, 16) } ?: discoverText
                drawLargeMessageLine(frame, itemName, y = 32, centerContent = true)
                if (state.dowsingPendingStored) {
                    drawLargeMessageLine(frame, foundText, y = 48, centerContent = true)
                } else {
                    val switchX = centeredSpriteDstX(switchText, 80, 16, innerLeft = 1, innerRight = WIDTH - 2)
                    blitClipped(
                        dst = frame,
                        src = switchText,
                        srcWidth = 80,
                        srcHeight = 16,
                        dstX = switchX,
                        dstY = 48,
                        clipMinX = 1,
                        clipMinY = 25,
                        clipMaxX = WIDTH - 2,
                        clipMaxY = 62,
                    )
                }

                return@with LcdPreviewFrame(
                    pixels = frame,
                    hasVisualContent = hasNonBackground(bush) || hasNonBackground(routeImage),
                )
            }

            val messageSprite =
                when (state.dowsingMode) {
                    DowsingMode.MissMessage,
                    DowsingMode.EndMessage,
                        -> noMatchText

                    DowsingMode.HintMessage -> {
                        if (state.dowsingPendingNear) {
                            nearText
                        } else {
                            farText
                        }
                    }

                    else -> discoverText
                }

            drawBottomMessageBox(frame, messageSprite)

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(bush) || hasNonBackground(routeImage),
            )
        }

    fun renderSwap(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame =
        with(DeviceLcdRenderer) {
            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val switchText = decodeSprite(spriteData, TEXT_SWITCH, 80, 16)
            val arrowReturn = decodeSprite(spriteData, ARROW_RETURN, 8, 16)
            val arrowUp = decodeSprite(spriteData, ARROW_8_UP, 8, 8)
            val itemIcon = decodeSprite(spriteData, ITEM_8, 8, 8)

            fillRect(frame, 0, 0, WIDTH - 1, 16, palette[0])
            blit(frame, switchText, 80, 16, 8, 0)
            blit(frame, arrowReturn, 8, 16, 0, 0)
            drawHorizontalLine(frame, y = 16, color = palette[3])

            val selected = state.dowsingSwapCursor.coerceIn(0, DeviceOffsets.DOWSED_ITEM_COUNT - 1)
            val slotXs = intArrayOf(24, 44, 64)
            for (slot in 0 until DeviceOffsets.DOWSED_ITEM_COUNT) {
                val x = slotXs[slot]
                blit(frame, itemIcon, 8, 8, x, 28)
                if (slot == selected) {
                    blit(frame, arrowUp, 8, 8, x, 36 + selectorBounceOffset(animationFrame))
                }
            }

            val selectedItemId = DeviceBinary.readDowsedItemId(eeprom, selected)
            val selectedRouteIndex = DeviceBinary.findRouteItemIndexForItemId(eeprom, selectedItemId)
            val selectedName =
                if (selectedRouteIndex != null) {
                    decodeSprite(
                        spriteData,
                        ROUTE_ITEM0_NAME + selectedRouteIndex * ROUTE_ITEM_NAME_STRIDE,
                        96,
                        16,
                    )
                } else {
                    decodeSprite(spriteData, TEXT_NOTHING_HELD, 96, 16)
                }

            drawBottomMessageBox(
                frame = frame,
                messageSprite = selectedName,
                messageWidth = 96,
                messageX = 0,
                clipToInner = true,
            )

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(switchText) || hasNonBackground(itemIcon),
            )
        }
}
