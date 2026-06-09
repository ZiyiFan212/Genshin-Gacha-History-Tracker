package utilities

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isReadable

val Path.isValidJson: Boolean
    get() = this.extension.lowercase() == "json"

val Path.isRegularFile: Boolean
    get() = Files.isRegularFile(this)

val Path.isNotEmpty: Boolean
    get() = Files.size(this) > 0L


// validate and return the path/error message
fun Path.validate(): Result<Path> {
    return runCatching {
        require(exists()) { "File does not exist: $this" }
        require(isReadable()) { "File is not readable: $this" }
        require(isRegularFile) { "File is not a regular file）: $this" }
        require(isValidJson) { "File is not JSON file: $this" }
        require(isNotEmpty) { "File is empty: $this" }
        this
    }
}

// find valid JSON in a given directory path
fun Path.findFirstJson(): Path? {
    if (this.isRegularFile && this.extension.lowercase() == "json") return this

    return if (Files.isDirectory(this)) {
        runCatching {
            Files.list(this).use { stream -> stream
                    .filter { it.extension.lowercase() == "json" }
                    .findFirst()
                    .orElse(null)
            }
        }.getOrNull()
    } else null
}