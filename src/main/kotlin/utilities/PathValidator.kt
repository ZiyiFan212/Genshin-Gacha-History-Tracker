package utilities

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile

val Path.isValidJson: Boolean
    get() = this.extension.lowercase() == "json"

val Path.isNotEmpty: Boolean
    get() = Files.size(this) > 0L

fun Path.validate(): Result<Path> {
    return runCatching {
        require(exists()) { "File does not exist: $this" }
        require(isReadable()) { "File is not readable: $this" }
        require(isRegularFile()) { "File is not a regular file: $this" }
        require(isValidJson) { "File is not JSON file: $this" }
        require(isNotEmpty) { "File is empty: $this" }
        this
    }
}

fun Path.findFirstJson(): Path? = findLatestJson().getOrNull()

fun Path.findLatestJson(): Result<Path> = runCatching {
    if (isRegularFile() && extension.lowercase() == "json") {
        validate().getOrThrow()
        return@runCatching this
    }

    require(Files.isDirectory(this)) { "Path is not a JSON file or directory: $this" }

    Files.list(this).use { stream ->
        stream
            .filter { it.isRegularFile() && it.extension.lowercase() == "json" }
            .max(Comparator.comparingLong { path -> Files.getLastModifiedTime(path).toMillis() })
            .orElseThrow { IllegalArgumentException("No JSON file found in directory: $this") }
            .also { it.validate().getOrThrow() }
    }
}