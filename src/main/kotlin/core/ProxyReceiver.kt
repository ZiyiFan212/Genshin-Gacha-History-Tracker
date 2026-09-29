package core

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import utilities.LogBody
import utilities.LogLevel
import utilities.LogWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class ProxyReceiver {
    private val mapper = ObjectMapper()
    private val port = 3000
    private var server: HttpServer? = null
    @Volatile
    private var capturedAuthKeyUrl: String? = null

    fun startServer() {
        if (server != null) return

        server = HttpServer.create(InetSocketAddress(port), 0).apply {
            createContext("/authkey") { exchange ->
                handleAuthkeyRequest(exchange)
            }
            setExecutor(null)
            start()
        }
        LogWriter.instance.tryLog(LogBody(LogLevel.INFO, "ProxyReceiver listening on localhost:$port"))
    }

    private fun handleAuthkeyRequest(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "POST") {
                sendError(exchange, 405)
                return
            }

            exchange.requestBody.use { inputStream ->
                val bytes = inputStream.readAllBytes()
                val jsonStr = String(bytes, StandardCharsets.UTF_8)
                val root = mapper.readTree(jsonStr)

                if (root == null || !root.has("url")) {
                    LogWriter.instance.tryLog(LogBody(LogLevel.WARN, "ProxyReceiver: invalid JSON, missing 'url' field"))
                    sendError(exchange, 400)
                    return
                }

                val gachaUrl = root["url"].asText()
                if (gachaUrl.isNullOrBlank()) {
                    LogWriter.instance.tryLog(LogBody(LogLevel.WARN, "ProxyReceiver: captured URL is empty"))
                    sendError(exchange, 400)
                    return
                }

                capturedAuthKeyUrl = gachaUrl
                LogWriter.instance.tryLog(LogBody(LogLevel.INFO, "Authkey URL captured (${gachaUrl.length} chars)"))
            }

            val response = "OK"
            val responseBytes = response.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { outputStream ->
                outputStream.write(responseBytes)
            }
        } catch (e: JsonProcessingException) {
            LogWriter.instance.tryLog(LogBody(LogLevel.ERROR, "ProxyReceiver: failed to parse authkey JSON", e))
            sendError(exchange, 400)
        } catch (e: IOException) {
            LogWriter.instance.tryLog(LogBody(LogLevel.ERROR, "ProxyReceiver: IO error handling request", e))
            sendError(exchange, 500)
        }
    }

    private fun sendError(exchange: HttpExchange, code: Int) {
        try {
            exchange.sendResponseHeaders(code, 0)
            exchange.close()
        } catch (_: IOException) {
        }
    }

    fun stopServer() {
        server?.let {
            it.stop(1)
            server = null
            LogWriter.instance.tryLog(LogBody(LogLevel.INFO, "ProxyReceiver: server stopped"))
        }
    }

    fun getCapturedAuthKeyUrl(): String? = capturedAuthKeyUrl

    fun clearCapturedAutoKeyUrl() {
        capturedAuthKeyUrl = null
    }
}