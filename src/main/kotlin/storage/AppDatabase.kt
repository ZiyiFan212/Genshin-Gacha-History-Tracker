package storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import model.GachaRecord
import model.UserStatistics
import model.customizeJson
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

object AppDatabase {

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { dbClose() }
        })
    }

    private val json = Json { prettyPrint = true }
    private val dbMutex = Mutex()

    private val connection: Connection by lazy {
        val dbPath = IOConfiguration.default_databasePath
        Files.createDirectories(dbPath.parent)
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").also { conn ->
            conn.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL;")
                statement.execute("PRAGMA synchronous=NORMAL;")
                statement.execute("PRAGMA foreign_keys=ON;")
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS gacha_storage (
                        uid                 TEXT            PRIMARY KEY,
                        raw_records_json    TEXT            NOT NULL,
                        stats_json          TEXT            NOT NULL
                    );
                    """.trimIndent()
                )
            }
        }
    }

    private suspend fun <T> withDb(block: (Connection) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                dbMutex.withLock { block(connection) }
            }
        }

    private suspend fun <T> dbTransaction(block: (Connection) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                dbMutex.withLock {
                    connection.autoCommit = false
                    try {
                        val result = block(connection)
                        connection.commit()
                        result
                    } catch (e: Exception) {
                        connection.rollback()
                        throw e
                    } finally {
                        connection.autoCommit = true
                    }
                }
            }
        }

    fun dbClose() {
        if (!connection.isClosed) connection.close()
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

    suspend fun upsert(pair: Pair<String, List<GachaRecord>>, stats: UserStatistics): Result<Int> =
        dbTransaction { conn ->
            val sql = "INSERT OR REPLACE INTO gacha_storage (uid, raw_records_json, stats_json) VALUES (?, ?, ?)"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, pair.first)
                statement.setString(2, customizeJson.encodeToString(pair.second))
                statement.setString(3, json.encodeToString(stats))
                statement.executeUpdate()
            }
        }

    suspend fun delete(uid: String): Result<Boolean> =
        dbTransaction { conn ->
            val sql = "DELETE FROM gacha_storage WHERE uid = ?"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, uid)
                statement.executeUpdate() > 0
            }
        }

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

    suspend fun updateCurrentUser(uid: String, newStat: UserStatistics): Result<Int> =
        dbTransaction { conn ->
            val sql = "UPDATE gacha_storage SET stats_json = ? WHERE uid = ?"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, json.encodeToString(newStat))
                statement.setString(2, uid)
                statement.executeUpdate()
            }
        }

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

    suspend fun updateRecords(uid: String, records: List<GachaRecord>): Result<Int> =
        dbTransaction { conn ->
            val sql = "UPDATE gacha_storage SET raw_records_json = ? WHERE uid = ?"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, customizeJson.encodeToString(records))
                statement.setString(2, uid)
                statement.executeUpdate()
            }
        }

    suspend fun getLastEndID(uid: String): Result<String> =
        withDb { conn ->
            val sql = "SELECT MAX(record_id) as max_id FROM gacha_records WHERE uid = ?"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, uid)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getString("max_id") ?: ""
                    } else {
                        ""
                    }
                }
            }
        }

}