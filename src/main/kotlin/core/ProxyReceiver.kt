package core

import com.fasterxml.jackson.databind.ObjectMapper
import logger.debug
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** A single-purpose HTTP receiver: fixed-length POSTs, no keep-alive or chunked bodies. */
class ProxyReceiver(private val port: Int = 3000, private val requestTimeoutMs: Long = 5_000) {
    private val mapper = ObjectMapper()
    @Volatile private var captured = AtomicReference<String?>(null)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private var server: ServerSocket? = null
    private var workers: ThreadPoolExecutor? = null
    private var deadlines: ScheduledThreadPoolExecutor? = null
    private var acceptThread: Thread? = null
    private var token = ""

    val boundAddress: InetSocketAddress?
        @Synchronized get() = server?.localSocketAddress as? InetSocketAddress

    @Synchronized
    fun getAuthToken(): String = token

    @Synchronized
    fun startServer() {
        if (server != null) return
        require(requestTimeoutMs > 0)
        val listener = ServerSocket()

        try {
            listener.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 8)
        } catch (e: Exception) {
            listener.close()
            throw e
        }

        token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val sessionCapture = AtomicReference<String?>(null)
        captured = sessionCapture

        val expectedToken = token.toByteArray(StandardCharsets.US_ASCII)
        val factory = ThreadFactory { task -> Thread(task, "authkey-receiver").apply { isDaemon = true } }
        val pool = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4), factory)
        val timer = ScheduledThreadPoolExecutor(1, factory).apply { removeOnCancelPolicy = true }
        workers = pool
        deadlines = timer
        server = listener
        acceptThread = factory.newThread {
            while (!listener.isClosed) {
                val socket = try { listener.accept() } catch (_: IOException) { break }
                sockets.add(socket)
                try {
                    // Includes queueing, headers and body, even if a client trickles bytes forever.
                    val deadline = timer.schedule({ runCatching { socket.close() } }, requestTimeoutMs, TimeUnit.MILLISECONDS)
                    try {
                        pool.execute {
                            try {
                                socket.use { handleRequest(it, expectedToken, sessionCapture) }
                            } finally {
                                deadline.cancel(false)
                                sockets.remove(socket)
                            }
                        }
                    } catch (_: RejectedExecutionException) {
                        deadline.cancel(false)
                        sockets.remove(socket)
                        socket.close()
                    }
                } catch (_: RejectedExecutionException) {
                    sockets.remove(socket)
                    socket.close()
                }
            }
        }.apply { start() }
        debug("ProxyReceiver listening on 127.0.0.1:${listener.localPort}")
    }

    @Synchronized
    fun stopServer() {
        var interrupted = Thread.interrupted()
        try {
            server?.close()
            server = null
            try { acceptThread?.join(1_000) }
            catch (_: InterruptedException) { interrupted = true }
        } finally {
            acceptThread = null
            sockets.forEach { runCatching { it.close() } }
            sockets.clear()
            workers?.shutdownNow()
            deadlines?.shutdownNow()
            workers = null
            deadlines = null
            token = ""
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    fun getCapturedAuthKeyUrl(): String? = captured.get()

    fun clearCapturedAutoKeyUrl() { captured.set(null) }

    // Receiving the URL from the socket. Validating and storing in the session.
    private fun handleRequest(socket: Socket, expectedToken: ByteArray, sessionCapture: AtomicReference<String?>) {
        try {
            socket.soTimeout = requestTimeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val input = socket.getInputStream()
            val header = ByteArrayOutputStream()
            var tail = 0

            while (true) {
                val byte = input.read()
                if (byte < 0) return
                header.write(byte)
                tail = (tail shl 8) or byte
                if (header.size() > MAX_HEADER_BYTES) return respond(socket, 431)
                if (tail == 0x0d0a0d0a) break
            }

            // First validate caught bytes, if it not any HTTP protocol, reject.
            val lines = header.toString(StandardCharsets.ISO_8859_1).split("\r\n")
            val request = lines.first().split(' ')
            if (request.size != 3 || (request[2] != "HTTP/1.0" && request[2] != "HTTP/1.1")) return respond(socket, 400)
            if (request[0] != "POST") return respond(socket, 405)
            if (request[1] != "/authkey") return respond(socket, 404)

            val headers = mutableMapOf<String, String>()
            for (line in lines.drop(1).filter { it.isNotEmpty() }) {
                val separator = line.indexOf(':')
                if (separator <= 0) return respond(socket, 400)
                val name = line.substring(0, separator).lowercase(java.util.Locale.ROOT)
                if (headers.put(name, line.substring(separator + 1).trim()) != null) return respond(socket, 400)
            }

            val supplied = headers["x-capture-token"]?.toByteArray(StandardCharsets.US_ASCII) ?: byteArrayOf()
            if (!MessageDigest.isEqual(expectedToken, supplied)) return respond(socket, 401)
            if (headers.containsKey("transfer-encoding")) return respond(socket, 400)
            if (headers["content-type"]?.substringBefore(';')?.trim() != "application/json") return respond(socket, 415)

            val length = headers["content-length"]?.toLongOrNull() ?: return respond(socket, 411)
            if (length < 0) return respond(socket, 400)
            if (length > MAX_BODY_BYTES) return respond(socket, 413)

            // Parsing byte to JSON
            val bytes = input.readNBytes(length.toInt())
            if (bytes.size != length.toInt()) return respond(socket, 400)
            val root = try {
                mapper.readTree(bytes)
            } catch (_: IOException) {
                return respond(socket, 400)
            }
            val value = root?.get("url")
            if (value == null || !value.isTextual || value.asText().isBlank()) return respond(socket, 400)
            if (socket.isClosed) return
            if (!sessionCapture.compareAndSet(null, value.asText())) return respond(socket, 409)
            debug("ProxyReceiver: authenticated URL saved (${value.asText().length} chars)")
            respond(socket, 200)
        } catch (_: IOException) {
            // Expired/closed sockets cannot keep a worker blocked or expose captured credentials.
        }
    }

    private fun respond(socket: Socket, status: Int) {
        val body = if (status == 200) "OK" else "Request rejected"
        val response = "HTTP/1.1 $status Result\r\nConnection: close\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\n\r\n$body"
        socket.getOutputStream().write(response.toByteArray(StandardCharsets.US_ASCII))
    }

    internal companion object {
        const val MAX_BODY_BYTES = 16 * 1024
        private const val MAX_HEADER_BYTES = 8 * 1024
    }
}
