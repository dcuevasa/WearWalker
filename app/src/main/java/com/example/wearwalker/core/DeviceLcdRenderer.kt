package com.example.wearwalker.core

import java.time.LocalTime

data class LcdPreviewFrame(
    val pixels: IntArray,
    val hasVisualContent: Boolean,
)

object DeviceLcdRenderer {
    const val WIDTH = 96
    const val HEIGHT = 64
    private var currentSpriteData: ByteArray = ByteArray(0)

    private val palette =
        intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFB4B4B4.toInt(),
            0xFF464646.toInt(),
            0xFF000000.toInt(),
        )

    private const val DIGIT_BASE = 0x0280
    private const val DIGIT_STRIDE = 32
    private const val DIGIT_SLASH = 0x0400

    private const val WATT_SYMBOL = 0x0420
    private const val POKEBALL_8 = 0x0460
    private const val POKEBALL_8_LIGHT = 0x0470
    private const val ITEM_8 = 0x0488

    private const val ARROW_8_UP = 0x04F8
    private const val ARROW_8_DOWN = 0x0528
    private const val ARROW_8_LEFT = 0x0558
    private const val ARROW_8_RIGHT = 0x0588
    private const val ARROW_RETURN = 0x05F8

    private const val BUBBLE_EXCLAMATION = 0x0670
    private const val BUBBLE_HEART = 0x0670 + 0x60
    private const val BUBBLE_MUSIC = 0x0670 + 0x60 * 2

    private const val MENU_RADAR = 0x0910
    private const val MENU_DOWSING = 0x0A50
    private const val MENU_CONNECT = 0x0B90
    private const val MENU_CARD = 0x0CD0
    private const val MENU_PKMN = 0x0E10
    private const val MENU_SETTINGS = 0x0F50

    private const val ICON_RADAR = 0x1090
    private const val ICON_DOWSING = 0x10D0
    private const val ICON_CONNECT = 0x1110
    private const val ICON_CARD = 0x1150
    private const val ICON_PKMN = 0x1190
    private const val ICON_SETTINGS = 0x11D0

    private const val SETTINGS_SOUND = 0x1690
    private const val SETTINGS_SHADE = 0x1730
    private const val SETTINGS_SOUND_OFF = 0x17D0
    private const val SETTINGS_SOUND_LOW = 0x1830
    private const val SETTINGS_SOUND_HIGH = 0x1890

    private const val CHEST_LARGE = 0x1910

    private const val DOWSING_BUSH = 0x1B50
    private const val DOWSING_LEFT = 0x1BD0

    private const val RADAR_BUSH = 0x1CB0
    private const val RADAR_EXCL0 = 0x1D70
    private const val RADAR_EXCL1 = 0x1DB0
    private const val RADAR_EXCL2 = 0x1DF0
    private const val RADAR_CLICK = 0x1E30
    private const val RADAR_ATTACK = 0x1E70
    private const val RADAR_CRIT = 0x1EF0
    private const val RADAR_CLOUD = 0x1F70
    private const val RADAR_HP_BAR = 0x2030
    private const val RADAR_CATCH = 0x2040
    private const val RADAR_BATTLE_MENU = 0x2050

    private const val TEXT_NEED_MORE_WATTS = 0x4330
    private const val TEXT_NO_POKE_HELD = 0x44B0
    private const val TEXT_NOTHING_HELD = 0x4630
    private const val TEXT_DISCOVER_ITEM = 0x47B0
    private const val TEXT_FOUND = 0x4930
    private const val TEXT_NOTHING_FOUND = 0x4AB0
    private const val TEXT_ITS_NEAR = 0x4C30
    private const val TEXT_ITS_FAR = 0x4DB0
    private const val TEXT_FIND_POKE = 0x4F30
    private const val TEXT_FOUND_SOMETHING = 0x50B0
    private const val TEXT_IT_GOT_AWAY = 0x5230
    private const val TEXT_APPEARED = 0x53B0
    private const val TEXT_WAS_CAUGHT = 0x5530
    private const val TEXT_FLED = 0x56B0
    private const val TEXT_ATTACKED = 0x59B0
    private const val TEXT_EVADED = 0x5B30
    private const val TEXT_CRITICAL_HIT = 0x5CB0
    private const val TEXT_BLANK = 0x5E30
    private const val TEXT_THROW_POKEBALL = 0x5FB0
    private const val TEXT_SWITCH = 0x8B30

    private const val CARD_TRAINER_ICON = 0x1210
    private const val CARD_TRAINER_NAME = 0x1250
    private const val CARD_ROUTE_ICON = 0x1390
    private const val CARD_STEPS_TEXT = 0x13D0
    private const val CARD_TOTAL_DAYS_TEXT = 0x1590

    private const val ROUTE_IMAGE = DeviceOffsets.AREA_SPRITE_OFFSET
    private const val ROUTE_NAME = DeviceOffsets.AREA_NAME_SPRITE_OFFSET
    private const val SMALL_POKEMON = DeviceOffsets.WALK_POKEMON_SMALL_ANIM_OFFSET
    private const val BIG_POKEMON = DeviceOffsets.WALK_POKEMON_BIG_ANIM_OFFSET
    private const val WALKING_POKEMON_NAME = DeviceOffsets.WALK_POKEMON_NAME_SPRITE_OFFSET
    private const val ROUTE_POKEMON_SPRITES = DeviceOffsets.ROUTE_POKEMON_SPRITES_OFFSET
    private const val ROUTE_POKEMON0_NAME = DeviceOffsets.ROUTE_POKEMON_NAME_SPRITES_OFFSET
    private const val ROUTE_ITEM0_NAME = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET
    private const val ROUTE_POKEMON_NAME_STRIDE = 0x140
    private const val ROUTE_ITEM_NAME_STRIDE = 0x180

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
            DeviceScreen.Pokemon -> renderPokemon(eeprom, spriteData, state, animationFrame)
            DeviceScreen.Settings -> renderSettings(spriteData, state)
        }
    }

    private fun renderHome(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        steps: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

        val hasWalkingPokemon = DeviceBinary.readWalkingPokemonSpecies(eeprom) != 0
        val bigPokemon =
            if (hasWalkingPokemon) {
                decodeAnimatedSprite(spriteData, BIG_POKEMON, 64, 48, animationFrame)
            } else {
                IntArray(64 * 48) { palette[0] }
            }
        val routeImage = decodeSprite(spriteData, ROUTE_IMAGE, 32, 24)
        val bubble =
            if (!hasWalkingPokemon || state.homeEventType == null) {
                null
            } else if (state.homeEventMusicBubble) {
                decodeSprite(spriteData, BUBBLE_MUSIC, 24, 16)
            } else {
                decodeSprite(spriteData, BUBBLE_EXCLAMATION, 24, 16)
            }
        val pokeball = decodeSprite(spriteData, POKEBALL_8, 8, 8)
        val item = decodeSprite(spriteData, ITEM_8, 8, 8)

        val yBob = if (animationFrame % 2 == 0) 0 else 1
        blit(frame, bigPokemon, 64, 48, 32, yBob)
        blit(frame, routeImage, 32, 24, 0, 24)
        if (bubble != null && animationFrame % 4 != 3) {
            blit(frame, bubble, 24, 16, 8, 4)
        }

        repeat(state.caughtPokemonCount.coerceIn(0, 3)) { index ->
            blit(frame, pokeball, 8, 8, index * 8, HEIGHT - 8)
        }
        repeat(state.foundItemCount.coerceIn(0, 3)) { index ->
            blit(frame, item, 8, 8, 24 + index * 8, HEIGHT - 8)
        }

        drawNumberRight(frame, spriteData, steps.coerceAtLeast(0), 95, HEIGHT - 16)
        drawHorizontalLine(frame, y = 48, color = palette[3])

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(bigPokemon) || hasNonBackground(routeImage),
        )
    }

    private fun renderMenu(
        spriteData: ByteArray,
        state: DeviceInteractionState,
        watts: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
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

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(selectedTitle) || hasNonBackground(icons.first()),
        )
    }

    private fun renderRadar(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        watts: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        if (state.radarMode != RadarMode.Scan) {
            return renderRadarBattle(eeprom, spriteData, state, animationFrame)
        }

        val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

        val radarBush = decodeSprite(spriteData, RADAR_BUSH, 32, 24)
        val arrowRight = decodeSprite(spriteData, ARROW_8_RIGHT, 8, 8)
        val exclamation0 = decodeSprite(spriteData, RADAR_EXCL0, 16, 16)
        val exclamation1 = decodeSprite(spriteData, RADAR_EXCL1, 16, 16)
        val exclamation2 = decodeSprite(spriteData, RADAR_EXCL2, 16, 16)
        val click = decodeSprite(spriteData, RADAR_CLICK, 16, 16)
        val findText = decodeSprite(spriteData, TEXT_FIND_POKE, 96, 16)
        val foundSomethingText = decodeSprite(spriteData, TEXT_FOUND_SOMETHING, 96, 16)
        val noMatchText = decodeSprite(spriteData, TEXT_NOTHING_FOUND, 96, 16)
        val gotAwayText = decodeSprite(spriteData, TEXT_IT_GOT_AWAY, 96, 16)

        val patches =
            arrayOf(
                8 to 0,
                56 to 0,
                16 to 24,
                64 to 24,
            )

        for (index in patches.indices) {
            val (x, y) = patches[index]
            blit(frame, radarBush, 32, 24, x, y)
            if (index == state.radarCursor) {
                blit(frame, arrowRight, 8, 8, x - 8, y + 8)
            }
        }

        val signalCursor = state.radarSignalCursor
        if (signalCursor != null && signalCursor in patches.indices && animationFrame % 2 == 0) {
            val exclamation =
                when (state.radarSignalLevel.coerceIn(1, 3)) {
                    1 -> exclamation0
                    2 -> exclamation1
                    else -> exclamation2
                }
            val (x, y) = patches[signalCursor]
            val exclamationX = (x + 16).coerceAtMost(WIDTH - 16)
            val exclamationY = (y - 8).coerceAtLeast(0)
            blit(frame, exclamation, 16, 16, exclamationX, exclamationY)
        }

        val bottomMessage =
            when (state.radarOutcome) {
                true -> {
                    val signal = state.radarResolvedCursor ?: state.radarCursor
                    val (x, y) = patches[signal.coerceIn(0, patches.lastIndex)]
                    val clickX = (x + 16).coerceAtMost(WIDTH - 16)
                    val clickY = (y - 8).coerceAtLeast(0)
                    blit(frame, click, 16, 16, clickX, clickY)
                    foundSomethingText
                }

                false -> {
                    gotAwayText
                }

                null -> {
                    if (state.radarSignalCursor == null) {
                        findText
                    } else {
                        foundSomethingText
                    }
                }
            }

        drawBottomMessageBox(frame, bottomMessage)

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(radarBush),
        )
    }

    private fun renderRadarBattle(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        if (state.radarMode == RadarMode.BattleSwap) {
            return renderRadarBattleSwap(eeprom, spriteData, state, animationFrame)
        }

        val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

        val playerPoke = decodeAnimatedSprite(spriteData, SMALL_POKEMON, 32, 24, animationFrame)
        val routeSlot = state.radarBattlePokemonSlot.coerceIn(0, 2)
        val enemyPoke =
            decodeAnimatedSprite(
                spriteData,
                ROUTE_POKEMON_SPRITES + routeSlot * 0x180,
                32,
                24,
                animationFrame,
            )
        val battleMenu = decodeSprite(spriteData, RADAR_BATTLE_MENU, 96, 32)
        val attackFlash = decodeSprite(spriteData, RADAR_ATTACK, 16, 32)
        val critFlash = decodeSprite(spriteData, RADAR_CRIT, 16, 32)
        val cloud = decodeSprite(spriteData, RADAR_CLOUD, 32, 24)
        val hpBarBlock = decodeSprite(spriteData, RADAR_HP_BAR, 8, 8)
        val catchStars = decodeSprite(spriteData, RADAR_CATCH, 8, 8)

        val battleAnimation =
            if (state.radarMode == RadarMode.BattleMessage && state.radarBattleAnimTicksRemaining > 0) {
                state.radarBattleAnimation
            } else {
                RadarBattleAnimation.None
            }
        val animTicks = state.radarBattleAnimTicksRemaining.coerceAtLeast(0)

        var enemyX = 6
        var enemyY = 4
        var playerX = 58
        var playerY = 6
        var showImpact = false
        var impactSprite = attackFlash
        var impactWidth = 16
        var impactHeight = 32
        var impactX = 24
        var impactY = 0
        var showSmoke = false
        var smokeX = 8
        var smokeY = 4
        var showCatchStars = false
        var catchStarsX = 0
        var catchStarsY = 0
        var hideEnemy = false
        var showBall = false
        var ballX = 0
        var ballY = 0
        val messageOffset = state.radarBattleMessageOffset ?: TEXT_BLANK

        when (battleAnimation) {
            RadarBattleAnimation.AttackHit -> {
                playerX = 54
                showImpact = true
                impactX = 40
            }

            RadarBattleAnimation.AttackCrit -> {
                playerX = 54
                showImpact = true
                impactSprite = critFlash
                impactX = 40
            }

            RadarBattleAnimation.AttackTrade -> {
                if (animTicks >= 2) {
                    playerX = 52
                    showImpact = true
                    impactX = 40
                } else {
                    enemyX = 28
                    showImpact = true
                    impactX = playerX - 10
                }
            }

            RadarBattleAnimation.AttackEnemyEvade -> {
                playerX = 52
                enemyX = if (animTicks >= 2) -8 else -16
            }

            RadarBattleAnimation.EnemyAttack -> {
                if (animTicks >= 2) {
                    enemyX = 18
                } else {
                    enemyX = 28
                    showImpact = true
                    impactX = playerX - 10
                }
            }

            RadarBattleAnimation.EvadeCounter -> {
                if (animTicks >= 2) {
                    playerX = 68
                    enemyX = 24
                    showSmoke = true
                    smokeX = 26
                    smokeY = 7
                } else {
                    playerX = 56
                    enemyX = 8
                    showImpact = true
                    impactX = enemyX + 20
                }
            }

            RadarBattleAnimation.EvadeStandoff -> {
                if (animTicks >= 2) {
                    playerX = 66
                    enemyX = 0
                } else {
                    playerX = 60
                    enemyX = 4
                }
            }

            RadarBattleAnimation.EvadeEnemyFlee -> {
                val phase = (3 - animTicks).coerceAtLeast(0)
                enemyX = 6 - phase * 18
                showSmoke = true
                smokeX = (enemyX + 10).coerceAtLeast(0)
                smokeY = 6
            }

            RadarBattleAnimation.CatchThrow -> {
                val throwStep = (3 - animTicks).coerceIn(0, 2)
                showBall = true
                ballX = 88 - throwStep * 35
                ballY = 55 - throwStep * 21
                if (animTicks == 1) {
                    showSmoke = true
                    smokeX = 8
                    smokeY = 4
                }
            }

            RadarBattleAnimation.CatchWiggle -> {
                hideEnemy = true
                showBall = true
                val wiggleOffset = if (animTicks == 2) 1 else -1
                ballX = 18 + wiggleOffset
                ballY = 12
            }

            RadarBattleAnimation.CatchSuccess -> {
                hideEnemy = true
                showBall = true
                ballX = 18
                ballY = 12
                showCatchStars = true
                catchStarsX = 14
                catchStarsY = 6
            }

            RadarBattleAnimation.CatchFail -> {
                when {
                    animTicks >= 4 -> {
                        showBall = true
                        ballX = 18
                        ballY = 12
                        hideEnemy = true
                    }

                    animTicks == 3 -> {
                        showBall = true
                        ballX = 18
                        ballY = 12
                        hideEnemy = true
                        showSmoke = true
                        smokeX = 8
                        smokeY = 4
                    }

                    animTicks == 2 -> {
                        showBall = false
                        hideEnemy = false
                        enemyX = 6
                    }

                    else -> {
                        showBall = false
                        hideEnemy = false
                        enemyX = -10 + selectorBounceOffset(animationFrame)
                        showSmoke = true
                        smokeX = (enemyX + 10).coerceAtLeast(0)
                        smokeY = 6
                    }
                }
            }

            RadarBattleAnimation.None -> {
                // Keep default battlefield positions.
            }
        }

        if (battleAnimation == RadarBattleAnimation.None && (messageOffset == TEXT_FLED || messageOffset == TEXT_IT_GOT_AWAY)) {
            hideEnemy = true
        }

        if (!hideEnemy) {
            blit(frame, enemyPoke, 32, 24, enemyX, enemyY)
        }
        blit(frame, playerPoke, 32, 24, playerX, playerY)

        if (showImpact) {
            blit(frame, impactSprite, impactWidth, impactHeight, impactX, impactY)
        }

        if (showSmoke) {
            blit(frame, cloud, 32, 24, smokeX, smokeY)
        }

        if (showCatchStars) {
            blit(frame, catchStars, 8, 8, catchStarsX, catchStarsY)
            blit(frame, catchStars, 8, 8, catchStarsX + 10, catchStarsY + 2)
            blit(frame, catchStars, 8, 8, catchStarsX + 5, catchStarsY + 9)
        }

        if (showBall) {
            val pokeball = decodeSprite(spriteData, POKEBALL_8, 8, 8)
            blit(frame, pokeball, 8, 8, ballX, ballY)
        }

        drawRadarHpBlocks(frame, hpBarBlock, startX = 2, startY = 24, hp = state.radarEnemyHp, maxHp = 4)
        drawRadarHpBlocks(frame, hpBarBlock, startX = 62, startY = 0, hp = state.radarPlayerHp, maxHp = 4)

        val showBattleMenu = state.radarMode == RadarMode.BattleMenu && state.radarBattleMessageOffset == null
        if (showBattleMenu) {
            fillRect(frame, 0, 32, WIDTH - 1, 63, palette[0])
            blit(frame, battleMenu, 96, 32, 0, 32)
            drawHorizontalLine(frame, y = 31, color = palette[3])
        } else {
            if (messageOffset == TEXT_BLANK && battleAnimation == RadarBattleAnimation.CatchFail) {
                // Keep the battlefield visible during flee animation, without showing a text box yet.
            } else if (messageOffset == TEXT_APPEARED) {
                val message = decodeSprite(spriteData, TEXT_APPEARED, 96, 16)
                val pokemonName =
                    decodeSprite(
                        spriteData,
                        ROUTE_POKEMON0_NAME + routeSlot * ROUTE_POKEMON_NAME_STRIDE,
                        80,
                        16,
                    )
                fillRect(frame, 0, 32, WIDTH - 1, HEIGHT - 1, palette[0])
                drawBattleMessageBoxBorder(frame)

                val nameX = leftAlignedSpriteDstX(pokemonName, 80, 16, innerLeft = 1, innerRight = 87)
                blitClipped(
                    dst = frame,
                    src = pokemonName,
                    srcWidth = 80,
                    srcHeight = 16,
                    dstX = nameX,
                    dstY = 33,
                    clipMinX = 1,
                    clipMinY = 33,
                    clipMaxX = WIDTH - 2,
                    clipMaxY = 62,
                )

                val appearedX = leftAlignedSpriteDstX(message, 96, 16, innerLeft = 1, innerRight = WIDTH - 2)
                blitClipped(
                    dst = frame,
                    src = message,
                    srcWidth = 96,
                    srcHeight = 16,
                    dstX = appearedX,
                    dstY = 48,
                    clipMinX = 1,
                    clipMinY = 33,
                    clipMaxX = WIDTH - 2,
                    clipMaxY = 62,
                )
            } else if (messageOffset == TEXT_FOUND_SOMETHING) {
                val pokemonName =
                    decodeSprite(
                        spriteData,
                        ROUTE_POKEMON0_NAME + routeSlot * ROUTE_POKEMON_NAME_STRIDE,
                        80,
                        16,
                    )
                drawBottomMessageBox(
                    frame = frame,
                    messageSprite = pokemonName,
                    messageWidth = 80,
                    messageX = 8,
                    clipToInner = false,
                )
            } else if (
                messageOffset == TEXT_ATTACKED &&
                state.radarBattleAction == RadarBattleAction.Attack
            ) {
                val attacked = decodeSprite(spriteData, TEXT_ATTACKED, 96, 16)
                val walkingName =
                    decodeSprite(
                        spriteData,
                        WALKING_POKEMON_NAME,
                        80,
                        16,
                    )
                val enemyName =
                    decodeSprite(
                        spriteData,
                        ROUTE_POKEMON0_NAME + routeSlot * ROUTE_POKEMON_NAME_STRIDE,
                        80,
                        16,
                    )

                val nameSprite = if (state.radarBattleMessageUsesEnemyName) enemyName else walkingName

                fillRect(frame, 0, 32, WIDTH - 1, HEIGHT - 1, palette[0])
                drawBattleMessageBoxBorder(frame)

                val nameX = leftAlignedSpriteDstX(nameSprite, 80, 16, innerLeft = 1, innerRight = 87)
                blitClipped(
                    dst = frame,
                    src = nameSprite,
                    srcWidth = 80,
                    srcHeight = 16,
                    dstX = nameX,
                    dstY = 33,
                    clipMinX = 1,
                    clipMinY = 33,
                    clipMaxX = WIDTH - 2,
                    clipMaxY = 62,
                )

                val attackedX = leftAlignedSpriteDstX(attacked, 96, 16, innerLeft = 1, innerRight = WIDTH - 2)
                blitClipped(
                    dst = frame,
                    src = attacked,
                    srcWidth = 96,
                    srcHeight = 16,
                    dstX = attackedX,
                    dstY = 48,
                    clipMinX = 1,
                    clipMinY = 33,
                    clipMaxX = WIDTH - 2,
                    clipMaxY = 62,
                )
            } else if (messageOffset == TEXT_CRITICAL_HIT) {
                val critical = decodeSprite(spriteData, TEXT_CRITICAL_HIT, 96, 16)
                fillRect(frame, 0, 32, WIDTH - 1, HEIGHT - 1, palette[0])
                drawBattleMessageBoxBorder(frame)

                val criticalX = leftAlignedSpriteDstX(critical, 96, 16, innerLeft = 1, innerRight = WIDTH - 2)
                blitClipped(
                    dst = frame,
                    src = critical,
                    srcWidth = 96,
                    srcHeight = 16,
                    dstX = criticalX,
                    dstY = 33,
                    clipMinX = 1,
                    clipMinY = 33,
                    clipMaxX = WIDTH - 2,
                    clipMaxY = 62,
                )
            } else if (messageOffset == TEXT_EVADED && state.radarBattleAction == RadarBattleAction.Attack) {
                val evaded = decodeSprite(spriteData, TEXT_EVADED, 96, 16)
                val enemyName =
                    decodeSprite(
                        spriteData,
                        ROUTE_POKEMON0_NAME + routeSlot * ROUTE_POKEMON_NAME_STRIDE,
                        80,
                        16,
                    )
                fillRect(frame, 0, 32, WIDTH - 1, HEIGHT - 1, palette[0])
                drawBattleMessageBoxBorder(frame)

                val nameX = leftAlignedSpriteDstX(enemyName, 80, 16, innerLeft = 1, innerRight = 87)
                blitClipped(
                    dst = frame,
                    src = enemyName,
                    srcWidth = 80,
                    srcHeight = 16,
                    dstX = nameX,
                    dstY = 33,
                    clipMinX = 1,
                    clipMinY = 33,
                    clipMaxX = WIDTH - 2,
                    clipMaxY = 62,
                )

                val evadedX = leftAlignedSpriteDstX(evaded, 96, 16, innerLeft = 1, innerRight = WIDTH - 2)
                blitClipped(
                    dst = frame,
                    src = evaded,
                    srcWidth = 96,
                    srcHeight = 16,
                    dstX = evadedX,
                    dstY = 48,
                    clipMinX = 1,
                    clipMinY = 33,
                    clipMaxX = WIDTH - 2,
                    clipMaxY = 62,
                )
            } else if (messageOffset == TEXT_FLED) {
                val fled = decodeSprite(spriteData, TEXT_FLED, 96, 16)
                val pokemonName =
                    decodeSprite(
                        spriteData,
                        ROUTE_POKEMON0_NAME + routeSlot * ROUTE_POKEMON_NAME_STRIDE,
                        80,
                        16,
                    )

                fillRect(frame, 0, 32, WIDTH - 1, HEIGHT - 1, palette[0])
                drawBattleMessageBoxBorder(frame)

                val nameX = leftAlignedSpriteDstX(pokemonName, 80, 16, innerLeft = 1, innerRight = 87)
                blitClipped(
                    dst = frame,
                    src = pokemonName,
                    srcWidth = 80,
                    srcHeight = 16,
                    dstX = nameX,
                    dstY = 33,
                    clipMinX = 1,
                    clipMinY = 33,
                    clipMaxX = WIDTH - 2,
                    clipMaxY = 62,
                )

                val fledX = leftAlignedSpriteDstX(fled, 96, 16, innerLeft = 1, innerRight = WIDTH - 2)
                blitClipped(
                    dst = frame,
                    src = fled,
                    srcWidth = 96,
                    srcHeight = 16,
                    dstX = fledX,
                    dstY = 48,
                    clipMinX = 1,
                    clipMinY = 33,
                    clipMaxX = WIDTH - 2,
                    clipMaxY = 62,
                )
            } else {
                val message = decodeSprite(spriteData, messageOffset, 96, 16)
                drawBottomMessageBox(frame = frame, messageSprite = message, clipToInner = false)
            }
        }

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(enemyPoke) || hasNonBackground(playerPoke),
        )
    }

    private fun renderRadarBattleSwap(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

        val switchText = decodeSprite(spriteData, TEXT_SWITCH, 80, 16)
        val arrowReturn = decodeSprite(spriteData, ARROW_RETURN, 8, 16)
        val arrowUp = decodeSprite(spriteData, ARROW_8_UP, 8, 8)
        val pokeball = decodeSprite(spriteData, POKEBALL_8, 8, 8)

        fillRect(frame, 0, 0, WIDTH - 1, 16, palette[0])
        blit(frame, switchText, 80, 16, 8, 0)
        blit(frame, arrowReturn, 8, 16, 0, 0)
        drawHorizontalLine(frame, y = 16, color = palette[3])

        val selected = state.radarSwapCursor.coerceIn(0, DeviceOffsets.POKEMON_SLOT_COUNT - 1)

        val slotXs = intArrayOf(24, 44, 64)
        for (slot in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
            val x = slotXs[slot]
            blit(frame, pokeball, 8, 8, x, 28)
            if (selected == slot) {
                blit(frame, arrowUp, 8, 8, x, 36 + selectorBounceOffset(animationFrame))
            }
        }

        val selectedRouteSlot =
            DeviceBinary.findRouteSlotForSpecies(
                eeprom,
                DeviceBinary.readCaughtPokemonSpecies(eeprom, selected),
            )

        val selectedName =
            if (selectedRouteSlot != null) {
                decodeSprite(
                    spriteData,
                    ROUTE_POKEMON0_NAME + selectedRouteSlot.coerceIn(0, 2) * ROUTE_POKEMON_NAME_STRIDE,
                    80,
                    16,
                )
            } else {
                decodeSprite(spriteData, TEXT_NO_POKE_HELD, 96, 16)
            }

        drawBottomMessageBox(
            frame = frame,
            messageSprite = selectedName,
            messageWidth = if (selectedName.size == 80 * 16) 80 else 96,
            messageX = if (selectedName.size == 80 * 16) 8 else 0,
            clipToInner = true,
        )

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(switchText) || hasNonBackground(pokeball),
        )
    }

    private fun renderDowsing(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        watts: Int,
        animationFrame: Int,
    ): LcdPreviewFrame {
        if (state.dowsingMode == DowsingMode.Swap) {
            return renderDowsingSwap(eeprom, spriteData, state, animationFrame)
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

            return LcdPreviewFrame(
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

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(bush) || hasNonBackground(routeImage),
        )
    }

    private fun renderDowsingSwap(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
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

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(switchText) || hasNonBackground(itemIcon),
        )
    }

    private fun renderConnect(spriteData: ByteArray): LcdPreviewFrame {
        val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

        val arrowReturn = decodeSprite(spriteData, ARROW_RETURN, 8, 16)
        val title = decodeSprite(spriteData, MENU_CONNECT, 80, 16)
        val icon = decodeSprite(spriteData, ICON_CONNECT, 16, 16)

        blit(frame, arrowReturn, 8, 16, 0, 0)
        blit(frame, title, 80, 16, 8, 0)
        blit(frame, icon, 16, 16, 40, 24)

        drawMessageBoxBorder(frame)

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(title) || hasNonBackground(icon),
        )
    }

    private fun renderCard(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
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

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(title) || hasNonBackground(trainerNameImage),
        )
    }

    private fun renderPokemon(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

        val arrowReturn = decodeSprite(spriteData, ARROW_RETURN, 8, 16)
        val arrowDown = decodeSprite(spriteData, ARROW_8_DOWN, 8, 8)
        val menuPkmn = decodeSprite(spriteData, MENU_PKMN, 80, 16)
        val pokeball = decodeSprite(spriteData, POKEBALL_8, 8, 8)
        val pokeballLight = decodeSprite(spriteData, POKEBALL_8_LIGHT, 8, 8)
        val item = decodeSprite(spriteData, ITEM_8, 8, 8)
        val chestLarge = decodeSprite(spriteData, CHEST_LARGE, 32, 24)
        val noPokemonText = decodeSprite(spriteData, TEXT_NO_POKE_HELD, 96, 16)

        data class PokemonEntry(
            val type: Int,
            val value: Int?,
        )

        val entries = mutableListOf<PokemonEntry>()

        val walkingSpecies = DeviceBinary.readWalkingPokemonSpecies(eeprom)
        if (walkingSpecies != 0) {
            entries += PokemonEntry(type = 0, value = null)
        }

        for (index in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
            val species = DeviceBinary.readCaughtPokemonSpecies(eeprom, index)
            if (species != 0) {
                val routeSlot = DeviceBinary.findRouteSlotForSpecies(eeprom, species) ?: index
                entries += PokemonEntry(type = 1, value = routeSlot.coerceIn(0, DeviceOffsets.POKEMON_SLOT_COUNT - 1))
            }
        }

        for (dowsedIndex in 0 until DeviceOffsets.DOWSED_ITEM_COUNT) {
            val itemId = DeviceBinary.readDowsedItemId(eeprom, dowsedIndex)
            if (itemId != 0) {
                val routeItemIndex = DeviceBinary.findRouteItemIndexForItemId(eeprom, itemId)
                entries += PokemonEntry(type = 2, value = routeItemIndex)
            }
        }

        val realEntries = entries.size
        val virtualEntries = realEntries.coerceAtLeast(1)
        val selectedIndex = wrapIndex(state.pokemonIndex, virtualEntries)

        blit(frame, arrowReturn, 8, 16, 0, 0)
        blit(frame, menuPkmn, 80, 16, 8, 0)

        if (realEntries == 0) {
            blit(frame, pokeballLight, 8, 8, 44, 24)
            blit(frame, arrowDown, 8, 8, 44, 16)
        }

        val pokemonRowY = 23
        val itemRowY = 34
        val arrowBob = selectorBounceOffset(animationFrame)
        var pokemonVisualIndex = 0
        var itemVisualIndex = 0
        entries.forEachIndexed { index, entry ->
            if (entry.type == 2) {
                val x = 20 + itemVisualIndex * 14
                blit(frame, item, 8, 8, x, itemRowY)
                if (selectedIndex == index) {
                    blit(frame, arrowDown, 8, 8, x, itemRowY - 8 + arrowBob)
                }
                itemVisualIndex += 1
            } else {
                val x = 8 + pokemonVisualIndex * 14
                val icon = if (entry.type == 0) pokeballLight else pokeball
                blit(frame, icon, 8, 8, x, pokemonRowY)
                if (selectedIndex == index) {
                    blit(frame, arrowDown, 8, 8, x, pokemonRowY - 8 + arrowBob)
                }
                pokemonVisualIndex += 1
            }
        }

        var messageSprite = noPokemonText
        var messageWidth = 96
        var messageX = 0
        var visualSprite = menuPkmn

        if (realEntries > 0) {
            val selectedEntry = entries[selectedIndex]
            when (selectedEntry.type) {
                0 -> {
                    val walkingPokemon = decodeAnimatedSprite(spriteData, SMALL_POKEMON, 32, 24, animationFrame)
                    val walkingName = decodeSprite(spriteData, WALKING_POKEMON_NAME, 80, 16)
                    blit(frame, walkingPokemon, 32, 24, 64, 24)
                    messageSprite = walkingName
                    messageWidth = 80
                    messageX = 0
                    visualSprite = walkingPokemon
                }

                1 -> {
                    val routeSlot = selectedEntry.value ?: 0
                    val pokemon =
                        decodeAnimatedSprite(
                            spriteData,
                            ROUTE_POKEMON_SPRITES + routeSlot * 0x180,
                            32,
                            24,
                            animationFrame,
                        )
                    val pokemonName =
                        decodeSprite(
                            spriteData,
                            ROUTE_POKEMON0_NAME + routeSlot * ROUTE_POKEMON_NAME_STRIDE,
                            80,
                            16,
                        )
                    blit(frame, pokemon, 32, 24, 64, 24)
                    messageSprite = pokemonName
                    messageWidth = 80
                    messageX = 0
                    visualSprite = pokemon
                }

                else -> {
                    val routeItemIndex = selectedEntry.value
                    val nameOffset = routeItemIndex?.let { ROUTE_ITEM0_NAME + it * ROUTE_ITEM_NAME_STRIDE }
                    blit(frame, chestLarge, 32, 24, 64, 24)
                    blit(frame, item, 8, 8, 68, 40)

                    if (nameOffset != null) {
                        messageSprite = decodeSprite(spriteData, nameOffset, 96, 16)
                        messageWidth = 96
                        messageX = 0
                    } else {
                        messageSprite = decodeSprite(spriteData, TEXT_NOTHING_HELD, 96, 16)
                        messageWidth = 96
                        messageX = 0
                    }
                    visualSprite = chestLarge
                }
            }
        }

        drawBottomMessageBox(
            frame = frame,
            messageSprite = messageSprite,
            messageWidth = messageWidth,
            messageX = messageX,
            clipToInner = true,
        )

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(menuPkmn) || hasNonBackground(visualSprite),
        )
    }

    private fun renderSettings(
        spriteData: ByteArray,
        state: DeviceInteractionState,
    ): LcdPreviewFrame {
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

        return LcdPreviewFrame(
            pixels = frame,
            hasVisualContent = hasNonBackground(menuSettings) || hasNonBackground(settingsSound),
        )
    }

    private fun menuTitleOffset(item: DeviceMenuItem): Int {
        return when (item) {
            DeviceMenuItem.Radar -> MENU_RADAR
            DeviceMenuItem.Dowsing -> MENU_DOWSING
            DeviceMenuItem.Connect -> MENU_CONNECT
            DeviceMenuItem.Card -> MENU_CARD
            DeviceMenuItem.Pokemon -> MENU_PKMN
            DeviceMenuItem.Settings -> MENU_SETTINGS
        }
    }

    private fun drawCostAndCurrentWatts(
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

    private fun drawMessageBoxBorder(frame: IntArray) {
        drawHorizontalLine(frame, y = 48, color = palette[3])
        drawHorizontalLine(frame, y = 63, color = palette[3])
        drawVerticalLine(frame, x = 0, color = palette[3], yStart = 48, yEndInclusive = 63)
        drawVerticalLine(frame, x = WIDTH - 1, color = palette[3], yStart = 48, yEndInclusive = 63)
    }

    private fun drawLargeMessageBoxBorder(frame: IntArray) {
        drawHorizontalLine(frame, y = 24, color = palette[3])
        drawHorizontalLine(frame, y = 63, color = palette[3])
        drawVerticalLine(frame, x = 0, color = palette[3], yStart = 24, yEndInclusive = 63)
        drawVerticalLine(frame, x = WIDTH - 1, color = palette[3], yStart = 24, yEndInclusive = 63)
    }

    private fun drawBattleMessageBoxBorder(frame: IntArray) {
        drawHorizontalLine(frame, y = 32, color = palette[3])
        drawHorizontalLine(frame, y = 63, color = palette[3])
        drawVerticalLine(frame, x = 0, color = palette[3], yStart = 32, yEndInclusive = 63)
        drawVerticalLine(frame, x = WIDTH - 1, color = palette[3], yStart = 32, yEndInclusive = 63)
    }

    private fun drawLargeMessageLine(
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

    private fun centeredSpriteDstX(
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

    private fun leftAlignedSpriteDstX(
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

    private fun opaqueSpriteBounds(
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

    private fun stableOpaqueSpriteBounds(
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

    private fun selectorBounceOffset(animationFrame: Int): Int {
        return if (animationFrame % 2 == 0) 0 else 1
    }

    private fun drawBottomMessageBox(
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

    private fun fillRect(
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

    private fun dimSprite(src: IntArray): IntArray {
        return IntArray(src.size) { index ->
            when (src[index]) {
                palette[3] -> palette[2]
                palette[2] -> palette[1]
                palette[1] -> palette[0]
                else -> palette[0]
            }
        }
    }

    private fun drawNumberRight(
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

    private fun drawFixed4Digits(
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

    private fun decodeAnimatedSprite(
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

    private fun decodeSprite(
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

    private fun selectSpriteSource(offset: Int): ByteArray {
        return currentSpriteData
    }

    private fun readUnsigned(
        data: ByteArray,
        index: Int,
    ): Int {
        if (index < 0 || index >= data.size) {
            return 0
        }
        return data[index].toInt() and 0xFF
    }

    private fun blit(
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

    private fun blitClipped(
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

    private fun drawHorizontalLine(
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

    private fun drawHorizontalLineSegment(
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

    private fun drawBattleSplit(frame: IntArray) {
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

    private fun drawRadarHpBlocks(
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

    private fun drawTinyWord(
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

    private fun drawTinyChar(
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

    private fun drawVerticalLine(
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

    private fun drawHpBlocks(
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

    private fun hasNonBackground(pixels: IntArray): Boolean {
        return pixels.any { pixel -> pixel != palette[0] }
    }

    private fun wrapIndex(
        value: Int,
        size: Int,
    ): Int {
        if (size <= 0) return 0
        val mod = value % size
        return if (mod < 0) mod + size else mod
    }
}
