package backup

import logger.LogBody
import logger.LogLevel
import logger.LogWriter
import storage.IOConfiguration
import storage.Database
import java.nio.file.Files
import java.util.UUID
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

object BackupManager {
    private val backupDir = IOConfiguration.default_STORAGEPath.resolve("backups")

    suspend fun backupBeforeImport(): Result<java.nio.file.Path> = runCatching {
        val db = IOConfiguration.default_databasePath
        if (!Files.exists(db)) {
            LogWriter.instance.tryLog(LogBody(LogLevel.INFO, "No database to backup yet"))
            return@runCatching db
        }
        Files.createDirectories(backupDir)
        val target = backupDir.resolve("gacha_${Instant.now().epochSecond}_${UUID.randomUUID()}.db")
        val staging = target.resolveSibling("${target.fileName}.partial")
        try {
            Database.backupTo(staging).getOrThrow()
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.deleteIfExists(staging)
            throw e
        }
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
