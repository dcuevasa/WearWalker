package com.wearwalker.wearwalker.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class BridgeHttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

data class BridgeHttpResponse(
    val statusCode: Int,
    val body: ByteArray,
    val contentType: String = "application/json",
    val headers: Map<String, String> = emptyMap(),
)

class BridgeHttpServer(
    private val port: Int,
    private val scope: CoroutineScope,
    private val requestHandler: suspend (BridgeHttpRequest) -> BridgeHttpResponse,
    private val onStatusChanged: (String) -> Unit,
) {
    companion object {
        private const val ACCEPT_TIMEOUT_MS = 1000
        private const val MAX_BODY_SIZE = 131072
        private const val MAX_LINE_LENGTH = 8192
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        acceptJob =
            scope.launch(Dispatchers.IO) {
                runServerLoop()
            }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        onStatusChanged("Wi-Fi bridge offline")
    }

    private suspend fun runServerLoop() {
        try {
            val socket = ServerSocket(port)
            socket.reuseAddress = true
            socket.soTimeout = ACCEPT_TIMEOUT_MS
            serverSocket = socket
            onStatusChanged("Wi-Fi bridge listening on 0.0.0.0:$port")

            while (running.get() && scope.isActive) {
                try {
                    val client = socket.accept()
                    scope.launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                } catch (_: SocketTimeoutException) {
                    // Loop again and check running flag.
                }
            }
        } catch (error: IOException) {
            onStatusChanged("Wi-Fi bridge failed on port $port: ${error.message}")
            running.set(false)
        } finally {
            runCatching { serverSocket?.close() }
            serverSocket = null
            if (!running.get()) {
                onStatusChanged("Wi-Fi bridge offline")
            }
        }
    }

    private suspend fun handleClient(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = 5000
                val request = readRequest(socket) ?: return
                val response =
                    try {
                        requestHandler(request)
                    } catch (error: Exception) {
                        jsonError(
                            statusCode = 500,
                            code = "internal_error",
                            message = error.message ?: "Internal server error",
                        )
                    }
                writeResponse(socket, response)
            }.onFailure {
                if (it !is SocketException) {
                    runCatching {
                        writeResponse(
                            socket,
                            jsonError(
                                statusCode = 500,
                                code = "internal_error",
                                message = it.message ?: "Internal server error",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun readRequest(socket: Socket): BridgeHttpRequest? {
        val input = BufferedInputStream(socket.getInputStream())

        val requestLine = readAsciiLine(input) ?: return null
        if (requestLine.isBlank()) {
            return null
        }

        val requestParts = requestLine.split(' ')
        if (requestParts.size < 2) {
            return null
        }

        val method = requestParts[0].trim().uppercase(Locale.ROOT)
        val path = requestParts[1].trim()

        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readAsciiLine(input) ?: return null
            if (line.isEmpty()) {
                break
            }

            val colon = line.indexOf(':')
            if (colon <= 0) {
                continue
            }

            val name = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            val value = line.substring(colon + 1).trim()
            headers[name] = value
        }

        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength < 0 || contentLength > MAX_BODY_SIZE) {
            throw IOException("Request body exceeds limits")
        }

        val body = ByteArray(contentLength)
        readFully(input, body)

        return BridgeHttpRequest(
            method = method,
            path = path,
            headers = headers,
            body = body,
        )
    }

    private fun writeResponse(
        socket: Socket,
        response: BridgeHttpResponse,
    ) {
        val output = BufferedOutputStream(socket.getOutputStream())

        val reason = reasonPhrase(response.statusCode)
        val headers = LinkedHashMap<String, String>()
        headers["Content-Type"] = response.contentType
        headers["Content-Length"] = response.body.size.toString()
        headers["Connection"] = "close"
        response.headers.forEach { (name, value) -> headers[name] = value }

        val builder = StringBuilder()
        builder.append("HTTP/1.1 ${response.statusCode} $reason\r\n")
        headers.forEach { (name, value) ->
            builder.append(name).append(": ").append(value).append("\r\n")
        }
        builder.append("\r\n")

        output.write(builder.toString().toByteArray(StandardCharsets.US_ASCII))
        output.write(response.body)
        output.flush()
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val buffer = ByteArray(MAX_LINE_LENGTH)
        var count = 0

        while (true) {
            val next = input.read()
            if (next == -1) {
                return if (count == 0) null else String(buffer, 0, count, StandardCharsets.US_ASCII)
            }

            if (next == '\n'.code) {
                val length = if (count > 0 && buffer[count - 1] == '\r'.code.toByte()) count - 1 else count
                return String(buffer, 0, length, StandardCharsets.US_ASCII)
            }

            if (count >= buffer.size) {
                throw IOException("HTTP line too long")
            }

            buffer[count] = next.toByte()
            count += 1
        }
    }

    private fun readFully(
        input: BufferedInputStream,
        target: ByteArray,
    ) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read <= 0) {
                throw EOFException("Unexpected EOF while reading request body")
            }
            offset += read
        }
    }

    private fun jsonError(
        statusCode: Int,
        code: String,
        message: String,
    ): BridgeHttpResponse {
        val payload = "{\"error\":\"${escapeJson(code)}\",\"message\":\"${escapeJson(message)}\"}"
        return BridgeHttpResponse(
            statusCode = statusCode,
            body = payload.toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun escapeJson(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun reasonPhrase(statusCode: Int): String {
        return when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            411 -> "Length Required"
            413 -> "Payload Too Large"
            415 -> "Unsupported Media Type"
            422 -> "Unprocessable Entity"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "Status"
        }
    }
}
