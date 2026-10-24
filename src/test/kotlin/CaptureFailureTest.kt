import assets.I18nManager
import core.HttpRequestTimeoutException
import core.fetchRecordsWithDeadline
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import model.CapturePhase
import storage.ImportTicket
import ui.AppViewModel
import kotlin.test.*

class CaptureFailureTest {
    @Test
    fun `timeout reaches UI without importing and capture can be started again`() = runBlocking {
        I18nManager.load("en").getOrThrow()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var captures = 0
        val viewModel = AppViewModel(
            scope = scope,
            searchAccount = { Result.success(null) },
            prepareCapture = { ImportTicket(0) },
            captureRecords = { phase ->
                captures++
                phase(CapturePhase.FETCHING)
                val failed = fetchRecordsWithDeadline { throw HttpRequestTimeoutException("test timeout") }
                Result.failure(failed.exceptionOrNull()!!)
            },
        )
        try {
            viewModel.selectUid("100000001")
            withTimeout(5_000) { viewModel.state.first { !it.isLoading } }
            repeat(2) { attempt ->
                withContext(Dispatchers.Main) { viewModel.captureFromProxy() }
                withTimeout(5_000) {
                    while (scope.coroutineContext[Job]!!.children.any()) yield()
                }
                val state = viewModel.state.value
                assertEquals(attempt + 1, captures)
                assertEquals(I18nManager["error.capture_timeout"], state.error)
                assertFalse(state.isCapturing)
                assertFalse(state.isLoading)
                assertEquals("100000001", state.selectedUid)
                assertNull(state.capturePhase)
                assertNull(state.lastImport)
                assertTrue(state.records.isEmpty())
                assertTrue(scope.isActive)
            }
        } finally { scope.cancel() }
    }
}
