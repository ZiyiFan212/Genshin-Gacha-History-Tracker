package core

class AuthkeyExpiredException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class GachaServerConnectionException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class InvalidAuthkeyUrlException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class TooFrequentRequestException @JvmOverloads constructor(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
