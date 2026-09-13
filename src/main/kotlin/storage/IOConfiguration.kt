package storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.notExists

object IOConfiguration {
    private val userHome: String =
        System.getProperty("user.home")
            ?: System.getProperty("user.dir")
            ?: "."

    const val FILES_NAME: String = "GenshinAnalyzer"
    val default_STORAGEPath: Path = Path.of(userHome, FILES_NAME, "data")
    val default_ExportPath: Path = Path.of(userHome, FILES_NAME, "export")
    val default_configPath: Path = Path.of(userHome, FILES_NAME, "config")
    val default_databasePath: Path = default_STORAGEPath.resolve("gacha.db")

    fun initialize(): Result<Unit> = runCatching {
        listOf(default_STORAGEPath, default_ExportPath, default_configPath).forEach { path ->
            if (path.notExists()) {
                Files.createDirectories(path)
            }
        }
    }
}
