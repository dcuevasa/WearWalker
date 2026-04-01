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

            val arrowReturn = decodeSprite(spriteData, ARROW_RETURN, 8, 16)
            val arrowLeft = decodeSprite(spriteData, ARROW_8_LEFT, 8, 8)
            val arrowRight = decodeSprite(spriteData, ARROW_8_RIGHT, 8, 8)
            val title = decodeSprite(spriteData, MENU_CARD, 80, 16)
            val trainerIcon = decodeSprite(spriteData, CARD_TRAINER_ICON, 16, 16)
            val trainerNameImage = decodeSprite(spriteData, CARD_TRAINER_NAME, 80, 16)
            val routeIcon = decodeSprite(spriteData, CARD_ROUTE_ICON, 16, 16)
            val routeNameImage = decodeSprite(spriteData, ROUTE_NAME, 80, 16)
            val timeText = decodeSprite(spriteData, CARD_TIME_TEXT, 32, 16)
            val daysText = decodeSprite(spriteData, CARD_DAYS_TEXT, 40, 16)
            val stepsText = decodeSprite(spriteData, CARD_STEPS_TEXT, 40, 16)
            val totalDaysText = decodeSprite(spriteData, CARD_TOTAL_DAYS_TEXT, 64, 16)

            val page = state.cardPageIndex.coerceIn(0, DeviceOffsets.STEP_HISTORY_DAYS)

            if (page == 0) {
                blit(frame, arrowReturn, 8, 16, 0, 0)
                if (page < DeviceOffsets.STEP_HISTORY_DAYS) {
                    blit(frame, arrowRight, 8, 8, 88, 4)
                }
                blit(frame, title, 80, 16, 8, 0)

                blit(frame, trainerIcon, 16, 16, 0, 16)
                blit(frame, trainerNameImage, 80, 16, 16, 16)
                blit(frame, routeIcon, 16, 16, 0, 32)
                blit(frame, routeNameImage, 80, 16, 16, 32)

                blit(frame, timeText, 32, 16, 0, 48)

                val now = LocalTime.now()
                drawTimeHmsRight(
                    frame = frame,
                    spriteData = spriteData,
                    hour = now.hour,
                    minute = now.minute,
                    second = now.second,
                    rightX = 95,
                    y = 48,
                )
            } else {
                val historyDayIndex = (page - 1).coerceIn(0, DeviceOffsets.STEP_HISTORY_DAYS - 1)
                val dayOffset = historyDayIndex + 1
                val historySteps = DeviceBinary.readStepHistoryForDay(eeprom, historyDayIndex).coerceAtLeast(0)
                val totalDays = DeviceBinary.readHealthTotalDays(eeprom).coerceAtLeast(0)
                val lifetimeSteps = DeviceBinary.readHealthLifetimeSteps(eeprom).coerceAtLeast(0)
                val dayHeaderWidth = 56
                val dayHeaderX = (WIDTH - dayHeaderWidth) / 2

                blit(frame, arrowLeft, 8, 8, 0, 4)
                if (page < DeviceOffsets.STEP_HISTORY_DAYS) {
                    blit(frame, arrowRight, 8, 8, 88, 4)
                }
                drawNegativeDayValue(frame, spriteData, dayOffset, x = dayHeaderX, y = 0)
                blit(frame, daysText, 40, 16, dayHeaderX + 16, 0)

                drawNumberRight(frame, spriteData, historySteps, 54, 16)
                blit(frame, stepsText, 40, 16, 56, 16)

                blit(frame, totalDaysText, 64, 16, 0, 32)
                drawNumberRight(frame, spriteData, totalDays, 94, 32)

                drawNumberRight(frame, spriteData, lifetimeSteps, 54, 48)
                blit(frame, stepsText, 40, 16, 56, 48)
            }

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent =
                    hasNonBackground(title) ||
                        hasNonBackground(trainerNameImage) ||
                        hasNonBackground(daysText),
            )
        }
    }

    private fun drawTimeHmsRight(
        frame: IntArray,
        spriteData: ByteArray,
        hour: Int,
        minute: Int,
        second: Int,
        rightX: Int,
        y: Int,
    ) {
        with(DeviceLcdRenderer) {
            val hh = hour.coerceIn(0, 23)
            val mm = minute.coerceIn(0, 59)
            val ss = second.coerceIn(0, 59)

            val startX = rightX - 63

            drawTwoDigits(frame, spriteData, hh, startX, y)
            drawTwoDigits(frame, spriteData, mm, startX + 24, y)
            drawTwoDigits(frame, spriteData, ss, startX + 48, y)
            drawColon(frame, startX + 16, y)
            drawColon(frame, startX + 40, y)
        }
    }

    private fun drawTwoDigits(
        frame: IntArray,
        spriteData: ByteArray,
        value: Int,
        x: Int,
        y: Int,
    ) {
        with(DeviceLcdRenderer) {
            val bounded = value.coerceIn(0, 99)
            val tens = bounded / 10
            val ones = bounded % 10
            val tensSprite = decodeSprite(spriteData, DIGIT_BASE + tens * DIGIT_STRIDE, 8, 16)
            val onesSprite = decodeSprite(spriteData, DIGIT_BASE + ones * DIGIT_STRIDE, 8, 16)
            blit(frame, tensSprite, 8, 16, x, y)
            blit(frame, onesSprite, 8, 16, x + 8, y)
        }
    }

    private fun drawColon(
        frame: IntArray,
        x: Int,
        y: Int,
    ) {
        with(DeviceLcdRenderer) {
            val topDotY = y + 5
            val bottomDotY = y + 10
            for (dotX in x + 3..x + 4) {
                if (dotX in 0 until WIDTH) {
                    if (topDotY in 0 until HEIGHT) {
                        frame[topDotY * WIDTH + dotX] = palette[3]
                    }
                    if (bottomDotY in 0 until HEIGHT) {
                        frame[bottomDotY * WIDTH + dotX] = palette[3]
                    }
                }
            }
        }
    }

    private fun drawNegativeDayValue(
        frame: IntArray,
        spriteData: ByteArray,
        day: Int,
        x: Int,
        y: Int,
    ) {
        with(DeviceLcdRenderer) {
            val clampedDay = day.coerceIn(1, DeviceOffsets.STEP_HISTORY_DAYS)

            drawHorizontalLineSegment(
                frame = frame,
                startX = x,
                endX = x + 5,
                y = y + 8,
                color = palette[3],
            )

            val daySprite = decodeSprite(spriteData, DIGIT_BASE + clampedDay * DIGIT_STRIDE, 8, 16)
            blit(frame, daySprite, 8, 16, x + 8, y)
        }
    }
}
