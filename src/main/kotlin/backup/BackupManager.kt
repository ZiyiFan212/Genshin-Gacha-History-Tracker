package backup

import utilities.LogBody
import utilities.LogLevel
import utilities.LogWriter
import storage.IOConfiguration
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

object BackupManager {
    private val backupDir = IOConfiguration.default_STORAGEPath.resolve("backups")

    fun backupBeforeImport(): Result<java.nio.file.Path> = runCatching {
        val db = IOConfiguration.default_databasePath
        if (!Files.exists(db)) {
            LogWriter.instance.tryLog(LogBody(LogLevel.INFO, "No database to backup yet"))
            return@runCatching db
        }
        Files.createDirectories(backupDir)
        val target = backupDir.resolve("gacha_${Instant.now().epochSecond}.db")
        Files.copy(db, target, StandardCopyOption.REPLACE_EXISTING)
        LogWriter.instance.tryLog(LogBody(LogLevel.INFO, "Database backed up to $target"))
        target
    }

    fun listBackups(): Result<List<java.nio.file.Path>> = runCatching {
        if (!Files.exists(backupDir)) return@runCatching emptyList()
        backupDir.listDirectoryEntries("gacha_*.db").sortedByDescending { it.name }
    }

    /**
    fun restoreBackup(backupPath: java.nio.file.Path): Result<Unit> = runCatching {
        require(Files.exists(backupPath)) { "Backup not found: $backupPath" }
        Files.copy(backupPath, Configuration.default_databasePath, StandardCopyOption.REPLACE_EXISTING)
        LogWriter.instance.tryLog(LogBody(LogLevel.INFO, "Database restored from $backupPath"))
    }
    */
}
