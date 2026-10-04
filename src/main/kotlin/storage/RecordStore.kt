package storage

import analytics.calculateStat
import model.GachaRecord
import model.UserStatistics
import model.customizeJson
import utilities.mergeWith
import utilities.isValidTime
import utilities.requireValidRecordTimes
import logger.LogBody
import logger.LogLevel
import logger.LogWriter
import java.sql.Connection

class ImportTicket internal constructor(internal val deletionVersion: Long)
data class MergeResult(val records: List<GachaRecord>, val stats: UserStatistics, val newCount: Int)
class ImportInvalidatedException : IllegalStateException("Account was deleted during this operation. Start a new import or capture to recreate it.")

/** Call under the connection's mutex. BEGIN IMMEDIATE also serializes other app instances. */
internal class RecordStore(
    private val connection: Connection,
    private val statistics: (List<GachaRecord>) -> UserStatistics = { it.calculateStat() },
) {
    fun initialize() {
        connection.createStatement().use { sql ->
            sql.execute("PRAGMA busy_timeout=5000")
            sql.execute("CREATE TABLE IF NOT EXISTS gacha_storage (uid TEXT PRIMARY KEY, raw_records_json TEXT NOT NULL, stats_json TEXT NOT NULL)")
            // Keep deletion events across reimports: older tickets must stay invalid even after recreation.
            sql.execute("CREATE TABLE IF NOT EXISTS account_deletions (generation INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL)")
            sql.execute("CREATE INDEX IF NOT EXISTS account_deletions_uid_generation ON account_deletions(uid, generation)")
        }
        repairStoredTimes()
    }

    /** Scan before any UI analytics can read legacy dates. Only changed accounts are rewritten. */
    private fun repairStoredTimes() {
        val removed = transaction {
            val users = connection.createStatement().use { sql ->
                sql.executeQuery("SELECT uid FROM gacha_storage").use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
            users.mapNotNull { uid ->
                val previous = readRecords(uid) ?: return@mapNotNull null
                val clean = previous.filter { it.time.isValidTime() }
                if (clean.size == previous.size) return@mapNotNull null
                write(uid, clean, statistics(clean))
                uid to (previous.size - clean.size)
            }
        }
        removed.forEach { (uid, count) -> logRemoved(uid, count) }
    }

    private fun logRemoved(uid: String, count: Int) {
        if (count > 0) LogWriter.instance.tryLog(LogBody(LogLevel.WARN, "Date maintenance: removed $count invalid record(s) for uid=$uid"))
    }

    fun beginImport(): ImportTicket = connection.createStatement().use { sql ->
        sql.executeQuery("SELECT COALESCE(MAX(generation), 0) FROM account_deletions").use {
            it.next()
            ImportTicket(it.getLong(1))
        }
    }

    fun merge(uid: String, incoming: List<GachaRecord>, ticket: ImportTicket): MergeResult = transaction {
        require(uid.isNotBlank()) { "UID is empty" }
        requireValidRecordTimes(incoming)
        connection.prepareStatement("SELECT 1 FROM account_deletions WHERE uid = ? AND generation > ? LIMIT 1").use { sql ->
            sql.setString(1, uid)
            sql.setLong(2, ticket.deletionVersion)
            sql.executeQuery().use { if (it.next()) throw ImportInvalidatedException() }
        }
        val previous = readRecords(uid) ?: emptyList()
        val merged = incoming.mergeWith(previous)
        val stats = statistics(merged)
        write(uid, merged, stats)
        MergeResult(merged, stats, (merged.size - previous.size).coerceAtLeast(0))
    }

    fun maintain(uid: String, transform: (List<GachaRecord>) -> List<GachaRecord>): Pair<List<GachaRecord>, UserStatistics>? {
        var removed = 0
        val result = transaction {
            val previous = readRecords(uid) ?: return@transaction null
            val clean = previous.filter { it.time.isValidTime() }
            removed = previous.size - clean.size
            val records = transform(clean)
            requireValidRecordTimes(records)
            val stats = statistics(records)
            write(uid, records, stats)
            records to stats
        }
        logRemoved(uid, removed)
        return result
    }

    fun delete(uid: String): Boolean = transaction {
        connection.prepareStatement("INSERT INTO account_deletions(uid) VALUES (?)").use {
            it.setString(1, uid)
            it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM gacha_storage WHERE uid = ?").use {
            it.setString(1, uid)
            it.executeUpdate() > 0
        }
    }

    private fun readRecords(uid: String): List<GachaRecord>? =
        connection.prepareStatement("SELECT raw_records_json FROM gacha_storage WHERE uid = ?").use { sql ->
            sql.setString(1, uid)
            sql.executeQuery().use { rows ->
                if (!rows.next()) null
                else customizeJson.decodeFromString<List<GachaRecord>>(rows.getString(1))
            }
        }

    private fun write(uid: String, records: List<GachaRecord>, stats: UserStatistics) {
        connection.prepareStatement("""
            INSERT INTO gacha_storage(uid, raw_records_json, stats_json) VALUES (?, ?, ?)
            ON CONFLICT(uid) DO UPDATE SET raw_records_json=excluded.raw_records_json, stats_json=excluded.stats_json
        """.trimIndent()).use {
            it.setString(1, uid)
            it.setString(2, customizeJson.encodeToString(records))
            it.setString(3, customizeJson.encodeToString(stats))
            it.executeUpdate()
        }
    }

    private fun <T> transaction(block: () -> T): T {
        check(connection.autoCommit) { "RecordStore owns its transaction" }
        connection.createStatement().use { it.execute("BEGIN IMMEDIATE") }
        try {
            val result = block()
            connection.createStatement().use { it.execute("COMMIT") }
            return result
        } catch (error: Throwable) {
            try { connection.createStatement().use { it.execute("ROLLBACK") } }
            catch (rollback: Throwable) { error.addSuppressed(rollback) }
            throw error
        }
    }
}
