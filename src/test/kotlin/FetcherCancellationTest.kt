import kotlinx.coroutines.*
import core.*
import fetcher.Fetcher
import model.ProxyExceptionType
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import kotlin.test.*

class FetcherCancellationTest {
    // we define a customized http client for offline testing.
    private class Client( private val interruptImmediately: Boolean = false,
                          private val respond: ((HttpRequest) -> String)? = null,
        ) : HttpClient() {
        val sending = CompletableDeferred<Unit>()
        var forced = false
        var shutdowns = 0
        var waits = 0
        var interruptClose = false
        var sends = 0
        override fun <T> send(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
            sends++
            sending.complete(Unit)
            if (respond != null) {
                val text = respond.invoke(request)
                return object : HttpResponse<T> {
                    override fun statusCode() = 200
                    override fun request() = request
                    override fun previousResponse(): Optional<HttpResponse<T>> = Optional.empty()
                    override fun headers() = HttpHeaders.of(emptyMap()) { _, _ -> true }
                    @Suppress("UNCHECKED_CAST")
                    override fun body(): T = text as T
                    override fun sslSession(): Optional<SSLSession> = Optional.empty()
                    override fun uri() = request.uri()
                    override fun version() = Version.HTTP_1_1
                }
            }
            if (interruptImmediately) throw InterruptedException("test interruption")
            Thread.sleep(Long.MAX_VALUE)
            error("Interrupted send must not return normally")
        }
        override fun shutdown() { shutdowns++ }
        override fun shutdownNow() { forced = true }
        override fun awaitTermination(duration: Duration): Boolean {
            waits++
            if (interruptClose) throw InterruptedException("close interrupted")
            return forced
        }
        override fun close() { error("Unbounded HttpClient.close must not be used") }
        override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
        override fun connectTimeout(): Optional<Duration> = Optional.empty()
        override fun followRedirects() = Redirect.NEVER
        override fun proxy(): Optional<ProxySelector> = Optional.empty()
        override fun sslContext(): SSLContext = SSLContext.getDefault()
        override fun sslParameters() = SSLParameters()
        override fun authenticator(): Optional<Authenticator> = Optional.empty()
        override fun version() = Version.HTTP_1_1
        override fun executor(): Optional<Executor> = Optional.empty()
        override fun <T> sendAsync(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): CompletableFuture<HttpResponse<T>> = error("unused")
        override fun <T> sendAsync(request: HttpRequest, handler: HttpResponse.BodyHandler<T>, push: HttpResponse.PushPromiseHandler<T>): CompletableFuture<HttpResponse<T>> = error("unused")
    }

    // we simulate the response of Mihoyo server.
    private fun fake_page(vararg ids: String): String {
        val list = ids.joinToString(",") { id ->
            """{"uid":"100000001","gacha_type":"301","time":"2026-01-01 12:00:00","name":"Test","item_type":"character","item_id":"10000003","id":"$id","rank_type":"3"}"""
        }
        return """{"retcode":0,"data":{"list":[$list]}}"""
    }

    /**
     * For all test cases, use offline ONLY constructor in the fetcher!!
     */
    @Test
    fun `retry timeout testing, with 3 attempts and retaining the type`() = runBlocking {
        val client = Client(respond = { request ->
            assertEquals(Duration.ofSeconds(15), request.timeout().orElseThrow())
            throw HttpTimeoutException("test timeout")
        })

        Fetcher("https://example.test/?authkey=test", client).use { fetcher ->
            val result = fetchRecordsWithDeadline { fetcher.getAllRecords() }
            val error = assertIs<ProxyException>(result.exceptionOrNull())

            // expect: 3 attempts and request timeout thrown.
            assertEquals(ProxyExceptionType.TIMEOUT, error.error)
            assertIs<HttpRequestTimeoutException>(error.cause)
            assertEquals(3, client.sends)
        }
    }

    @Test
    fun `timeout retries can recover to a complete empty capture`() = runBlocking {
        var attempts = 0
        val client = Client(respond = {
            if (++attempts <= 2) throw HttpTimeoutException("test timeout")
            fake_page()
        })
        Fetcher("https://example.test/?authkey=test", client).use { fetcher ->
            assertTrue(fetchRecordsWithDeadline { fetcher.getAllRecords() }.getOrThrow().isEmpty())
            assertEquals(8, client.sends) // Two retries followed by the six banners.
        }
    }

    @Test
    fun `testing trimming on repeated page`() = runBlocking {
        var calls = 0
        val client = Client(respond = { if (++calls <= 2) fake_page("10") else fake_page() })
        Fetcher("https://example.test/?authkey=test", client).use { fetcher ->
            val result = fetchRecordsWithDeadline { fetcher.getAllRecords() }
            assertEquals(listOf("10"), result.getOrThrow().map { it.recordID })
        }
    }

    @Test
    fun `testing trimming records after the duplicated endID, return all pages`() = runBlocking {
        var calls = 0
        val client = Client(respond = { when (++calls) {
            1 -> fake_page("30", "20")
            2 -> fake_page("10", "20", "5")
            else -> fake_page()
        } })

        // The algorithm simply cutoff the records!
        // Having same endID from the official server is extremely rare, but never zero.
        Fetcher("https://example.test/?authkey=test", client).use { fetcher ->
            assertEquals(listOf("30", "20", "10"), fetchRecordsWithDeadline { fetcher.getAllRecords() }
                .getOrThrow().map { it.recordID })
        }
    }




    /**
     * The following three tests focus on interruptible bridge closure process, validate if
     * the coroutine job is correctly canceled in current scope. Thrown exception type must
     * also be validated, as interruptible exception should not be caught by another IO exception
     * branches!!
     */
    @Test
    fun `testing client closure via interruptible bridge`() = runBlocking {
        val client = Client()
        val fetcher = Fetcher("https://example.test/?authkey=test", client)
        val cleaned = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.IO) {
            try {
                runInterruptible { fetcher.getAllRecords() }
            } finally {
                fetcher.close()
                cleaned.complete(Unit)
            }
        }

        // as we proceed shutdown, current job is canceled and fetcher is closed.
        try {
            withTimeout(5_000) { client.sending.await() }
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertTrue(cleaned.isCompleted)
            assertEquals(1, client.shutdowns)
            assertTrue(client.forced)

        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `check the thrown exception type from fetcher (Interrupt exception!)`() {
        val client = Client(interruptImmediately = true)
        Fetcher("https://example.test/?authkey=test", client).use {
            assertFailsWith<InterruptedException> { it.getAllRecords() }
        }
    }

    @Test
    fun `check if interrupt flag is restored after forcibly shutdown`() {
        val client = Client().apply { interruptClose = true }
        try {
            Fetcher("https://example.test/?authkey=test", client).close()
            assertTrue(client.forced)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }
}
