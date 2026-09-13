package core

import assets.ItemTranslator
import fetcher.Fetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utilities.AppLogger
import model.GachaRecord
import model.sanitizeItemName
import storage.AppDatabase
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

enum class CapturePhase {
    STARTING,
    WAITING_FOR_GAME,
    FETCHING,
}

enum class ProxyExceptionType {
    TIMEOUT,
    PROXY_SCRIPT_FAILED,
    NO_AUTHKEY,
    AUTHKEY_EXPIRED,
    SERVER_CONNECTION,
    INVALID_AUTHKEY_URL,
    PROXY_NOT_FOUND,
    PROXY_START_FAILED,
    FETCH_FAILED,
    AUTHKEY_DELIVERY_FAILED,
    OPERATION_CANCELLED,
}

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
     * @param currentUid Currently selected user UID, used to query database for latest record ID during incremental update
     * @return Result containing UID and list of gacha records
     */
    suspend fun captureGachaRecords(onPhase: (CapturePhase) -> Unit = {}, currentUid: String? = null):
            Result<Pair<String, List<GachaRecord>>> {

        return withContext(Dispatchers.IO) {
            val proxyReceiver = ProxyReceiver()
            var proxyProcess: Process? = null
            var fetcher: Fetcher? = null

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
                    ProcessBuilder(nodeExe, proxyScript.absolutePath, "8080")
                        .directory(File(System.getProperty("user.dir")))
                        .redirectErrorStream(true)
                        .start()
                } catch (e: IOException) {
                    return@withContext Result.failure(ProxyException(ProxyExceptionType.PROXY_START_FAILED, cause = e))
                }

                onPhase(CapturePhase.WAITING_FOR_GAME)
                //debug(proxyProcess)

                val finished = proxyProcess.waitFor(120, TimeUnit.SECONDS)
                if (!finished) {
                    proxyProcess.destroyForcibly()
                    return@withContext Result.failure(ProxyException(ProxyExceptionType.TIMEOUT))
                }

                if (proxyProcess.exitValue() != 0) {
                    val exitCode = proxyProcess.exitValue()
                    AppLogger.error("Proxy script exited with code $exitCode")
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
                fetcher = Fetcher(authKeyURL)

                if (currentUid != null) {
                    val lastEndId = AppDatabase.getLastEndID(currentUid).getOrNull()
                    if (!lastEndId.isNullOrEmpty())
                        fetcher.setLastEndId(lastEndId)
                }

                val javaRecords = try {
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

                Result.success(Fetcher.getUid() to records)
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
                AppLogger.error("Unexpected capture error", e)
                Result.failure(ProxyException(ProxyExceptionType.FETCH_FAILED, cause = e))
            } finally {
                fetcher?.close()
                /**
                 * We have to ensure that the proxy server is destroyed despite the lauching process is
                 * disruppted or failed. Otherwise, the proxy server will be kept on and blocking the
                 * network.
                 * Here we terminate the process and its children process.
                 */
                proxyReceiver.stopServer()
                proxyProcess?.destroy()
                proxyProcess?.destroyForcibly()
                proxyProcess?.descendants()?.forEach { it.destroyForcibly() }
                resetSystemProxy()

                System.clearProperty("http.proxyHost")
                System.clearProperty("http.proxyPort")
                System.clearProperty("https.proxyHost")
                System.clearProperty("https.proxyPort")
                java.net.ProxySelector.setDefault(null)
            }
        }
    }

    /**
     * Running a script to reset the system proxy, as capturing records open a local
     * proxy server at port 8080.
     *
     *  - Not cleanup will cause apps such as Microsoft Edge unable to connect to the
     *  internet. Since interrupting the user's normal usage is not what we plan, having more
     *  backups is essentially important.
     *
     *  - If this script fail, another cleanup script [cleanup.js] will forcibly
     *  shut off the proxy server by editing the registry table (on Windows OS).
     */
    private fun resetSystemProxy() {
        val cleanupScript = File(System.getProperty("user.dir"), "proxy/cleanup.js")
        
        try {
            val systemProxyScript = File(System.getProperty("user.dir"), "proxy/systemProxy.js")
            if (systemProxyScript.isFile) {
                val script = """
                    const { setSystemProxy } = require('./proxy/systemProxy');
                    (async () => {
                        try {
                            await setSystemProxy(false);
                        } catch (e) {
                            process.exit(1);
                        }
                    })();
                """.trimIndent()
                
                val scriptProcess = ProcessBuilder(nodeExe, "-e", script)
                    .directory(File(System.getProperty("user.dir")))
                    .redirectErrorStream(true)
                    .start()
                
                val completed = scriptProcess.waitFor(5, TimeUnit.SECONDS)
                if (!completed) {
                    scriptProcess.destroyForcibly()
                    AppLogger.warn("System proxy cleanup process timed out, trying cleanup script")
                    runCleanupScript(cleanupScript)
                } else if (scriptProcess.exitValue() != 0) {
                    AppLogger.warn("System proxy cleanup script failed with exit code ${scriptProcess.exitValue()}, trying cleanup script")
                    runCleanupScript(cleanupScript)
                }
            } else {
                AppLogger.warn("systemProxy.js not found, using cleanup script directly")
                runCleanupScript(cleanupScript)
            }
        } catch (e: Exception) {
            AppLogger.error("Failed to cleanup proxy via system script", e)
            runCleanupScript(cleanupScript)
        }
    }

    private fun runCleanupScript(cleanupScript: File) {
        if (!cleanupScript.isFile) {
            AppLogger.error("Cleanup script not found: ${cleanupScript.absolutePath}")
            return
        }
        
        try {
            val process = ProcessBuilder(nodeExe, cleanupScript.absolutePath, "8080")
                .directory(File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .start()
            
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                AppLogger.warn("Cleanup script timed out")
            } else if (process.exitValue() != 0) {
                AppLogger.warn("Cleanup script failed with exit code ${process.exitValue()}")
            }
        } catch (e: Exception) {
            AppLogger.error("Failed to run cleanup script", e)
        }
    }

    /**
    private fun debug (process: Process) {
        Thread {
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                var line?
                while (reader.readLine().also { line = it } != null) {
                    AppLogger.debug("$line")
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
    */

}