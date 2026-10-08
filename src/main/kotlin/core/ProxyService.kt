package core

import assets.ItemTranslator
import fetcher.Fetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logger.LogBody
import model.Severity
import logger.LogWriter
import logger.debug
import model.CapturePhase
import model.ProxyExceptionType
import model.GachaRecord
import utilities.records.sanitizeItemName
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.UUID

private const val PROXY_EXIT_TIMEOUT = 2
private const val PROXY_EXIT_DELIVERY_FAILED = 3

class ProxyException(val error: ProxyExceptionType, message: String? = null,
    cause: Throwable? = null) : Exception(message, cause)

class ProxyService {
    // executable node path
    val nodeExe = NodeLocator.resolveNodejsEnvironment()

    /**
     * Capture gacha records
     *
     * @param onPhase Callback function to notify capture phase changes
     * @return Result containing UID and list of gacha records
     */
    suspend fun captureGachaRecords(onPhase: (CapturePhase) -> Unit = {}):
            Result<Pair<String, List<GachaRecord>>> {

        return withContext(Dispatchers.IO) {
            val proxyReceiver = ProxyReceiver()
            val sessionId = UUID.randomUUID().toString()
            var proxyProcess: Process? = null
            var fetcher: Fetcher? = null
            val lifecycle = ProxyLifecycle({ recoverProxySession(sessionId) })

            try {
                onPhase(CapturePhase.STARTING)
                proxyReceiver.clearCapturedAutoKeyUrl()

                val proxyScript = NodeLocator.resolveProxyScriptPath()
                if (!proxyScript.isFile) {
                    return@withContext Result.failure(ProxyException(ProxyExceptionType.PROXY_NOT_FOUND))
                }

                try {
                    proxyReceiver.startServer()
                } catch (e: Exception) {
                    return@withContext Result.failure(ProxyException(ProxyExceptionType.PROXY_START_FAILED, cause = e))
                }

                proxyProcess = try {
                    lifecycle.start(ProcessBuilder(nodeExe, proxyScript.absolutePath, "8080")
                        .directory(File(System.getProperty("user.dir")))
                        .apply {
                            environment()["GENSHIN_PROXY_SESSION"] = sessionId
                            environment()["GENSHIN_CAPTURE_TOKEN"] = proxyReceiver.getAuthToken()
                            environment()["GENSHIN_PROXY_PARENT_PIPE"] = "1"
                        }
                        .redirectErrorStream(true))
                } catch (e: IOException) {
                    return@withContext Result.failure(ProxyException(ProxyExceptionType.PROXY_START_FAILED, cause = e))
                }

                onPhase(CapturePhase.WAITING_FOR_GAME)
                streamProxyDebugOutput(proxyProcess)
                debug("ProxyService: waiting for Node exit (120s); proxy=8080, receiver=3000")

                val finished = proxyProcess.waitFor(120, TimeUnit.SECONDS)
                if (!finished) {
                    debug("ProxyService: Node exit timed out; URL received=${proxyReceiver.getCapturedAuthKeyUrl() != null}")
                    return@withContext Result.failure(ProxyException(ProxyExceptionType.TIMEOUT))
                }

                debug("ProxyService: Node exited with code ${proxyProcess.exitValue()}")
                if (proxyProcess.exitValue() != 0) {
                    val exitCode = proxyProcess.exitValue()
                    LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Proxy script exited with code $exitCode"))
                    val error = when (exitCode) {
                        PROXY_EXIT_TIMEOUT -> ProxyExceptionType.TIMEOUT
                        PROXY_EXIT_DELIVERY_FAILED -> ProxyExceptionType.AUTHKEY_DELIVERY_FAILED
                        else -> ProxyExceptionType.PROXY_SCRIPT_FAILED
                    }
                    return@withContext Result.failure(ProxyException(error))
                }

                val authKeyURL = proxyReceiver.getCapturedAuthKeyUrl()
                    ?: return@withContext Result.failure(ProxyException(ProxyExceptionType.NO_AUTHKEY))

                onPhase(CapturePhase.FETCHING)
                debug("ProxyService: creating Fetcher")
                fetcher = Fetcher(authKeyURL)
                debug("ProxyService: Fetcher initialized; query preparation completed")

                val javaRecords = try {
                    debug("ProxyService: calling Fetcher.getAllRecords()")
                    fetcher.getAllRecords()
                } catch (e: IOException) {
                    return@withContext Result.failure(ProxyException(ProxyExceptionType.SERVER_CONNECTION, cause = e))
                }

                val records = javaRecords.map { record ->
                    /**
                     * Since Mihoyo doesn't send back the item ID, we need to create the Name -> ID map [ItemTranslator.buildNameToIdMap].
                     * Now, each item has its corresponding ID.
                     * */
                    val itemId = ItemTranslator.getIdByName(record.name)
                    record.copy(
                        itemID = itemId,
                        itemType = record.sanitizeItemName()
                    )
                }

                debug("ProxyService: fetch completed; records=${records.size}, UID present=${fetcher.getUid().isNotBlank()}")
                Result.success(fetcher.getUid() to records)
            } catch (e: AuthkeyExpiredException) {
                Result.failure(ProxyException(ProxyExceptionType.AUTHKEY_EXPIRED, cause = e))
            } catch (e: GachaServerConnectionException) {
                Result.failure(ProxyException(ProxyExceptionType.SERVER_CONNECTION, cause = e))
            } catch (e: InvalidAuthkeyUrlException) {
                Result.failure(ProxyException(ProxyExceptionType.INVALID_AUTHKEY_URL, cause = e))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Result.failure(ProxyException(ProxyExceptionType.OPERATION_CANCELLED, cause = e))
            } catch (e: Exception) {
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Unexpected capture error", e))
                Result.failure(ProxyException(ProxyExceptionType.FETCH_FAILED, cause = e))
            } finally {
                try {
                    lifecycle.close()
                } catch (e: Exception) {
                    LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Capture cleanup failed; session snapshot retained", e))
                } finally {
                    proxyReceiver.stopServer()
                    fetcher?.close()
                }

            }
        }
    }

    private fun recoverProxySession(sessionId: String) {
        val sessionScript = File(System.getProperty("user.dir"), "proxy/windowsProxySession.js")
        try {
            val process = ProcessBuilder(nodeExe, sessionScript.absolutePath)
                .directory(File(System.getProperty("user.dir")))
                .apply { environment()["GENSHIN_PROXY_SESSION"] = sessionId }
                .redirectErrorStream(true)
                .start()
            streamProxyDebugOutput(process)
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Session proxy recovery timed out; snapshot retained"))
            } else if (process.exitValue() != 0) {
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Session proxy recovery failed; snapshot retained"))
            }
        } catch (e: Exception) {
            LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to recover capture proxy session", e))
        }
    }

    private fun streamProxyDebugOutput(process: Process) {
        Thread {
            try {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.forEachLine { line ->
                        val redacted = line.replace(Regex("(?i)(authkey=)[^&\\s]+"), "$1[REDACTED]")
                        debug("Node: $redacted")
                    }
                }
                debug("ProxyService: Node output stream closed")
            } catch (e: IOException) {
                debug("ProxyService: Node output reader stopped (${e.javaClass.simpleName})")
            }
        }.apply {
            name = "proxy-output-reader"
            isDaemon = true
            start()
        }
    }

}
