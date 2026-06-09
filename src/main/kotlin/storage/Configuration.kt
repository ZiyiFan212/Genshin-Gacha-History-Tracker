package storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.notExists

object Configuration {
    val first: String = System.getProperty("user.home")
    const val FILES_NAME: String = "GenshinAnalyzer"
    val default_STORAGEPath: Path = Path.of(first, FILES_NAME, "data")
    val default_ExportPath: Path = Path.of(first, FILES_NAME, "export")
    val default_configPath: Path = Path.of(first, FILES_NAME, "config")

    fun initialize(){
        if (default_STORAGEPath.notExists()){
            Files.createDirectories(default_STORAGEPath)
        }
        if (default_ExportPath.notExists()){
            Files.createDirectories(default_ExportPath)
        }
        if (default_configPath.notExists()){
            Files.createDirectories(default_configPath)
        }
    }
}