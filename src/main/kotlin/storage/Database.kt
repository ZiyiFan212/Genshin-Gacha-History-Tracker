package storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import model.GachaRecord
import model.UserStatistics
import model.customizeJson
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

object Database {

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { dbClose() }
        })
    }

    private val dbMutex = Mutex()
    private var closed = false

    private val connectionDelegate = lazy {
        val dbPath = IOConfiguration.default_databasePath
        Files.createDirectories(dbPath.parent)
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").also { conn ->
            conn.createStatement().use { statement ->
                statement.execute("PRAGMA busy_timeout=5000;")
                statement.execute("PRAGMA journal_mode=WAL;")
                statement.execute("PRAGMA synchronous=NORMAL;")
                statement.execute("PRAGMA foreign_keys=ON;")

            }
            RecordStore(conn).initialize()
        }
    }
    private val connection: Connection by connectionDelegate

    private suspend fun <T> withDb(block: (Connection) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                dbMutex.withLock {
                    check(!closed) { "Database is closed" }
                    block(connection)
                }
            }
        }

    fun dbClose() = runBlocking {
        dbMutex.withLock {
            closed = true
            if (connectionDelegate.isInitialized() && !connection.isClosed) connection.close()
        }
    }

    /** SQLite builds a consistent standalone snapshot, including committed WAL pages. */
    suspend fun backupTo(target: Path): Result<Unit> = withDb { conn ->
        DBSnapshot.write(conn, target)
    }

    suspend fun search(uid: String): Result<Pair<List<GachaRecord>, UserStatistics>?> =
        withDb { conn ->
            val sql = "SELECT raw_records_json, stats_json FROM gacha_storage WHERE uid = ?"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, uid)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        val recordsJson = rs.getString("raw_records_json")
                        val statsJson = rs.getString("stats_json")
                        require(!recordsJson.isNullOrBlank()) { "Corrupted DB row: empty raw_records_json for uid=$uid" }
                        require(!statsJson.isNullOrBlank()) { "Corrupted DB row: empty stats_json for uid=$uid" }
                        Pair(
                            customizeJson.decodeFromString<List<GachaRecord>>(recordsJson),
                            customizeJson.decodeFromString<UserStatistics>(statsJson)
                        )
                    } else {
                        null
                    }
                }
            }
        }

    suspend fun beginImport(): Result<ImportTicket> = withDb { RecordStore(it).beginImport() }

    suspend fun mergeRecords(uid: String, incoming: List<GachaRecord>, ticket: ImportTicket): Result<MergeResult> =
        withDb { RecordStore(it).merge(uid, incoming, ticket) }

    suspend fun delete(uid: String): Result<Boolean> = withDb { RecordStore(it).delete(uid) }

    suspend fun listAllUids(): Result<List<String>> =
        withDb { conn ->
            val sql = "SELECT uid FROM gacha_storage"
            conn.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString("uid")) }
                }
            }
        }

    suspend fun ifExist(uid: String): Result<Boolean> =
        withDb { conn ->
            val sql = "SELECT 1 FROM gacha_storage WHERE uid = ? LIMIT 1"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, uid)
                statement.executeQuery().use { rs -> rs.next() }
            }
        }

    suspend fun updateStats(uid: String): Result<Pair<List<GachaRecord>, UserStatistics>?> =
        withDb { RecordStore(it).maintain(uid) { records -> records } }

    @Deprecated("Get record has another implementation.")
    suspend fun getRecord(uid: String): Result<List<GachaRecord>> =
        withDb { connection ->
            val sql = "SELECT raw_records_json FROM gacha_storage WHERE uid = ?"
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, uid)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        val recordsJson = resultSet.getString("raw_records_json")
                        require(!recordsJson.isNullOrBlank()) { "Corrupted DB row: empty raw_records_json for uid=$uid" }
                        customizeJson.decodeFromString<List<GachaRecord>>(recordsJson)
                    } else {
                        emptyList()
                    }
                }
            }
        }

    suspend fun updateRecords(
        uid: String,
        transform: (List<GachaRecord>) -> List<GachaRecord>,
    ): Result<Pair<List<GachaRecord>, UserStatistics>?> = withDb { RecordStore(it).maintain(uid, transform) }
}
