package com.example.wearwalker.core

import java.time.LocalTime

internal object CardViewRenderer {
    internal fun render(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return with(DeviceLcdRenderer) {
            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val arrowLeft = decodeSprite(spriteData, ARROW_8_LEFT, 8, 8)
            val arrowRight = decodeSprite(spriteData, ARROW_8_RIGHT, 8, 8)
            val title = decodeSprite(spriteData, MENU_CARD, 80, 16)
            val trainerIcon = decodeSprite(spriteData, CARD_TRAINER_ICON, 16, 16)
            val trainerNameImage = decodeSprite(spriteData, CARD_TRAINER_NAME, 80, 16)
            val routeIcon = decodeSprite(spriteData, CARD_ROUTE_ICON, 16, 16)
            val routeNameImage = decodeSprite(spriteData, ROUTE_NAME, 80, 16)
            val stepsText = decodeSprite(spriteData, CARD_STEPS_TEXT, 40, 16)
            val totalDaysText = decodeSprite(spriteData, CARD_TOTAL_DAYS_TEXT, 64, 16)

            val steps = DeviceBinary.readHealthTodaySteps(eeprom).coerceAtMost(99_999)
            val historyCount = DeviceBinary.stepHistoryCount(eeprom).coerceAtLeast(0)
            val page = state.cardPageIndex.coerceAtLeast(0)

            blit(frame, arrowLeft, 8, 8, 0, 4)
            blit(frame, arrowRight, 8, 8, 88, 4)
            blit(frame, title, 80, 16, 8, 0)
            blit(frame, trainerIcon, 16, 16, 0, 16)
            blit(frame, trainerNameImage, 80, 16, 16, 16)
            blit(frame, routeIcon, 16, 16, 0, 32)
            blit(frame, routeNameImage, 80, 16, 16, 32)

            if (page == 0) {
                blit(frame, stepsText, 40, 16, 0, 48)
                drawNumberRight(frame, spriteData, steps, 70, 48)

                val now = LocalTime.now()
                drawFixed4Digits(
                    frame = frame,
                    spriteData = spriteData,
                    value = now.hour * 100 + now.minute,
                    rightX = 95,
                    y = 48,
                )
            } else {
                val historyDayIndex = (page - 1).coerceIn(0, (historyCount - 1).coerceAtLeast(0))
                val historySteps = DeviceBinary.readStepHistoryForDay(eeprom, historyDayIndex).coerceAtMost(99_999)
                blit(frame, totalDaysText, 64, 16, 0, 48)
                drawNumberRight(frame, spriteData, historySteps, 95, 48)
                drawNumberRight(frame, spriteData, historyDayIndex + 1, 95, 32)
            }

            drawMessageBoxBorder(frame)

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(title) || hasNonBackground(trainerNameImage),
            )
        }
    }
}
