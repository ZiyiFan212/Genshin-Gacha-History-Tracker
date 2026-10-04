import core.ProxyReceiver
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyReceiverTest {
    private fun request(receiver: ProxyReceiver, token: String = receiver.getAuthToken(), body: String = "{\"url\":\"https://example.invalid/?authkey=test\"}", length: Int = body.toByteArray().size): Int =
        Socket("127.0.0.1", receiver.boundAddress!!.port).use { socket ->
            socket.soTimeout = 3_000
            val headers = "POST /authkey HTTP/1.1\r\nHost: localhost\r\nX-Capture-Token: $token\r\nContent-Type: application/json\r\nContent-Length: $length\r\n\r\n"
            socket.getOutputStream().write((headers + body).toByteArray(StandardCharsets.UTF_8))
            socket.getInputStream().bufferedReader().readLine().split(' ')[1].toInt()
        }

    @Test
    fun `receiver binds loopback authenticates and refuses overwrite`() {
        val receiver = ProxyReceiver(0)
        receiver.startServer()
        try {
            assertTrue(receiver.boundAddress!!.address.isLoopbackAddress)
            assertEquals(401, request(receiver, token = ""))
            assertEquals(401, request(receiver, token = "wrong"))
            assertNull(receiver.getCapturedAuthKeyUrl())
            assertEquals(200, request(receiver))
            assertEquals(409, request(receiver, body = "{\"url\":\"replacement\"}"))
            assertEquals("https://example.invalid/?authkey=test", receiver.getCapturedAuthKeyUrl())
        } finally { receiver.stopServer() }
    }

    @Test
    fun `oversized and invalid bodies are rejected without consuming the capture`() {
        val receiver = ProxyReceiver(0)
        receiver.startServer()
        try {
            assertEquals(413, request(receiver, body = "", length = ProxyReceiver.MAX_BODY_BYTES + 1))
            assertEquals(400, request(receiver, body = "not json"))
            assertEquals(400, request(receiver, body = "{\"url\":123}"))
            assertNull(receiver.getCapturedAuthKeyUrl())
            assertEquals(200, request(receiver))
        } finally { receiver.stopServer() }
    }

    @Test
    fun `restarting rotates the token and clears the old capture`() {
        val receiver = ProxyReceiver(0)
        receiver.startServer()
        val old = receiver.getAuthToken()
        assertEquals(200, request(receiver))
        receiver.stopServer()
        receiver.startServer()
        try {
            assertNotEquals(old, receiver.getAuthToken())
            assertNull(receiver.getCapturedAuthKeyUrl())
            assertEquals(401, request(receiver, token = old))
            assertEquals(200, request(receiver))
        } finally { receiver.stopServer() }
    }

    @Test
    fun `slow headers and bodies expire and do not block subsequent capture`() {
        val receiver = ProxyReceiver(0, requestTimeoutMs = 300)
        receiver.startServer()
        try {
            val partialRequests = listOf(
                "POST /authkey HTTP/1.1\r\nHost:",
                "POST /authkey HTTP/1.1\r\nX-Capture-Token: ${receiver.getAuthToken()}\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{",
            )
            for (partial in partialRequests) {
                Socket("127.0.0.1", receiver.boundAddress!!.port).use { socket ->
                    socket.soTimeout = 2_000
                    socket.getOutputStream().write(partial.toByteArray())
                    assertEquals(-1, socket.getInputStream().read())
                }
            }
            assertNull(receiver.getCapturedAuthKeyUrl())
            assertEquals(200, request(receiver))
        } finally { receiver.stopServer() }
    }

    @Test
    fun `trickling bytes cannot extend the total request deadline`() {
        val receiver = ProxyReceiver(0, requestTimeoutMs = 400)
        receiver.startServer()
        try {
            Socket("127.0.0.1", receiver.boundAddress!!.port).use { socket ->
                socket.soTimeout = 2_000
                socket.getOutputStream().write("POST /authkey HTTP/1.1\r\nX-Slow: ".toByteArray())
                val writer = Thread {
                    try {
                        repeat(30) {
                            socket.getOutputStream().write('a'.code)
                            Thread.sleep(50)
                        }
                    } catch (_: Exception) { }
                }.apply { isDaemon = true; start() }
                try { assertEquals(-1, socket.getInputStream().read()) }
                finally { writer.interrupt(); writer.join(1_000) }
            }
            assertNull(receiver.getCapturedAuthKeyUrl())
            assertEquals(200, request(receiver))
        } finally { receiver.stopServer() }
    }

    @Test
    fun `interrupted caller can stop and restart the receiver`() {
        val receiver = ProxyReceiver(0)
        receiver.startServer()
        try {
            Thread.currentThread().interrupt()
            receiver.stopServer()
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
        receiver.startServer()
        try { assertEquals(200, request(receiver)) }
        finally { receiver.stopServer() }
    }
}
