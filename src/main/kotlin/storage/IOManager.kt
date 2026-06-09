package storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import template.GachaRecord
import template.PlayerStats
import template.customizeJson
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import kotlin.io.path.writeText

object IOManager {

    // Serialize to JSON
    @Serializable
    data class UIGFExportInfo(
        val uid: String,
        val lang: String = "en",
        @SerialName("export_timestamp") val exportTimestamp: Long = Instant.now().epochSecond,
        @SerialName("export_app") val exportApp: String = "Genshin-Analyzer-NEXT",
        @SerialName("export_app_version") val exportAppVersion: String = "v2.0",
        @SerialName("uigf_version") val uigfVersion: String
    )
    private val json = Json { prettyPrint = true }

    /**
     * Export UIGF v3.0/4.0 JSON file.
     * @param pair The user record <K->UID, V->List of record>
     * @param path The user selecting path (default: [Configuration.default_ExportPath])
     * @param version Version "v3.0" or "v4.0"
     */
    fun exportUIGF(pair: Pair<String, List<GachaRecord>>, version: String, path: Path = Configuration.default_ExportPath) {

        val infoObj = json.encodeToJsonElement(UIGFExportInfo(uid = pair.first, uigfVersion = version))
        val recordsArr = json.encodeToJsonElement(pair.second)
        val key = if (version.contains("3.0")) "list" else "records"
        val finalJsonMap = buildJsonObject {
            put("info", infoObj)
            put(key, recordsArr)
        }

        val jsonStr = json.encodeToString(JsonObject.serializer(), finalJsonMap)
        path.writeText(jsonStr)
    }


    // SQLite local storage
    private val connection: Connection by lazy {
        DriverManager.getConnection(Configuration.default_STORAGEPath.toString()).also { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL;")
                statement.execute("PRAGMA synchronous=NORMAL;")
                statement.execute("PRAGMA foreign_keys=ON;")
            }
        }
    }
    init {
        connection.createStatement().use { statement ->
            statement.execute("""
                CREATE TABLE IF NOT EXISTS gacha_storage (
                    uid                 TEXT            PRIMARY KEY,
                    raw_records_json    TEXT            NOT NULL,
                    stats_json          TEXT            NOT NULL
                );
            """.trimIndent())
        }
    }

    private suspend fun <T> query(block: (Connection) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { block(connection) } }

    private suspend fun <T> dbTransaction(block: (Connection) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
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

    fun close() {
        if (!connection.isClosed) connection.close()
    }// Called when program exists

    suspend fun searchingFromSQL(uid: String): Result<Pair<List<GachaRecord>, PlayerStats>?> =
        query { connection ->
            val sql = "SELECT raw_records_json, stats_json FROM gacha_storage WHERE uid = ?"
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, uid)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) Pair(
                        customizeJson.decodeFromString<List<GachaRecord>>(rs.getString("raw_records_json")),
                        customizeJson.decodeFromString<PlayerStats>(rs.getString("stats_json"))
                    ) else null
                }
            }
        }

    suspend fun upsertingInSQL(pair: Pair<String, List<GachaRecord>>, stats: PlayerStats): Result<Int> =
        query { connection ->
            val sql = "INSERT OR REPLACE INTO gacha_storage (uid, raw_records_json, stats_json) VALUES (?, ?, ?)"
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, pair.first)
                stmt.setString(2, customizeJson.encodeToString(pair.second))
                stmt.setString(3, json.encodeToString(stats))
                stmt.executeUpdate()
            }
        }

    suspend fun deleteFromSQL(uid: String): Result<Boolean> =
        query { connection ->
            val sql = "DELETE FROM gacha_storage WHERE uid = ?"
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, uid)
                stmt.executeUpdate() > 0
            }
        }

    suspend fun listAllUids(): Result<List<String>> =
        query { connection ->
            val sql = "SELECT uid FROM gacha_storage"
            connection.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString("uid")) }
                }
            }
        }

    suspend fun existsInSQL(uid: String): Result<Boolean> =
        query { connection ->
            val sql = "SELECT 1 FROM gacha_storage WHERE uid = ? LIMIT 1"
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, uid)
                stmt.executeQuery().use { rs -> rs.next() }
            }
        }
}
