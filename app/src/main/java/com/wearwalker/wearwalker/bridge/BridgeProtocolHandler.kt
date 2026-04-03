package com.wearwalker.wearwalker.bridge

import com.wearwalker.wearwalker.core.DeviceBinary
import com.wearwalker.wearwalker.core.DeviceEngine
import com.wearwalker.wearwalker.core.DeviceOffsets
import com.wearwalker.wearwalker.data.EepromStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.math.min

private class BridgeProtocolException(
    val statusCode: Int,
    val code: String,
    override val message: String,
) : RuntimeException(message)

private data class SpritePatchRegion(
    val offset: Int,
    val size: Int,
)

class BridgeProtocolHandler(
    private val eepromStorage: EepromStorage,
    private val getEngine: () -> DeviceEngine?,
    private val onStateRefreshed: (String) -> Unit,
) {
    companion object {
        private const val TEAM_TRAINER_TID_OFFSET = DeviceOffsets.TEAM_OFFSET + 48
        private const val TEAM_TRAINER_SID_OFFSET = DeviceOffsets.TEAM_OFFSET + 50
        private const val TEAM_TRAINER_NAME_OFFSET = DeviceOffsets.TEAM_OFFSET + 56
        private const val TEAM_TRAINER_NAME_CHARS = 8
        private const val TEAM_COMPANION_OFFSET = DeviceOffsets.TEAM_OFFSET + 96
        private const val TEAM_COMPANION_SIZE = 56
        private const val TEAM_COMPANION_COUNT = 6
        private const val TEAM_COMPANION_SPECIES_OFFSET = 0

        private const val ROUTE_WALKING_SUMMARY_OFFSET = DeviceOffsets.ROUTE_INFO_OFFSET
        private const val ROUTE_NICKNAME_OFFSET = DeviceOffsets.ROUTE_INFO_OFFSET + 16
        private const val ROUTE_NICKNAME_CHARS = 11
        private const val ROUTE_IMAGE_INDEX_OFFSET = DeviceOffsets.ROUTE_INFO_OFFSET + 39

        private const val ROUTE_COMPANION_MIN_STEPS_OFFSET = DeviceOffsets.ROUTE_INFO_OFFSET + 0x82
        private const val ROUTE_COMPANION_CHANCE_OFFSET = DeviceOffsets.ROUTE_INFO_OFFSET + 0x88
        private const val ROUTE_ITEM_MIN_STEPS_OFFSET = DeviceOffsets.ROUTE_INFO_OFFSET + 0xA0
        private const val ROUTE_ITEM_CHANCE_OFFSET = DeviceOffsets.ROUTE_INFO_OFFSET + 0xB4

        private val SPRITE_PATCH_LAYOUT =
            mapOf(
                "trainerCardName" to SpritePatchRegion(offset = 0x1250, size = 0x140),
                "areaSprite" to SpritePatchRegion(offset = DeviceOffsets.AREA_SPRITE_OFFSET, size = 0xC0),
                "areaNameSprite" to SpritePatchRegion(offset = DeviceOffsets.AREA_NAME_SPRITE_OFFSET, size = 0x140),
                "walkPokeSmall0" to
                    SpritePatchRegion(offset = DeviceOffsets.WALK_COMPANION_SMALL_ANIM_OFFSET, size = 0xC0),
                "walkPokeSmall1" to
                    SpritePatchRegion(offset = DeviceOffsets.WALK_COMPANION_SMALL_ANIM_OFFSET + 0xC0, size = 0xC0),
                "walkPokeLarge0" to
                    SpritePatchRegion(offset = DeviceOffsets.WALK_COMPANION_BIG_ANIM_OFFSET, size = 0x300),
                "walkPokeLarge1" to
                    SpritePatchRegion(offset = DeviceOffsets.WALK_COMPANION_BIG_ANIM_OFFSET + 0x300, size = 0x300),
                "joinPokeLarge0" to SpritePatchRegion(offset = 0x9EFE, size = 0x300),
                "joinPokeLarge1" to SpritePatchRegion(offset = 0xA1FE, size = 0x300),
                "walkPokeName" to
                    SpritePatchRegion(offset = DeviceOffsets.WALK_COMPANION_NAME_SPRITE_OFFSET, size = 0x140),
                "routePoke0Small0" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_COMPANION_SPRITES_OFFSET, size = 0xC0),
                "routePoke0Small1" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_COMPANION_SPRITES_OFFSET + 0xC0, size = 0xC0),
                "routePoke1Small0" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_COMPANION_SPRITES_OFFSET + 0x180, size = 0xC0),
                "routePoke1Small1" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_COMPANION_SPRITES_OFFSET + 0x240, size = 0xC0),
                "routePoke2Small0" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_COMPANION_SPRITES_OFFSET + 0x300, size = 0xC0),
                "routePoke2Small1" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_COMPANION_SPRITES_OFFSET + 0x3C0, size = 0xC0),
                "routePoke0Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_COMPANION_NAME_SPRITES_OFFSET, size = 0x140),
                "routePoke1Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_COMPANION_NAME_SPRITES_OFFSET + 0x140, size = 0x140),
                "routePoke2Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_COMPANION_NAME_SPRITES_OFFSET + 0x280, size = 0x140),
                "routeItem0Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0x000, size = 0x140),
                "routeItem1Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0x180, size = 0x140),
                "routeItem2Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0x300, size = 0x140),
                "routeItem3Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0x480, size = 0x140),
                "routeItem4Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0x600, size = 0x140),
                "routeItem5Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0x780, size = 0x140),
                "routeItem6Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0x900, size = 0x140),
                "routeItem7Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0xA80, size = 0x140),
                "routeItem8Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0xC00, size = 0x140),
                "routeItem9Name" to
                    SpritePatchRegion(offset = DeviceOffsets.ROUTE_ITEM_NAME_SPRITES_OFFSET + 0xD80, size = 0x140),
            )
    }

    private val mutationMutex = Mutex()

    suspend fun handle(request: BridgeHttpRequest): BridgeHttpResponse {
        val method = request.method.uppercase()
        val path = request.path.substringBefore('?')

        return try {
            when {
                method == "GET" && (path == "/api/v1/status" || path == "/api/v1/bridge/status") -> handleStatus()
                method == "GET" && (path == "/api/v1/snapshot" || path == "/api/v1/device/snapshot") -> handleSnapshot()
                method == "GET" && path == "/api/v1/sync/package" -> handleSyncPackage()
                method == "POST" &&
                    (path == "/api/v1/commands/set-trainer" || path == "/api/v1/device/commands/set-trainer") ->
                    handleSetTrainer(request)
                method == "POST" &&
                    (path == "/api/v1/commands/set-watts" || path == "/api/v1/device/commands/set-watts") ->
                    handleSetWatts(request)
                method == "POST" &&
                    (path == "/api/v1/commands/set-steps" || path == "/api/v1/device/commands/set-steps") ->
                    handleSetSteps(request)
                method == "POST" &&
                    (path == "/api/v1/commands/set-sync" || path == "/api/v1/device/commands/set-sync") ->
                    handleSetSync(request)
                method == "PATCH" &&
                    (path == "/api/v1/patch/identity" || path == "/api/v1/device/identity") ->
                    handlePatchIdentity(request)
                method == "POST" && path == "/api/v1/stroll/send" -> handleStrollSend(request)
                method == "POST" && path == "/api/v1/stroll/return" -> handleStrollReturn(request)
                method == "POST" && path == "/api/v2/stroll/sprite-patches" -> handleSpritePatches(request)
                method == "GET" && path == "/api/v1/eeprom/export" -> handleEepromExport()
                method == "PUT" && path == "/api/v1/eeprom/import" -> handleEepromImport(request)
                else -> errorResponse(404, "not_found", "Unknown endpoint: $method $path")
            }
        } catch (error: BridgeProtocolException) {
            errorResponse(error.statusCode, error.code, error.message)
        } catch (error: Exception) {
            errorResponse(500, "internal_error", error.message ?: "Internal server error")
        }
    }

    private fun handleStatus(): BridgeHttpResponse {
        val info = eepromStorage.getFileInfo()
        val engine = getEngine()

        val payload =
            JSONObject()
                .put("status", "ok")
                .put("apiVersion", "v1")
                .put("backend", "wearos-embedded")
                .put("connected", engine != null)
                .put("eepromPath", info.absolutePath)
                .put("eepromExists", info.exists)
                .put("eepromSize", info.sizeBytes)

        if (engine != null) {
            val snapshot = snapshotJson(engine.exportEeprom())
            payload.put("trainerName", snapshot.optString("trainerName"))
            payload.put("steps", snapshot.optInt("steps"))
            payload.put("watts", snapshot.optInt("watts"))
        }

        return okJson(payload)
    }

    private suspend fun handleSnapshot(): BridgeHttpResponse {
        val snapshot = mutationMutex.withLock {
            val engine = requireEngine()
            snapshotJson(engine.exportEeprom())
        }
        return okJson(snapshotEnvelope(snapshot))
    }

    private suspend fun handleSyncPackage(): BridgeHttpResponse {
        val payload =
            mutationMutex.withLock {
                val engine = requireEngine()
                val eeprom = engine.exportEeprom()

                JSONObject()
                    .put("status", "ok")
                    .put("schema", "wearwalker-sync-v1")
                    .put("generatedAtEpochSeconds", Instant.now().epochSecond)
                    .put("snapshot", snapshotJson(eeprom))
                    .put("domains", buildSyncDomains(eeprom))
            }

        return okJson(payload)
    }

    private suspend fun handleSetTrainer(request: BridgeHttpRequest): BridgeHttpResponse {
        val body = parseJson(request)
        val name = body.optString("name", "").trim()
        if (name.isEmpty()) {
            throw BridgeProtocolException(400, "invalid_name", "Field 'name' is required")
        }

        val snapshot =
            mutateEeprom("Trainer name updated via bridge") { eeprom ->
                writeDeviceTextFixed(
                    eeprom = eeprom,
                    offset = DeviceOffsets.IDENTITY_OWNER_NAME_OFFSET,
                    maxChars = 8,
                    text = name,
                )
                writeDeviceTextFixed(
                    eeprom = eeprom,
                    offset = TEAM_TRAINER_NAME_OFFSET,
                    maxChars = TEAM_TRAINER_NAME_CHARS,
                    text = name,
                )
            }

        return okJson(snapshotEnvelope(snapshot))
    }

    private suspend fun handleSetWatts(request: BridgeHttpRequest): BridgeHttpResponse {
        val body = parseJson(request)
        val watts = body.requireInt("watts", min = 0, max = 0xFFFF)

        val snapshot =
            mutateEeprom("Watts updated via bridge") { eeprom ->
                DeviceBinary.writeCurrentWatts(eeprom, watts)
            }

        return okJson(snapshotEnvelope(snapshot))
    }

    private suspend fun handleSetSteps(request: BridgeHttpRequest): BridgeHttpResponse {
        val body = parseJson(request)
        val steps = body.requireLong("steps", min = 0L, max = 0xFFFF_FFFFL)

        val snapshot =
            mutateEeprom("Steps updated via bridge") { eeprom ->
                writeStepCounters(eeprom, steps)
            }

        return okJson(snapshotEnvelope(snapshot))
    }

    private suspend fun handleSetSync(request: BridgeHttpRequest): BridgeHttpResponse {
        val body = parseJson(request)
        val epochSeconds =
            when {
                body.has("epochSeconds") -> body.requireLong("epochSeconds", min = 0L, max = 0xFFFF_FFFFL)
                body.has("epoch") -> body.requireLong("epoch", min = 0L, max = 0xFFFF_FFFFL)
                else -> throw BridgeProtocolException(400, "missing_field", "Field 'epochSeconds' is required")
            }

        val snapshot =
            mutateEeprom("Last sync timestamp updated via bridge") { eeprom ->
                writeLastSyncAcrossBlocks(eeprom, epochSeconds)
            }

        return okJson(snapshotEnvelope(snapshot))
    }

    private suspend fun handlePatchIdentity(request: BridgeHttpRequest): BridgeHttpResponse {
        val body = parseJson(request)

        val trainerName =
            when {
                body.has("trainerName") -> body.optString("trainerName", "").trim()
                body.has("trainer") -> body.optString("trainer", "").trim()
                else -> ""
            }

        val hasTid = body.has("trainerTid")
        val hasSid = body.has("trainerSid")
        val hasProtocolVersion = body.has("protocolVersion")
        val hasProtocolSubVersion = body.has("protocolSubVersion")
        val hasSync = body.has("lastSyncEpochSeconds")
        val hasSyncLegacy = body.has("epoch")
        val hasSteps = body.has("stepCount")

        val hasAnyField =
            trainerName.isNotEmpty() ||
                hasTid ||
                hasSid ||
                hasProtocolVersion ||
                hasProtocolSubVersion ||
                hasSync ||
                hasSyncLegacy ||
                hasSteps

        if (!hasAnyField) {
            throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_identity_patch",
                message = "At least one identity field is required",
            )
        }

        val snapshot =
            mutateEeprom("Identity patched via bridge") { eeprom ->
                if (trainerName.isNotEmpty()) {
                    writeDeviceTextFixed(
                        eeprom = eeprom,
                        offset = DeviceOffsets.IDENTITY_OWNER_NAME_OFFSET,
                        maxChars = 8,
                        text = trainerName,
                    )
                    writeDeviceTextFixed(
                        eeprom = eeprom,
                        offset = TEAM_TRAINER_NAME_OFFSET,
                        maxChars = TEAM_TRAINER_NAME_CHARS,
                        text = trainerName,
                    )
                }

                if (hasTid) {
                    val tid = body.requireInt("trainerTid", min = 0, max = 0xFFFF)
                    DeviceBinary.writeU16BE(eeprom, DeviceOffsets.IDENTITY_OFFSET + 12, tid)
                    DeviceBinary.writeU16LE(eeprom, TEAM_TRAINER_TID_OFFSET, tid)
                }

                if (hasSid) {
                    val sid = body.requireInt("trainerSid", min = 0, max = 0xFFFF)
                    DeviceBinary.writeU16BE(eeprom, DeviceOffsets.IDENTITY_OFFSET + 14, sid)
                    DeviceBinary.writeU16LE(eeprom, TEAM_TRAINER_SID_OFFSET, sid)
                }

                if (hasProtocolVersion) {
                    val value = body.requireInt("protocolVersion", min = 0, max = 0xFF)
                    eeprom[DeviceOffsets.IDENTITY_PROTOCOL_VERSION_OFFSET] = value.toByte()
                }

                if (hasProtocolSubVersion) {
                    val value = body.requireInt("protocolSubVersion", min = 0, max = 0xFF)
                    eeprom[DeviceOffsets.IDENTITY_PROTOCOL_SUB_VERSION_OFFSET] = value.toByte()
                }

                if (hasSync || hasSyncLegacy) {
                    val epochSeconds =
                        if (hasSync) {
                            body.requireLong("lastSyncEpochSeconds", min = 0L, max = 0xFFFF_FFFFL)
                        } else {
                            body.requireLong("epoch", min = 0L, max = 0xFFFF_FFFFL)
                        }
                    writeLastSyncAcrossBlocks(eeprom, epochSeconds)
                }

                if (hasSteps) {
                    val stepCount = body.requireLong("stepCount", min = 0L, max = 0xFFFF_FFFFL)
                    writeStepCounters(eeprom, stepCount)
                }
            }

        return okJson(snapshotEnvelope(snapshot))
    }

    private suspend fun handleStrollSend(request: BridgeHttpRequest): BridgeHttpResponse {
        val body = parseJson(request)
        val resolvedRouteConfig = body.optJSONObject("resolvedRouteConfig")
        val usedResolvedRouteConfig = resolvedRouteConfig != null

        val snapshot =
            mutateEeprom("Stroll data imported via bridge") { eeprom ->
                if (resolvedRouteConfig != null) {
                    applyResolvedRouteConfig(eeprom, resolvedRouteConfig)
                } else {
                    val courseId = body.optInt("courseId", 0).coerceIn(0, 0xFF)
                    eeprom[ROUTE_IMAGE_INDEX_OFFSET] = courseId.toByte()
                }

                val walkingSource =
                    if (resolvedRouteConfig != null) {
                        buildWalkingSummarySource(body, resolvedRouteConfig)
                    } else {
                        buildLegacyWalkingSummarySource(body)
                    }
                writeCompanionSummary(
                    eeprom = eeprom,
                    offset = ROUTE_WALKING_SUMMARY_OFFSET,
                    payload = walkingSource,
                    allowSpeciesZero = false,
                )

                val friendship = body.optInt("friendship", 70).coerceIn(0, 0xFF)
                DeviceBinary.writeWalkingCompanionFriendship(eeprom, friendship)

                val nickname = body.optString("nickname", "").trim()
                writeDeviceTextFixed(
                    eeprom = eeprom,
                    offset = ROUTE_NICKNAME_OFFSET,
                    maxChars = ROUTE_NICKNAME_CHARS,
                    text = nickname,
                )

                if (body.optBoolean("clearBuffers", false)) {
                    clearCaughtCompanion(eeprom)
                    clearDowsedItems(eeprom)
                }

                writeLastSyncAcrossBlocks(eeprom, Instant.now().epochSecond)
            }

        val response =
            snapshotEnvelope(snapshot)
                .put("resolvedRouteSource", if (usedResolvedRouteConfig) "wearos-local" else "legacy-course-id")
                .put("applied", true)

        return okJson(response)
    }

    private suspend fun handleStrollReturn(request: BridgeHttpRequest): BridgeHttpResponse {
        val body = parseJson(request)
        val walkedSteps = body.requireLong("walkedSteps", min = 0L, max = 0xFFFF_FFFFL)
        val bonusWatts = body.optLong("bonusWatts", 0L).coerceIn(0L, 0xFFFFL).toInt()
        val autoCaptures = body.optInt("autoCaptures", 0).coerceAtLeast(0)
        val replaceWhenFull = body.optBoolean("replaceWhenFull", false)
        val clearCaughtAfterReturn = body.optBoolean("clearCaughtAfterReturn", false)

        val captureSpeciesIds =
            body.optJSONArray("captureSpeciesIds")
                ?.let { toSpeciesList(it) }
                ?: emptyList()

        val resultPayload =
            mutationMutex.withLock {
                val engine = requireEngine()
                val eeprom = engine.exportEeprom()

                val walkingSpecies = DeviceBinary.readWalkingCompanionSpecies(eeprom)
                if (walkingSpecies == 0) {
                    throw BridgeProtocolException(
                        statusCode = 400,
                        code = "invalid_stroll_return",
                        message = "No active walking companion to return",
                    )
                }

                val currentSteps = DeviceBinary.readU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET)
                val nextSteps = (currentSteps + walkedSteps).coerceAtMost(0xFFFF_FFFFL)
                writeStepCounters(eeprom, nextSteps)

                val wattsFromSteps = (walkedSteps / 20L).coerceAtMost(0xFFFFL).toInt()
                val currentWatts = DeviceBinary.readCurrentWatts(eeprom)
                val nextWatts = min(0xFFFF, currentWatts + wattsFromSteps + bonusWatts)
                DeviceBinary.writeCurrentWatts(eeprom, nextWatts)

                val allCaptures = mutableListOf<Int>()
                allCaptures.addAll(captureSpeciesIds)
                repeat(autoCaptures) { index ->
                    val routeSlot = index % DeviceOffsets.COMPANION_SLOT_COUNT
                    val species = DeviceBinary.readRouteCompanionSpecies(eeprom, routeSlot)
                    if (species > 0) {
                        allCaptures.add(species)
                    }
                }

                var appliedCaptures = 0
                var droppedCaptures = 0
                allCaptures.forEachIndexed { index, speciesId ->
                    val routeSlot = DeviceBinary.findRouteSlotForSpecies(eeprom, speciesId) ?: (index % DeviceOffsets.COMPANION_SLOT_COUNT)
                    val caught = captureCompanion(eeprom, routeSlot, replaceWhenFull, index)
                    if (caught) {
                        appliedCaptures += 1
                    } else {
                        droppedCaptures += 1
                    }
                }

                clearWalkingCompanion(eeprom)

                if (clearCaughtAfterReturn) {
                    clearCaughtCompanion(eeprom)
                }

                clearMatchingTeamCompanion(eeprom, walkingSpecies)
                writeLastSyncAcrossBlocks(eeprom, Instant.now().epochSecond)
                syncGeneralMirror(eeprom)

                engine.replaceEeprom(eeprom)
                eepromStorage.save(eeprom)
                onStateRefreshed("Stroll return processed via bridge")

                JSONObject()
                    .put("status", "ok")
                    .put("capturesApplied", appliedCaptures)
                    .put("capturesDropped", droppedCaptures)
                    .put("snapshot", snapshotJson(eeprom))
            }

        val snapshot = resultPayload.getJSONObject("snapshot")
        val response = snapshotEnvelope(snapshot)
        response.put("capturesApplied", resultPayload.getInt("capturesApplied"))
        response.put("capturesDropped", resultPayload.getInt("capturesDropped"))
        return okJson(response)
    }

    private suspend fun handleSpritePatches(request: BridgeHttpRequest): BridgeHttpResponse {
        val body = parseJson(request)
        val patches = body.optJSONArray("patches")
            ?: throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_sprite_patches",
                message = "Field 'patches' must be an array",
            )

        val snapshot =
            mutateEeprom("Sprite patches applied via bridge") { eeprom ->
                for (index in 0 until patches.length()) {
                    val patch = patches.optJSONObject(index)
                        ?: throw BridgeProtocolException(
                            statusCode = 400,
                            code = "invalid_sprite_patch",
                            message = "Patch at index $index is not an object",
                        )

                    val key = patch.optString("key", "").trim()
                    val dataHex = patch.optString("dataHex", "").trim()
                    val region = SPRITE_PATCH_LAYOUT[key]
                        ?: throw BridgeProtocolException(
                            statusCode = 400,
                            code = "invalid_sprite_patch",
                            message = "Unsupported sprite key: $key",
                        )

                    val patchBytes = decodeHex(dataHex)
                    if (patchBytes.size != region.size) {
                        throw BridgeProtocolException(
                            statusCode = 400,
                            code = "invalid_sprite_patch",
                            message = "Patch '$key' must be exactly ${region.size} bytes",
                        )
                    }

                    System.arraycopy(patchBytes, 0, eeprom, region.offset, patchBytes.size)
                }
            }

        val response = snapshotEnvelope(snapshot)
        response.put("patched", patches.length())
        return okJson(response)
    }

    private suspend fun handleEepromExport(): BridgeHttpResponse {
        val eeprom =
            mutationMutex.withLock {
                val engine = requireEngine()
                engine.exportEeprom()
            }

        return BridgeHttpResponse(
            statusCode = 200,
            body = eeprom,
            contentType = "application/octet-stream",
            headers = mapOf("Cache-Control" to "no-store"),
        )
    }

    private suspend fun handleEepromImport(request: BridgeHttpRequest): BridgeHttpResponse {
        val contentType = request.headers["content-type"]?.lowercase() ?: ""
        if (!contentType.contains("application/octet-stream")) {
            throw BridgeProtocolException(
                statusCode = 415,
                code = "invalid_content_type",
                message = "Content-Type must be application/octet-stream",
            )
        }

        val lengthHeader = request.headers["content-length"]
            ?: throw BridgeProtocolException(
                statusCode = 411,
                code = "missing_content_length",
                message = "Content-Length header is required",
            )

        val declaredLength = lengthHeader.toIntOrNull()
            ?: throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_content_length",
                message = "Content-Length must be a valid integer",
            )

        if (declaredLength != DeviceOffsets.EEPROM_SIZE) {
            throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_eeprom_size",
                message = "Expected ${DeviceOffsets.EEPROM_SIZE} bytes, got $declaredLength",
            )
        }

        if (request.body.size != DeviceOffsets.EEPROM_SIZE) {
            throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_eeprom_payload",
                message = "EEPROM payload must contain ${DeviceOffsets.EEPROM_SIZE} bytes",
            )
        }

        val snapshot =
            mutationMutex.withLock {
                val engine = requireEngine()
                val eeprom = request.body.copyOf()

                engine.replaceEeprom(eeprom)
                eepromStorage.save(eeprom)
                onStateRefreshed("EEPROM imported via bridge")
                snapshotJson(eeprom)
            }

        return okJson(snapshotEnvelope(snapshot).put("importedBytes", DeviceOffsets.EEPROM_SIZE))
    }

    private suspend fun mutateEeprom(
        statusMessage: String,
        block: (ByteArray) -> Unit,
    ): JSONObject {
        return mutationMutex.withLock {
            val engine = requireEngine()
            val eeprom = engine.exportEeprom()

            block(eeprom)
            syncGeneralMirror(eeprom)

            engine.replaceEeprom(eeprom)
            eepromStorage.save(eeprom)
            onStateRefreshed(statusMessage)
            snapshotJson(eeprom)
        }
    }

    private fun requireEngine(): DeviceEngine {
        return getEngine()
            ?: throw BridgeProtocolException(
                statusCode = 503,
                code = "engine_unavailable",
                message = "EEPROM engine is not ready",
            )
    }

    private fun parseJson(request: BridgeHttpRequest): JSONObject {
        val contentType = request.headers["content-type"]?.lowercase() ?: ""
        if (!contentType.contains("application/json")) {
            throw BridgeProtocolException(
                statusCode = 415,
                code = "invalid_content_type",
                message = "Content-Type must be application/json",
            )
        }

        val text = request.body.toString(StandardCharsets.UTF_8)
        return try {
            JSONObject(text)
        } catch (error: Exception) {
            throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_json",
                message = "Malformed JSON body",
            )
        }
    }

    private fun okJson(payload: JSONObject): BridgeHttpResponse {
        return BridgeHttpResponse(
            statusCode = 200,
            body = payload.toString().toByteArray(StandardCharsets.UTF_8),
            contentType = "application/json",
            headers = mapOf("Cache-Control" to "no-store"),
        )
    }

    private fun errorResponse(
        statusCode: Int,
        code: String,
        message: String,
    ): BridgeHttpResponse {
        val payload =
            JSONObject()
                .put("error", code)
                .put("message", message)

        return BridgeHttpResponse(
            statusCode = statusCode,
            body = payload.toString().toByteArray(StandardCharsets.UTF_8),
            contentType = "application/json",
        )
    }

    private fun snapshotEnvelope(snapshot: JSONObject): JSONObject {
        val payload = JSONObject().put("status", "ok").put("snapshot", snapshot)
        val keys = snapshot.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            payload.put(key, snapshot.opt(key))
        }
        return payload
    }

    private fun snapshotJson(eeprom: ByteArray): JSONObject {
        val trainerName =
            DeviceBinary.readDeviceTextFixed(
                data = eeprom,
                offset = DeviceOffsets.IDENTITY_OWNER_NAME_OFFSET,
                maxChars = 8,
            ).ifBlank { "Unknown" }

        val identitySteps = DeviceBinary.readU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET)
        val todaySteps = DeviceBinary.readU32BE(eeprom, DeviceOffsets.HEALTH_TODAY_STEPS_OFFSET)
        val lifetimeSteps = DeviceBinary.readU32BE(eeprom, DeviceOffsets.HEALTH_LIFETIME_STEPS_OFFSET)
        val stepCount =
            when {
                identitySteps > 0L -> identitySteps
                todaySteps > 0L -> todaySteps
                else -> lifetimeSteps
            }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val protocolVersion = eeprom[DeviceOffsets.IDENTITY_PROTOCOL_VERSION_OFFSET].toInt() and 0xFF
        val protocolSubVersion = eeprom[DeviceOffsets.IDENTITY_PROTOCOL_SUB_VERSION_OFFSET].toInt() and 0xFF

        return JSONObject()
            .put("trainerName", trainerName)
            .put("trainer", trainerName)
            .put("steps", stepCount)
            .put("watts", DeviceBinary.readCurrentWatts(eeprom))
            .put("protocolVersion", protocolVersion)
            .put("protocolSubVersion", protocolSubVersion)
            .put("lastSyncEpochSeconds", DeviceBinary.readLastSyncSeconds(eeprom))
    }

    private fun buildSyncDomains(eeprom: ByteArray): JSONObject {
        val identityDomain =
            JSONObject()
                .put(
                    "trainerName",
                    DeviceBinary.readDeviceTextFixed(
                        data = eeprom,
                        offset = DeviceOffsets.IDENTITY_OWNER_NAME_OFFSET,
                        maxChars = 8,
                    ),
                )
                .put("protocolVersion", eeprom[DeviceOffsets.IDENTITY_PROTOCOL_VERSION_OFFSET].toInt() and 0xFF)
                .put("protocolSubVersion", eeprom[DeviceOffsets.IDENTITY_PROTOCOL_SUB_VERSION_OFFSET].toInt() and 0xFF)
                .put("lastSyncEpochSeconds", DeviceBinary.readU32BE(eeprom, DeviceOffsets.IDENTITY_LAST_SYNC_OFFSET))
                .put("identityStepCount", DeviceBinary.readU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET))

        val statsDomain =
            JSONObject()
                .put("steps", snapshotJson(eeprom).optInt("steps"))
                .put("watts", DeviceBinary.readCurrentWatts(eeprom))
                .put("todaySteps", DeviceBinary.readU32BE(eeprom, DeviceOffsets.HEALTH_TODAY_STEPS_OFFSET))
                .put("lifetimeSteps", DeviceBinary.readU32BE(eeprom, DeviceOffsets.HEALTH_LIFETIME_STEPS_OFFSET))
                .put("lastSyncEpochSeconds", DeviceBinary.readLastSyncSeconds(eeprom))

        val strollDomain =
            JSONObject()
                .put("walkingSpecies", DeviceBinary.readWalkingCompanionSpecies(eeprom))
                .put("friendship", DeviceBinary.readWalkingCompanionFriendship(eeprom))
                .put("routeImageIndex", eeprom[ROUTE_IMAGE_INDEX_OFFSET].toInt() and 0xFF)

        val caught = JSONArray()
        for (slot in 0 until DeviceOffsets.COMPANION_SLOT_COUNT) {
            val base = DeviceOffsets.CAUGHT_COMPANION_OFFSET + slot * DeviceOffsets.COMPANION_SUMMARY_SIZE
            caught.put(
                JSONObject()
                    .put("slot", slot)
                    .put("speciesId", DeviceBinary.readCaughtCompanionSpecies(eeprom, slot))
                    .put("level", eeprom[base + 12].toInt() and 0xFF),
            )
        }

        val dowsed = JSONArray()
        for (index in 0 until DeviceOffsets.DOWSED_ITEM_COUNT) {
            dowsed.put(
                JSONObject()
                    .put("slot", index)
                    .put("itemId", DeviceBinary.readDowsedItemId(eeprom, index)),
            )
        }

        val gifted = JSONArray()
        for (index in 0 until DeviceOffsets.GIFTED_ITEM_COUNT) {
            gifted.put(
                JSONObject()
                    .put("slot", index)
                    .put("itemId", DeviceBinary.readGiftedItemId(eeprom, index)),
            )
        }

        val inventoryDomain =
            JSONObject()
                .put("caught", caught)
                .put("dowsedItems", dowsed)
                .put("giftedItems", gifted)

        return JSONObject()
            .put("identity", identityDomain)
            .put("stats", statsDomain)
            .put("stroll", strollDomain)
            .put("inventory", inventoryDomain)
    }

    private fun writeStepCounters(
        eeprom: ByteArray,
        steps: Long,
    ) {
        val normalized = steps.coerceIn(0L, 0xFFFF_FFFFL)
        DeviceBinary.writeU32BE(eeprom, DeviceOffsets.IDENTITY_STEP_COUNT_OFFSET, normalized)
        DeviceBinary.writeU32BE(eeprom, DeviceOffsets.HEALTH_LIFETIME_STEPS_OFFSET, normalized)
        DeviceBinary.writeU32BE(eeprom, DeviceOffsets.HEALTH_TODAY_STEPS_OFFSET, normalized)
    }

    private fun writeLastSyncAcrossBlocks(
        eeprom: ByteArray,
        epochSeconds: Long,
    ) {
        val value = epochSeconds.coerceIn(0L, 0xFFFF_FFFFL)
        DeviceBinary.writeU32BE(eeprom, DeviceOffsets.IDENTITY_LAST_SYNC_OFFSET, value)
        DeviceBinary.writeU32BE(eeprom, DeviceOffsets.HEALTH_LAST_SYNC_OFFSET, value)
        DeviceBinary.writeU32BE(eeprom, DeviceOffsets.GENERAL_LAST_SYNC_OFFSET, value)
    }

    private fun syncGeneralMirror(eeprom: ByteArray) {
        System.arraycopy(
            eeprom,
            DeviceOffsets.GENERAL_DATA_PRIMARY_OFFSET,
            eeprom,
            DeviceOffsets.GENERAL_DATA_MIRROR_OFFSET,
            DeviceOffsets.GENERAL_DATA_LENGTH,
        )
    }

    private fun applyResolvedRouteConfig(
        eeprom: ByteArray,
        config: JSONObject,
    ) {
        val routeImageIndex = config.optInt("routeImageIndex", config.optInt("courseId", 0)).coerceIn(0, 0xFF)
        eeprom[ROUTE_IMAGE_INDEX_OFFSET] = routeImageIndex.toByte()

        val slots = config.optJSONArray("slots")
            ?: throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_resolved_route_config",
                message = "resolvedRouteConfig.slots is required",
            )

        if (slots.length() != DeviceOffsets.COMPANION_SLOT_COUNT) {
            throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_resolved_route_config",
                message = "resolvedRouteConfig.slots must have exactly ${DeviceOffsets.COMPANION_SLOT_COUNT} entries",
            )
        }

        val seenSlots = BooleanArray(DeviceOffsets.COMPANION_SLOT_COUNT)
        for (index in 0 until slots.length()) {
            val slot = slots.optJSONObject(index)
                ?: throw BridgeProtocolException(
                    statusCode = 400,
                    code = "invalid_resolved_route_config",
                    message = "Slot at index $index is invalid",
                )

            val slotIndex = slot.optInt("slot", -1)
            if (slotIndex !in 0 until DeviceOffsets.COMPANION_SLOT_COUNT || seenSlots[slotIndex]) {
                throw BridgeProtocolException(
                    statusCode = 400,
                    code = "invalid_resolved_route_config",
                    message = "Slot indexes must be unique and between 0 and 2",
                )
            }
            seenSlots[slotIndex] = true

            val routeCompanionOffset =
                DeviceOffsets.ROUTE_COMPANION_LIST_OFFSET + slotIndex * DeviceOffsets.COMPANION_SUMMARY_SIZE
            writeCompanionSummary(
                eeprom = eeprom,
                offset = routeCompanionOffset,
                payload = slot,
                allowSpeciesZero = false,
            )

            val minSteps = slot.optInt("minSteps", 0).coerceIn(0, 0xFFFF)
            val chance = slot.optInt("chance", 0).coerceIn(0, 0xFF)
            DeviceBinary.writeU16LE(eeprom, ROUTE_COMPANION_MIN_STEPS_OFFSET + slotIndex * 2, minSteps)
            eeprom[ROUTE_COMPANION_CHANCE_OFFSET + slotIndex] = chance.toByte()
        }

        val items = config.optJSONArray("items") ?: JSONArray()

        clearRouteItems(eeprom)
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index)
                ?: throw BridgeProtocolException(
                    statusCode = 400,
                    code = "invalid_resolved_route_config",
                    message = "Item at index $index is invalid",
                )

            val routeItemIndex = item.optInt("routeItemIndex", -1)
            if (routeItemIndex !in 0 until DeviceOffsets.ROUTE_ITEM_COUNT) {
                throw BridgeProtocolException(
                    statusCode = 400,
                    code = "invalid_resolved_route_config",
                    message = "routeItemIndex must be between 0 and 9",
                )
            }

            val itemId = item.optInt("itemId", 0).coerceIn(0, 0xFFFF)
            val minSteps = item.optInt("minSteps", 0).coerceIn(0, 0xFFFF)
            val chance = item.optInt("chance", 0).coerceIn(0, 0xFF)

            DeviceBinary.writeU16LE(eeprom, DeviceOffsets.ROUTE_ITEM_LIST_OFFSET + routeItemIndex * 2, itemId)
            DeviceBinary.writeU16LE(eeprom, ROUTE_ITEM_MIN_STEPS_OFFSET + routeItemIndex * 2, minSteps)
            eeprom[ROUTE_ITEM_CHANCE_OFFSET + routeItemIndex] = chance.toByte()
        }
    }

    private fun buildWalkingSummarySource(
        body: JSONObject,
        resolvedRouteConfig: JSONObject,
    ): JSONObject {
        val source = JSONObject()

        if (body.has("speciesId")) {
            source.put("speciesId", body.optInt("speciesId", 0))
        }
        if (body.has("level")) {
            source.put("level", body.optInt("level", 1))
        }
        if (body.has("moves")) {
            source.put("moves", body.optJSONArray("moves"))
        }
        if (body.has("heldItemId")) {
            source.put("heldItemId", body.optInt("heldItemId", 0))
        }
        if (body.has("variantFlags")) {
            source.put("variantFlags", body.optInt("variantFlags", 0))
        }
        if (body.has("specialFlags")) {
            source.put("specialFlags", body.optInt("specialFlags", 0))
        }
        if (body.has("gender")) {
            source.put("gender", body.optInt("gender", 0))
        }

        if (!source.has("speciesId")) {
            val slots = resolvedRouteConfig.optJSONArray("slots")
            if (slots != null && slots.length() > 0) {
                val first = slots.optJSONObject(0)
                if (first != null) {
                    source.put("speciesId", first.optInt("speciesId", 0))
                    source.put("level", first.optInt("level", 1))
                    source.put("moves", first.optJSONArray("moves"))
                    source.put("variantFlags", first.optInt("variantFlags", 0))
                    source.put("specialFlags", first.optInt("specialFlags", 0))
                }
            }
        }

        if (!source.has("speciesId") || source.optInt("speciesId", 0) <= 0) {
            throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_stroll_payload",
                message = "speciesId is required for walking companion",
            )
        }

        return source
    }

    private fun buildLegacyWalkingSummarySource(body: JSONObject): JSONObject {
        val speciesId = body.optInt("speciesId", 0)
        if (speciesId <= 0) {
            throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_stroll_payload",
                message = "speciesId is required for walking companion",
            )
        }

        val source =
            JSONObject()
                .put("speciesId", speciesId)
                .put("level", body.optInt("level", 10).coerceIn(1, 100))
                .put("heldItemId", body.optInt("heldItemId", body.optInt("heldItem", 0)).coerceIn(0, 0xFFFF))
                .put("variantFlags", body.optInt("variantFlags", 0).coerceIn(0, 0xFF))
                .put("specialFlags", body.optInt("specialFlags", 0).coerceIn(0, 0xFF))

        if (body.has("moves")) {
            source.put("moves", body.optJSONArray("moves"))
        }

        return source
    }

    private fun writeCompanionSummary(
        eeprom: ByteArray,
        offset: Int,
        payload: JSONObject,
        allowSpeciesZero: Boolean,
    ) {
        val speciesId = payload.optInt("speciesId", 0)
        if (!allowSpeciesZero && speciesId <= 0) {
            throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_companion",
                message = "speciesId must be greater than zero",
            )
        }

        val clampedSpecies = speciesId.coerceIn(0, 0xFFFF)
        val heldItemId = payload.optInt("heldItemId", payload.optInt("heldItem", 0)).coerceIn(0, 0xFFFF)
        val level = payload.optInt("level", 1).coerceIn(1, 100)

        DeviceBinary.writeU16LE(eeprom, offset, clampedSpecies)
        DeviceBinary.writeU16LE(eeprom, offset + 2, heldItemId)

        val moves = payload.optJSONArray("moves")
        for (index in 0 until 4) {
            val move = moves?.optInt(index, 0) ?: 0
            DeviceBinary.writeU16LE(eeprom, offset + 4 + index * 2, move.coerceIn(0, 0xFFFF))
        }

        val variantFlags =
            when {
                payload.has("variantFlags") -> payload.optInt("variantFlags", 0).coerceIn(0, 0xFF)
                payload.optInt("gender", 0) == 1 -> 0x20
                else -> 0
            }
        val specialFlags = payload.optInt("specialFlags", 0).coerceIn(0, 0xFF)

        eeprom[offset + 10] = variantFlags.toByte()
        eeprom[offset + 11] = specialFlags.toByte()
        eeprom[offset + 12] = level.toByte()
        eeprom[offset + 13] = 0
        eeprom[offset + 14] = 0
        eeprom[offset + 15] = 0
    }

    private fun clearRouteItems(eeprom: ByteArray) {
        val routeListBytes = DeviceOffsets.ROUTE_ITEM_COUNT * 2
        val minStepsBytes = DeviceOffsets.ROUTE_ITEM_COUNT * 2

        eeprom.fill(
            element = 0,
            fromIndex = DeviceOffsets.ROUTE_ITEM_LIST_OFFSET,
            toIndex = DeviceOffsets.ROUTE_ITEM_LIST_OFFSET + routeListBytes,
        )
        eeprom.fill(
            element = 0,
            fromIndex = ROUTE_ITEM_MIN_STEPS_OFFSET,
            toIndex = ROUTE_ITEM_MIN_STEPS_OFFSET + minStepsBytes,
        )
        eeprom.fill(
            element = 0,
            fromIndex = ROUTE_ITEM_CHANCE_OFFSET,
            toIndex = ROUTE_ITEM_CHANCE_OFFSET + DeviceOffsets.ROUTE_ITEM_COUNT,
        )
    }

    private fun clearCaughtCompanion(eeprom: ByteArray) {
        val bytes = DeviceOffsets.COMPANION_SLOT_COUNT * DeviceOffsets.COMPANION_SUMMARY_SIZE
        eeprom.fill(
            element = 0,
            fromIndex = DeviceOffsets.CAUGHT_COMPANION_OFFSET,
            toIndex = DeviceOffsets.CAUGHT_COMPANION_OFFSET + bytes,
        )
    }

    private fun clearDowsedItems(eeprom: ByteArray) {
        val bytes = DeviceOffsets.DOWSED_ITEM_COUNT * DeviceOffsets.ITEM_DATA_SIZE
        eeprom.fill(
            element = 0,
            fromIndex = DeviceOffsets.DOWSED_ITEMS_OFFSET,
            toIndex = DeviceOffsets.DOWSED_ITEMS_OFFSET + bytes,
        )
    }

    private fun clearWalkingCompanion(eeprom: ByteArray) {
        eeprom.fill(
            element = 0,
            fromIndex = ROUTE_WALKING_SUMMARY_OFFSET,
            toIndex = ROUTE_WALKING_SUMMARY_OFFSET + DeviceOffsets.COMPANION_SUMMARY_SIZE,
        )
        writeDeviceTextFixed(
            eeprom = eeprom,
            offset = ROUTE_NICKNAME_OFFSET,
            maxChars = ROUTE_NICKNAME_CHARS,
            text = "",
        )
        DeviceBinary.writeWalkingCompanionFriendship(eeprom, 0)
    }

    private fun clearMatchingTeamCompanion(
        eeprom: ByteArray,
        speciesId: Int,
    ) {
        if (speciesId <= 0) {
            return
        }

        for (slot in 0 until TEAM_COMPANION_COUNT) {
            val offset = TEAM_COMPANION_OFFSET + slot * TEAM_COMPANION_SIZE
            val candidate = DeviceBinary.readU16LE(eeprom, offset + TEAM_COMPANION_SPECIES_OFFSET)
            if (candidate == speciesId) {
                eeprom.fill(
                    element = 0,
                    fromIndex = offset,
                    toIndex = offset + TEAM_COMPANION_SIZE,
                )
                return
            }
        }
    }

    private fun captureCompanion(
        eeprom: ByteArray,
        routeSlot: Int,
        replaceWhenFull: Boolean,
        captureIndex: Int,
    ): Boolean {
        if (routeSlot !in 0 until DeviceOffsets.COMPANION_SLOT_COUNT) {
            return false
        }

        val sourceOffset =
            DeviceOffsets.ROUTE_COMPANION_LIST_OFFSET + routeSlot * DeviceOffsets.COMPANION_SUMMARY_SIZE
        val species = DeviceBinary.readU16LE(eeprom, sourceOffset)
        if (species == 0) {
            return false
        }

        for (slot in 0 until DeviceOffsets.COMPANION_SLOT_COUNT) {
            val targetOffset = DeviceOffsets.CAUGHT_COMPANION_OFFSET + slot * DeviceOffsets.COMPANION_SUMMARY_SIZE
            val currentSpecies = DeviceBinary.readU16LE(eeprom, targetOffset)
            if (currentSpecies == 0) {
                System.arraycopy(eeprom, sourceOffset, eeprom, targetOffset, DeviceOffsets.COMPANION_SUMMARY_SIZE)
                return true
            }
        }

        if (!replaceWhenFull) {
            return false
        }

        val replaceSlot = captureIndex % DeviceOffsets.COMPANION_SLOT_COUNT
        val targetOffset = DeviceOffsets.CAUGHT_COMPANION_OFFSET + replaceSlot * DeviceOffsets.COMPANION_SUMMARY_SIZE
        System.arraycopy(eeprom, sourceOffset, eeprom, targetOffset, DeviceOffsets.COMPANION_SUMMARY_SIZE)
        return true
    }

    private fun toSpeciesList(array: JSONArray): List<Int> {
        val values = mutableListOf<Int>()
        for (index in 0 until array.length()) {
            val candidate = array.optInt(index, -1)
            if (candidate in 1..0xFFFF) {
                values.add(candidate)
            }
        }
        return values
    }

    private fun decodeHex(hex: String): ByteArray {
        val normalized = hex.trim()
        if (normalized.length % 2 != 0) {
            throw BridgeProtocolException(
                statusCode = 400,
                code = "invalid_hex",
                message = "Hex payload length must be even",
            )
        }

        val bytes = ByteArray(normalized.length / 2)
        var index = 0
        while (index < normalized.length) {
            val hi = normalized[index].digitToIntOrNull(16)
            val lo = normalized[index + 1].digitToIntOrNull(16)
            if (hi == null || lo == null) {
                throw BridgeProtocolException(
                    statusCode = 400,
                    code = "invalid_hex",
                    message = "Hex payload contains invalid characters",
                )
            }
            bytes[index / 2] = ((hi shl 4) or lo).toByte()
            index += 2
        }
        return bytes
    }

    private fun writeDeviceTextFixed(
        eeprom: ByteArray,
        offset: Int,
        maxChars: Int,
        text: String,
    ) {
        for (index in 0 until maxChars) {
            val encoded = if (index < text.length) encodeDeviceChar(text[index]) else 0x0000
            DeviceBinary.writeU16LE(eeprom, offset + index * 2, encoded)
        }
    }

    private fun encodeDeviceChar(char: Char): Int {
        return when {
            char == ' ' -> 0x0000
            char in '0'..'9' -> 0x0121 + (char.code - '0'.code)
            char in 'A'..'Z' -> 0x012B + (char.code - 'A'.code)
            char in 'a'..'z' -> 0x0145 + (char.code - 'a'.code)
            char == '!' -> 0x00E1
            char == '?' -> 0x00E2
            char == '*' -> 0x00E6
            char == '/' -> 0x00E7
            char == '+' -> 0x00F0
            char == '-' -> 0x00F1
            char == '=' -> 0x00F4
            char == '.' -> 0x00F8
            char == ',' -> 0x00F9
            else -> 0x00E2
        }
    }

    private fun JSONObject.requireInt(
        key: String,
        min: Int,
        max: Int,
    ): Int {
        if (!has(key)) {
            throw BridgeProtocolException(400, "missing_field", "Field '$key' is required")
        }
        val value = optInt(key, Int.MIN_VALUE)
        if (value == Int.MIN_VALUE || value !in min..max) {
            throw BridgeProtocolException(400, "invalid_field", "Field '$key' must be between $min and $max")
        }
        return value
    }

    private fun JSONObject.requireLong(
        key: String,
        min: Long,
        max: Long,
    ): Long {
        if (!has(key)) {
            throw BridgeProtocolException(400, "missing_field", "Field '$key' is required")
        }

        val raw = opt(key)
        val value =
            when (raw) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            } ?: throw BridgeProtocolException(400, "invalid_field", "Field '$key' must be numeric")

        if (value !in min..max) {
            throw BridgeProtocolException(400, "invalid_field", "Field '$key' must be between $min and $max")
        }
        return value
    }
}
