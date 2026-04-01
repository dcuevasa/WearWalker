package com.example.wearwalker.core

internal object RadarViewRenderer {
    fun renderScan(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame =
        with(DeviceLcdRenderer) {
            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val radarBush = decodeSprite(spriteData, RADAR_BUSH, 32, 24)
            val arrowRight = decodeSprite(spriteData, ARROW_8_RIGHT, 8, 8)
            val exclamation0 = decodeSprite(spriteData, RADAR_EXCL0, 16, 16)
            val exclamation1 = decodeSprite(spriteData, RADAR_EXCL1, 16, 16)
            val exclamation2 = decodeSprite(spriteData, RADAR_EXCL2, 16, 16)
            val click = decodeSprite(spriteData, RADAR_CLICK, 16, 16)
            val findText = decodeSprite(spriteData, TEXT_FIND_POKE, 96, 16)
            val foundSomethingText = decodeSprite(spriteData, TEXT_FOUND_SOMETHING, 96, 16)
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

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(radarBush),
            )
        }

    fun renderBattle(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame =
        with(DeviceLcdRenderer) {
            if (state.radarMode == RadarMode.BattleSwap) {
                return@with renderSwap(eeprom, spriteData, state, animationFrame)
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
                val markerBall = decodeSprite(spriteData, POKEBALL_8, 8, 8)
                blit(frame, markerBall, 8, 8, ballX, ballY)
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
                    val encounterName =
                        decodeSprite(
                            spriteData,
                            ROUTE_POKEMON0_NAME + routeSlot * ROUTE_POKEMON_NAME_STRIDE,
                            80,
                            16,
                        )
                    fillRect(frame, 0, 32, WIDTH - 1, HEIGHT - 1, palette[0])
                    drawBattleMessageBoxBorder(frame)

                    val nameX = leftAlignedSpriteDstX(encounterName, 80, 16, innerLeft = 1, innerRight = 87)
                    blitClipped(
                        dst = frame,
                        src = encounterName,
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
                    val encounterName =
                        decodeSprite(
                            spriteData,
                            ROUTE_POKEMON0_NAME + routeSlot * ROUTE_POKEMON_NAME_STRIDE,
                            80,
                            16,
                        )
                    drawBottomMessageBox(
                        frame = frame,
                        messageSprite = encounterName,
                        messageWidth = 80,
                        messageX = 8,
                        clipToInner = false,
                    )
                } else if (
                    messageOffset == TEXT_ATTACKED &&
                        state.radarBattleAction == RadarBattleAction.Attack
                ) {
                    val attacked = decodeSprite(spriteData, TEXT_ATTACKED, 96, 16)
                    val playerName =
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

                    val nameSprite = if (state.radarBattleMessageUsesEnemyName) enemyName else playerName

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
                    val encounterName =
                        decodeSprite(
                            spriteData,
                            ROUTE_POKEMON0_NAME + routeSlot * ROUTE_POKEMON_NAME_STRIDE,
                            80,
                            16,
                        )

                    fillRect(frame, 0, 32, WIDTH - 1, HEIGHT - 1, palette[0])
                    drawBattleMessageBoxBorder(frame)

                    val nameX = leftAlignedSpriteDstX(encounterName, 80, 16, innerLeft = 1, innerRight = 87)
                    blitClipped(
                        dst = frame,
                        src = encounterName,
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

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(enemyPoke) || hasNonBackground(playerPoke),
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
            val markerBall = decodeSprite(spriteData, POKEBALL_8, 8, 8)

            fillRect(frame, 0, 0, WIDTH - 1, 16, palette[0])
            blit(frame, switchText, 80, 16, 8, 0)
            blit(frame, arrowReturn, 8, 16, 0, 0)
            drawHorizontalLine(frame, y = 16, color = palette[3])

            val selected = state.radarSwapCursor.coerceIn(0, DeviceOffsets.POKEMON_SLOT_COUNT - 1)

            val slotXs = intArrayOf(24, 44, 64)
            for (slot in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
                val x = slotXs[slot]
                blit(frame, markerBall, 8, 8, x, 28)
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

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(switchText) || hasNonBackground(markerBall),
            )
        }
}
