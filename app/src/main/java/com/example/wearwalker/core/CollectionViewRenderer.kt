package com.example.wearwalker.core

internal object CollectionViewRenderer {
    internal fun render(
        eeprom: ByteArray,
        spriteData: ByteArray,
        state: DeviceInteractionState,
        animationFrame: Int,
    ): LcdPreviewFrame {
        return with(DeviceLcdRenderer) {
            val frame = IntArray(WIDTH * HEIGHT) { palette[0] }

            val arrowReturn = decodeSprite(spriteData, ARROW_RETURN, 8, 16)
            val arrowDown = decodeSprite(spriteData, ARROW_8_DOWN, 8, 8)
            val menuPkmn = decodeSprite(spriteData, MENU_PKMN, 80, 16)
            val pokeball = decodeSprite(spriteData, POKEBALL_8, 8, 8)
            val pokeballLight = decodeSprite(spriteData, POKEBALL_8_LIGHT, 8, 8)
            val item = decodeSprite(spriteData, ITEM_8, 8, 8)
            val chestLarge = decodeSprite(spriteData, CHEST_LARGE, 32, 24)
            val noPokemonText = decodeSprite(spriteData, TEXT_NO_POKE_HELD, 96, 16)

            data class CollectionEntry(
                val type: Int,
                val value: Int?,
            )

            val entries = mutableListOf<CollectionEntry>()

            val walkingSpecies = DeviceBinary.readWalkingPokemonSpecies(eeprom)
            if (walkingSpecies != 0) {
                entries += CollectionEntry(type = 0, value = null)
            }

            for (index in 0 until DeviceOffsets.POKEMON_SLOT_COUNT) {
                val species = DeviceBinary.readCaughtPokemonSpecies(eeprom, index)
                if (species != 0) {
                    val routeSlot = DeviceBinary.findRouteSlotForSpecies(eeprom, species) ?: index
                    entries += CollectionEntry(type = 1, value = routeSlot.coerceIn(0, DeviceOffsets.POKEMON_SLOT_COUNT - 1))
                }
            }

            for (dowsedIndex in 0 until DeviceOffsets.DOWSED_ITEM_COUNT) {
                val itemId = DeviceBinary.readDowsedItemId(eeprom, dowsedIndex)
                if (itemId != 0) {
                    val routeItemIndex = DeviceBinary.findRouteItemIndexForItemId(eeprom, itemId)
                    entries += CollectionEntry(type = 2, value = routeItemIndex)
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

            LcdPreviewFrame(
                pixels = frame,
                hasVisualContent = hasNonBackground(menuPkmn) || hasNonBackground(visualSprite),
            )
        }
    }
}
