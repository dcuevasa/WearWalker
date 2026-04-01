package com.wearwalker.wearwalker.core

data class LcdPreviewFrame(
    val pixels: IntArray,
    val hasVisualContent: Boolean,
)

object DeviceLcdRenderer {
    const val WIDTH = 96
    const val HEIGHT = 64
    internal var currentSpriteData: ByteArray = ByteArray(0)

    internal val palette =
        intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFB4B4B4.toInt(),
            0xFF464646.toInt(),
            0xFF000000.toInt(),
        )

    internal const val DIGIT_BASE = 0x0280
    internal const val DIGIT_STRIDE = 32
    internal const val DIGIT_SLASH = 0x0400

    internal const val WATT_SYMBOL = 0x0420
    internal const val COMPANIONBALL_8 = 0x0460
    internal const val COMPANIONBALL_8_LIGHT = 0x0470
    internal const val ITEM_8 = 0x0488

    internal const val ARROW_8_UP = 0x04F8
    internal const val ARROW_8_DOWN = 0x0528
    internal const val ARROW_8_LEFT = 0x0558
    internal const val ARROW_8_RIGHT = 0x0588
    internal const val ARROW_RETURN = 0x05F8

    internal const val BUBBLE_EXCLAMATION = 0x0670
    internal const val BUBBLE_HEART = 0x0670 + 0x60
    internal const val BUBBLE_MUSIC = 0x0670 + 0x60 * 2

    internal const val MENU_RADAR = 0x0910
    internal const val MENU_DOWSING = 0x0A50
    internal const val MENU_CONNECT = 0x0B90
    internal const val MENU_CARD = 0x0CD0
    internal const val MENU_PKMN = 0x0E10
    internal const val MENU_SETTINGS = 0x0F50

    internal const val ICON_RADAR = 0x1090
    internal const val ICON_DOWSING = 0x10D0
    internal const val ICON_CONNECT = 0x1110
    internal const val ICON_CARD = 0x1150
    internal const val ICON_PKMN = 0x1190
    internal const val ICON_SETTINGS = 0x11D0

    internal const val SETTINGS_SOUND = 0x1690
    internal const val SETTINGS_SHADE = 0x1730
    internal const val SETTINGS_SOUND_OFF = 0x17D0
    internal const val SETTINGS_SOUND_LOW = 0x1830
    internal const val SETTINGS_SOUND_HIGH = 0x1890
    internal const val SETTINGS_CONTRAST_BAR = 0x18F0

    internal const val CHEST_LARGE = 0x1910

    internal const val DOWSING_BUSH = 0x1B50
    internal const val DOWSING_LEFT = 0x1BD0

    internal const val RADAR_BUSH = 0x1CB0
    internal const val RADAR_EXCL0 = 0x1D70
    internal const val RADAR_EXCL1 = 0x1DB0
    internal const val RADAR_EXCL2 = 0x1DF0
    internal const val RADAR_CLICK = 0x1E30
    internal const val RADAR_ATTACK = 0x1E70
    internal const val RADAR_CRIT = 0x1EF0
    internal const val RADAR_CLOUD = 0x1F70
    internal const val RADAR_HP_BAR = 0x2030
    internal const val RADAR_CATCH = 0x2040
    internal const val RADAR_BATTLE_MENU = 0x2050

    internal const val TEXT_NEED_MORE_WATTS = 0x4330
    internal const val TEXT_NO_COMPANION_HELD = 0x44B0
    internal const val TEXT_NOTHING_HELD = 0x4630
    internal const val TEXT_DISCOVER_ITEM = 0x47B0
    internal const val TEXT_FOUND = 0x4930
    internal const val TEXT_NOTHING_FOUND = 0x4AB0
    internal const val TEXT_ITS_NEAR = 0x4C30
    internal const val TEXT_ITS_FAR = 0x4DB0
    internal const val TEXT_FIND_COMPANION = 0x4F30
    internal const val TEXT_FOUND_SOMETHING = 0x50B0
    internal const val TEXT_IT_GOT_AWAY = 0x5230
    internal const val TEXT_APPEARED = 0x53B0
    internal const val TEXT_WAS_CAUGHT = 0x5530
    internal const val TEXT_FLED = 0x56B0
    internal const val TEXT_ATTACKED = 0x59B0
    internal const val TEXT_EVADED = 0x5B30
    internal const val TEXT_CRITICAL_HIT = 0x5CB0
    internal const val TEXT_BLANK = 0x5E30
    internal const val TEXT_THROW_COMPANIONBALL = 0x5FB0
    internal const val TEXT_SWITCH = 0x8B30

    internal const val CARD_OWNER_ICON = 0x1210
    internal const val CARD_OWNER_NAME = 0x1250
    internal const val CARD_ROUTE_ICON = 0x1390
    internal const val CARD_STEPS_TEXT = 0x13D0
    internal const val CARD_TIME_TEXT = 0x1470
    internal const val CARD_DAYS_TEXT = 0x14F0
    internal const val CARD_TOTAL_DAYS_TEXT = 0x1590

    internal const val ROUTE_IMAGE = DeviceOffsets.AREA_SPRITE_OFFSET
    internal const val ROUTE_NAME = DeviceOffsets.AREA_NAME_SPRITE_OFFSET
    internal const val SMALL_COMPANION = DeviceOffsets.WALK_COMPANION_SMALL_ANIM_OFFSET
    internal const val BIG_COMPANION = DeviceOffsets.WALK_COMPANION_BIG_ANIM_OFFSET
    internal const val WALKING_COMPANION_NAME = DeviceOffsets.WALK_COMPANION_NAME_SPRITE_OFFSET
    internal const val ROUTE_COMPANION_SPRITES = DeviceOffsets.ROUTE_COMPANION_SPRITES_OFFSET
    internal const val ROUTE_COMPANION0_NAME = DeviceOffsets.ROUTE_COMPANION_NAME_SPRITES_OFFSET
    internal const val ROUTE_ITEM0_NAME = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET
    internal const val ROUTE_COMPANION_NAME_STRIDE = 0x140
    internal const val ROUTE_ITEM_NAME_STRIDE = 0x180

    fun render(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        steps: Int,
        watts: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        currentSpriteData = spriteData

        return when (state.screen) {
            DeviceScreen.Home -> renderHome(eeprom, spriteData, state, steps, animationFrame)
            DeviceScreen.Menu -> renderMenu(spriteData, state, watts, animationFrame)
            DeviceScreen.Radar -> renderRadar(eeprom, spriteData, state, watts, animationFrame)
            DeviceScreen.Dowsing -> renderDowsing(eeprom, spriteData, state, watts, animationFrame)
            DeviceScreen.Connect -> renderConnect(spriteData)
            DeviceScreen.Card -> renderCard(eeprom, spriteData, state, animationFrame)
            DeviceScreen.Companion -> renderCompanion(eeprom, spriteData, state, animationFrame)
            DeviceScreen.Settings -> renderSettings(spriteData, state, animationFrame)
        }
    }

    internal fun renderHome(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        steps: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return HomeViewRenderer.render(eeprom, spriteData, state, steps, animationFrame)
    }

    internal fun renderMenu(
        spriteData: ByteArray,
        state: DeviceInteractionState,
        watts: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return MenuViewRenderer.render(spriteData, state, watts, animationFrame)
    }

    internal fun renderRadar(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        watts: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return if (state.radarMode == RadarMode.Scan) {
            RadarViewRenderer.renderScan(eeprom, spriteData, state, animationFrame)
        } else {
            RadarViewRenderer.renderBattle(eeprom, spriteData, state, animationFrame)
        }
    }

    internal fun renderRadarBattle(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return RadarViewRenderer.renderBattle(eeprom, spriteData, state, animationFrame)
    }

    internal fun renderRadarBattleSwap(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return RadarViewRenderer.renderSwap(eeprom, spriteData, state, animationFrame)
    }

    internal fun renderDowsing(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        watts: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return DowsingViewRenderer.renderMain(eeprom, spriteData, state, animationFrame)
    }

    internal fun renderDowsingSwap(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return DowsingViewRenderer.renderSwap(eeprom, spriteData, state, animationFrame)
    }

    internal fun renderConnect(spriteData: ByteArray): LcdPreviewFrame {
        return ConnectViewRenderer.render(spriteData)
    }

    internal fun renderCard(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return CardViewRenderer.render(eeprom, spriteData, state, animationFrame)
    }

    internal fun renderCompanion(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return CollectionViewRenderer.render(eeprom, spriteData, state, animationFrame)
    }

    internal fun renderSettings(
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return SettingsViewRenderer.render(spriteData, state, animationFrame)
    }

    internal fun menuTitleOffset(item: DeviceMenuItem): Int {
        return when (item) {
            DeviceMenuItem.Radar -> MENU_RADAR
            DeviceMenuItem.Dowsing -> MENU_DOWSING
            DeviceMenuItem.Connect -> MENU_CONNECT
            DeviceMenuItem.Card -> MENU_CARD
            DeviceMenuItem.Companion -> MENU_PKMN
            DeviceMenuItem.Settings -> MENU_SETTINGS
        }
    }

    internal fun drawCostAndCurrentWatts(
        frame: IntArray,
        spriteData: ByteArray,
        cost: Int,
        currentWatts: Int,
        y: Int,
    ) {
        val wattSymbol = decodeSprite(spriteData, WATT_SYMBOL, 16, 16)
        val slash = decodeSprite(spriteData, DIGIT_SLASH, 8, 16)

        drawNumberRight(frame, spriteData, cost.coerceAtLeast(0), 15, y)
        blit(frame, wattSymbol, 16, 16, 16, y)

        blit(frame, slash, 8, 16, 34, y)

        drawNumberRight(frame, spriteData, currentWatts.coerceAtLeast(0), 79, y)
        blit(frame, wattSymbol, 16, 16, 80, y)
    }

    internal fun drawMessageBoxBorder(frame: IntArray) {
        drawHorizontalLine(frame, y = 48, color = palette[3])
        drawHorizontalLine(frame, y = 63, color = palette[3])
        drawVerticalLine(frame, x = 0, color = palette[3], yStart = 48, yEndInclusive = 63)
        drawVerticalLine(frame, x = WIDTH - 1, color = palette[3], yStart = 48, yEndInclusive = 63)
    }

    internal fun drawLargeMessageBoxBorder(frame: IntArray) {
        drawHorizontalLine(frame, y = 24, color = palette[3])
        drawHorizontalLine(frame, y = 63, color = palette[3])
        drawVerticalLine(frame, x = 0, color = palette[3], yStart = 24, yEndInclusive = 63)
        drawVerticalLine(frame, x = WIDTH - 1, color = palette[3], yStart = 24, yEndInclusive = 63)
    }

    internal fun drawBattleMessageBoxBorder(frame: IntArray) {
        drawHorizontalLine(frame, y = 32, color = palette[3])
        drawHorizontalLine(frame, y = 63, color = palette[3])
        drawVerticalLine(frame, x = 0, color = palette[3], yStart = 32, yEndInclusive = 63)
        drawVerticalLine(frame, x = WIDTH - 1, color = palette[3], yStart = 32, yEndInclusive = 63)
    }

    internal fun drawLargeMessageLine(
        frame: IntArray,
        messageSprite: IntArray,
        y: Int,
        centerContent: Boolean,
    ) {
        val clipMinX = 1
        val clipMaxX = WIDTH - 2
        val clipMinY = 25
        val clipMaxY = 62
        val lineSprite = messageSprite
        val lineWidth = 96

        val dstX =
            if (centerContent) {
                centeredSpriteDstX(lineSprite, lineWidth, 16, clipMinX, clipMaxX)
            } else {
                0
            }

        blitClipped(
            dst = frame,
            src = lineSprite,
            srcWidth = lineWidth,
            srcHeight = 16,
            dstX = dstX,
            dstY = y,
            clipMinX = clipMinX,
            clipMinY = clipMinY,
            clipMaxX = clipMaxX,
            clipMaxY = clipMaxY,
        )
    }

    internal fun centeredSpriteDstX(
        sprite: IntArray,
        spriteWidth: Int,
        spriteHeight: Int,
        innerLeft: Int,
        innerRight: Int,
    ): Int {
        val bounds = stableOpaqueSpriteBounds(sprite, spriteWidth, spriteHeight) ?: return innerLeft
        val contentLeft = bounds.first
        val contentRight = bounds.second
        val contentWidth = contentRight - contentLeft + 1
        val innerWidth = innerRight - innerLeft + 1

        val minDstX = innerLeft - contentLeft
        val maxDstX = innerRight - contentRight
        if (minDstX > maxDstX) {
            return minDstX
        }

        val targetLeft = innerLeft + (innerWidth - contentWidth) / 2
        val desiredDstX = targetLeft - contentLeft
        return desiredDstX.coerceIn(minDstX, maxDstX)
    }

    internal fun leftAlignedSpriteDstX(
        sprite: IntArray,
        spriteWidth: Int,
        spriteHeight: Int,
        innerLeft: Int,
        innerRight: Int,
    ): Int {
        val bounds = stableOpaqueSpriteBounds(sprite, spriteWidth, spriteHeight) ?: return innerLeft
        val contentLeft = bounds.first
        val contentRight = bounds.second
        val minDstX = innerLeft - contentLeft
        val maxDstX = innerRight - contentRight
        return minDstX.coerceAtMost(maxDstX)
    }

    internal fun opaqueSpriteBounds(
        sprite: IntArray,
        spriteWidth: Int,
        spriteHeight: Int,
    ): Pair<Int, Int>? {
        var minX = spriteWidth
        var maxX = -1

        for (y in 0 until spriteHeight) {
            val rowStart = y * spriteWidth
            for (x in 0 until spriteWidth) {
                if (sprite[rowStart + x] != palette[0]) {
                    if (x < minX) {
                        minX = x
                    }
                    if (x > maxX) {
                        maxX = x
                    }
                }
            }
        }

        return if (maxX < minX) {
            null
        } else {
            minX to maxX
        }
    }

    internal fun stableOpaqueSpriteBounds(
        sprite: IntArray,
        spriteWidth: Int,
        spriteHeight: Int,
    ): Pair<Int, Int>? {
        val base = opaqueSpriteBounds(sprite, spriteWidth, spriteHeight) ?: return null
        val columnCounts = IntArray(spriteWidth)

        for (y in 0 until spriteHeight) {
            val rowStart = y * spriteWidth
            for (x in 0 until spriteWidth) {
                if (sprite[rowStart + x] != palette[0]) {
                    columnCounts[x] += 1
                }
            }
        }

        var left = base.first
        var right = base.second

        while (left < right && columnCounts[left] <= 1) {
            left += 1
        }
        while (right > left && columnCounts[right] <= 1) {
            right -= 1
        }

        return left to right
    }

    internal fun selectorBounceOffset(animationFrame: Int): Int {
        return if (animationFrame % 2 == 0) 0 else 1
    }

    internal fun drawBottomMessageBox(
        frame: IntArray,
        messageSprite: IntArray,
        messageWidth: Int = 96,
        messageX: Int = 0,
        clipToInner: Boolean = false,
    ) {
        if (clipToInner) {
            fillRect(frame, 1, 49, WIDTH - 2, 62, palette[0])
            blitClipped(
                dst = frame,
                src = messageSprite,
                srcWidth = messageWidth,
                srcHeight = 16,
                dstX = messageX,
                dstY = 49,
                clipMinX = 1,
                clipMinY = 49,
                clipMaxX = WIDTH - 2,
                clipMaxY = 62,
            )
        } else {
            fillRect(frame, 0, 49, WIDTH - 1, 62, palette[0])
            blit(frame, messageSprite, messageWidth, 16, messageX, 49)
        }
        drawMessageBoxBorder(frame)
    }

    internal fun fillRect(
        frame: IntArray,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Int,
    ) {
        for (y in startY..endY) {
            if (y !in 0 until HEIGHT) {
                continue
            }
            for (x in startX..endX) {
                if (x in 0 until WIDTH) {
                    frame[y * WIDTH + x] = color
                }
            }
        }
    }

    internal fun dimSprite(src: IntArray): IntArray {
        return IntArray(src.size) { index ->
            when (src[index]) {
                palette[3] -> palette[2]
                palette[2] -> palette[1]
                palette[1] -> palette[0]
                else -> palette[0]
            }
        }
    }

    internal fun drawNumberRight(
        frame: IntArray,
        eeprom: ByteArray,
        value: Int,
        rightX: Int,
        y: Int,
    ) {
        val text = value.toString()
        var x = rightX - 7
        for (char in text.reversed()) {
            val digit = char.digitToIntOrNull() ?: continue
            val digitSprite = decodeSprite(eeprom, DIGIT_BASE + digit * DIGIT_STRIDE, 8, 16)
            blit(frame, digitSprite, 8, 16, x, y)
            x -= 8
        }
    }

    internal fun drawFixed4Digits(
        frame: IntArray,
        spriteData: ByteArray,
        value: Int,
        rightX: Int,
        y: Int,
    ) {
        val bounded = value.coerceIn(0, 9999)
        val d0 = bounded / 1000
        val d1 = (bounded / 100) % 10
        val d2 = (bounded / 10) % 10
        val d3 = bounded % 10
        val digits = intArrayOf(d0, d1, d2, d3)

        var x = rightX - 31
        for (digit in digits) {
            val sprite = decodeSprite(spriteData, DIGIT_BASE + digit * DIGIT_STRIDE, 8, 16)
            blit(frame, sprite, 8, 16, x, y)
            x += 8
        }
    }

    internal fun decodeAnimatedSprite(
        eeprom: ByteArray,
        baseOffset: Int,
        width: Int,
        height: Int,
        frame: Int,
    ): IntArray {
        val frameStride = (width * height) / 4
        val frameIndex = if (frame and 0x1 == 0) 0 else 1
        return decodeSprite(eeprom, baseOffset + frameIndex * frameStride, width, height)
    }

    internal fun decodeSprite(
        eeprom: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
    ): IntArray {
        val source = selectSpriteSource(offset)
        val output = IntArray(width * height) { palette[0] }
        val stripes = height / 8

        for (stripe in 0 until stripes) {
            for (x in 0 until width) {
                val base = offset + (stripe * width + x) * 2
                val firstByte = readUnsigned(source, base)
                val secondByte = readUnsigned(source, base + 1)

                for (bit in 0 until 8) {
                    val y = stripe * 8 + bit
                    if (y >= height) {
                        continue
                    }
                    val firstBit = (firstByte ushr bit) and 0x1
                    val secondBit = (secondByte ushr bit) and 0x1
                    val paletteIndex = (firstBit shl 1) or secondBit
                    output[y * width + x] = palette[paletteIndex]
                }
            }
        }

        return output
    }

    internal fun selectSpriteSource(offset: Int): ByteArray {
        return currentSpriteData
    }

    internal fun readUnsigned(
        data: ByteArray,
        index: Int,
    ): Int {
        if (index < 0 || index >= data.size) {
            return 0
        }
        return data[index].toInt() and 0xFF
    }

    internal fun blit(
        dst: IntArray,
        src: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        dstX: Int,
        dstY: Int,
    ) {
        for (y in 0 until srcHeight) {
            val targetY = dstY + y
            if (targetY !in 0 until HEIGHT) {
                continue
            }
            for (x in 0 until srcWidth) {
                val targetX = dstX + x
                if (targetX !in 0 until WIDTH) {
                    continue
                }
                val pixel = src[y * srcWidth + x]
                if (pixel != palette[0]) {
                    dst[targetY * WIDTH + targetX] = pixel
                }
            }
        }
    }

    internal fun blitClipped(
        dst: IntArray,
        src: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        dstX: Int,
        dstY: Int,
        clipMinX: Int,
        clipMinY: Int,
        clipMaxX: Int,
        clipMaxY: Int,
    ) {
        for (y in 0 until srcHeight) {
            val targetY = dstY + y
            if (targetY !in clipMinY..clipMaxY || targetY !in 0 until HEIGHT) {
                continue
            }
            for (x in 0 until srcWidth) {
                val targetX = dstX + x
                if (targetX !in clipMinX..clipMaxX || targetX !in 0 until WIDTH) {
                    continue
                }
                val pixel = src[y * srcWidth + x]
                if (pixel != palette[0]) {
                    dst[targetY * WIDTH + targetX] = pixel
                }
            }
        }
    }

    internal fun drawHorizontalLine(
        frame: IntArray,
        y: Int,
        color: Int,
    ) {
        if (y !in 0 until HEIGHT) {
            return
        }
        for (x in 0 until WIDTH) {
            frame[y * WIDTH + x] = color
        }
    }

    internal fun drawHorizontalLineSegment(
        frame: IntArray,
        startX: Int,
        endX: Int,
        y: Int,
        color: Int,
    ) {
        if (y !in 0 until HEIGHT) {
            return
        }
        for (x in startX..endX) {
            if (x in 0 until WIDTH) {
                frame[y * WIDTH + x] = color
            }
        }
    }

    internal fun drawBattleSplit(frame: IntArray) {
        for (x in 0 until WIDTH) {
            val y = 31 - (x / 10)
            if (y in 0 until HEIGHT) {
                frame[y * WIDTH + x] = palette[2]
            }
            if (y + 1 in 0 until HEIGHT) {
                frame[(y + 1) * WIDTH + x] = palette[1]
            }
        }
    }

    internal fun drawRadarHpBlocks(
        frame: IntArray,
        hpBlockSprite: IntArray,
        startX: Int,
        startY: Int,
        hp: Int,
        maxHp: Int,
    ) {
        val clampedHp = hp.coerceIn(0, maxHp)
        for (index in 0 until maxHp) {
            val x = startX + index * 8
            val filled = index < clampedHp
            if (filled) {
                blit(frame, hpBlockSprite, 8, 8, x, startY)
            } else {
                fillRect(frame, x, startY, x + 7, startY + 7, palette[0])
            }
        }
    }

    internal fun drawTinyWord(
        frame: IntArray,
        text: String,
        startX: Int,
        startY: Int,
        color: Int,
    ) {
        var x = startX
        for (char in text) {
            drawTinyChar(frame, char, x, startY, color)
            x += 6
        }
    }

    internal fun drawTinyChar(
        frame: IntArray,
        char: Char,
        x: Int,
        y: Int,
        color: Int,
    ) {
        val glyph =
            when (char) {
                'A' -> arrayOf("0110", "1001", "1111", "1001", "1001")
                'C' -> arrayOf("0111", "1000", "1000", "1000", "0111")
                'E' -> arrayOf("1111", "1000", "1110", "1000", "1111")
                'F' -> arrayOf("1111", "1000", "1110", "1000", "1000")
                'L' -> arrayOf("1000", "1000", "1000", "1000", "1111")
                'N' -> arrayOf("1001", "1101", "1011", "1001", "1001")
                'R' -> arrayOf("1110", "1001", "1110", "1010", "1001")
                else -> return
            }

        glyph.forEachIndexed { row, rowBits ->
            rowBits.forEachIndexed { col, bit ->
                if (bit == '1') {
                    val px = x + col
                    val py = y + row
                    if (px in 0 until WIDTH && py in 0 until HEIGHT) {
                        frame[py * WIDTH + px] = color
                    }
                }
            }
        }
    }

    internal fun drawVerticalLine(
        frame: IntArray,
        x: Int,
        color: Int,
        yStart: Int,
        yEndInclusive: Int,
    ) {
        if (x !in 0 until WIDTH) {
            return
        }
        for (y in yStart..yEndInclusive) {
            if (y in 0 until HEIGHT) {
                frame[y * WIDTH + x] = color
            }
        }
    }

    internal fun drawHpBlocks(
        frame: IntArray,
        startX: Int,
        startY: Int,
        hp: Int,
        maxHp: Int,
    ) {
        val clampedHp = hp.coerceIn(0, maxHp)
        for (block in 0 until maxHp) {
            val blockX = startX + block * 7
            val filled = block < clampedHp

            for (y in startY until startY + 4) {
                if (y !in 0 until HEIGHT) {
                    continue
                }
                for (x in blockX until blockX + 6) {
                    if (x !in 0 until WIDTH) {
                        continue
                    }
                    val isBorder = y == startY || y == startY + 3 || x == blockX || x == blockX + 5
                    frame[y * WIDTH + x] =
                        if (isBorder) {
                            palette[2]
                        } else if (filled) {
                            palette[3]
                        } else {
                            palette[0]
                        }
                }
            }
        }
    }

    internal fun hasNonBackground(pixels: IntArray): Boolean {
        return pixels.any { pixel -> pixel != palette[0] }
    }

    internal fun wrapIndex(
        value: Int,
        size: Int,
    ): Int {
        if (size <= 0) return 0
        val mod = value % size
        return if (mod < 0) mod + size else mod
    }
}
