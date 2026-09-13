package utilities

import assets.I18nManager
import core.AuthkeyExpiredException
import core.ProxyExceptionType
import core.GachaServerConnectionException
import core.InvalidAuthkeyUrlException
import core.ProxyException
import utilities.AppLogger

fun ProxyExceptionType.userMessage(): String = I18nManager[i18nKey()]

fun ProxyExceptionType.i18nKey(): String = when (this) {
    ProxyExceptionType.TIMEOUT -> "error.capture_timeout"
    ProxyExceptionType.PROXY_SCRIPT_FAILED -> "error.proxy_script_failed"
    ProxyExceptionType.NO_AUTHKEY -> "error.no_authkey"
    ProxyExceptionType.AUTHKEY_EXPIRED -> "error.authkey_expired"
    ProxyExceptionType.SERVER_CONNECTION -> "error.server_connection"
    ProxyExceptionType.INVALID_AUTHKEY_URL -> "error.invalid_authkey_url"
    ProxyExceptionType.PROXY_NOT_FOUND -> "error.proxy_not_found"
    ProxyExceptionType.PROXY_START_FAILED -> "error.proxy_start_failed"
    ProxyExceptionType.FETCH_FAILED -> "error.fetch_failed"
    ProxyExceptionType.AUTHKEY_DELIVERY_FAILED -> "error.authkey_delivery_failed"
    ProxyExceptionType.OPERATION_CANCELLED -> "error.operation_cancelled"
}

fun Throwable.toUserMessage(): String {
    if (this is ProxyException) return error.userMessage()
    return when (rootCause()) {
        is AuthkeyExpiredException -> I18nManager["error.authkey_expired"]
        is GachaServerConnectionException -> I18nManager["error.server_connection"]
        is InvalidAuthkeyUrlException -> I18nManager["error.invalid_authkey_url"]
        else -> message?.takeIf(::isMessageReadable) ?: I18nManager["error.capture_failed"]
    }
}

fun Throwable.safeUserMessage(): String = try {
    toUserMessage()
} catch (e: Exception) {
    AppLogger.error("Failed to format user-facing error message", e)
    I18nManager["error.capture_failed"]
}

private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()

private fun isMessageReadable(message: String): Boolean {
    if (message.isBlank()) return false
    if (message.contains('$')) return false
    if (message.matches(Regex("""^[\w.$/]+$"""))) return false
    return true
}