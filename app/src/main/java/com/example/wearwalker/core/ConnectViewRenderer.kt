package com.example.wearwalker.core

internal object ConnectViewRenderer {
    internal fun render(spriteData: ByteArray): LcdPreviewFrame {
        return with(DeviceLcdRenderer) {
            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val arrowReturn = decodeSprite(spriteData, ARROW_RETURN, 8, 16)
            val title = decodeSprite(spriteData, MENU_CONNECT, 80, 16)
            val icon = decodeSprite(spriteData, ICON_CONNECT, 16, 16)

            blit(frame, arrowReturn, 8, 16, 0, 0)
            blit(frame, title, 80, 16, 8, 0)
            blit(frame, icon, 16, 16, 40, 24)

            drawMessageBoxBorder(frame)

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(title) || hasNonBackground(icon),
            )
        }
    }
}
