import analytics.calculateStat
import model.GachaRecord
import model.UserStatistics
import model.customizeJson
import storage.ImportInvalidatedException
import storage.RecordStore
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordStoreTest {
    private fun record(id: String) = GachaRecord("301", "2026-01-01 12:00:00", "Test", "weapon", "11301", id, 3)

    private fun database(block: (Connection, Connection) -> Unit) {
        val dir = createTempDirectory("record-store-")
        try {
            val url = "jdbc:sqlite:${dir.resolve("test.db")}"
            DriverManager.getConnection(url).use { first ->
                first.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
                RecordStore(first).initialize()
                DriverManager.getConnection(url).use { second ->
                    RecordStore(second).initialize()
                    block(first, second)
                }
            }
        } finally { dir.toFile().deleteRecursively() }
    }

    private fun saved(connection: Connection): Pair<List<GachaRecord>, UserStatistics>? =
        connection.createStatement().use { sql ->
            sql.executeQuery("SELECT raw_records_json, stats_json FROM gacha_storage WHERE uid='123'").use {
                if (!it.next()) null else customizeJson.decodeFromString<List<GachaRecord>>(it.getString(1)) to
                    customizeJson.decodeFromString<UserStatistics>(it.getString(2))
            }
        }

    private fun injectLegacy(connection: Connection, records: List<GachaRecord>) {
        connection.prepareStatement("UPDATE gacha_storage SET raw_records_json=? WHERE uid='123'").use {
            it.setString(1, customizeJson.encodeToString(records))
            it.executeUpdate()
        }
    }

    @Test
    fun `invalid incoming time rejects the entire batch without changing the database`() = database { a, _ ->
        val store = RecordStore(a)
        val ticket = store.beginImport()
        store.merge("123", listOf(record("1")), ticket)
        val before = saved(a)
        assertFailsWith<IllegalArgumentException> {
            store.merge("123", listOf(record("2"), record("3").copy(time = "2025-02-30 12:00:00")), ticket)
        }
        assertEquals(before, saved(a))
        val leap = record("4").copy(time = "2024-02-29 23:59:59")
        store.merge("123", listOf(leap), ticket)
        assertEquals(leap.time, saved(a)!!.first.first { it.recordID == "4" }.time)
    }

    @Test
    fun `maintenance removes legacy invalid dates before transform and recalculates statistics`() = database { a, _ ->
        val store = RecordStore(a)
        store.merge("123", listOf(record("1")), store.beginImport())
        val valid = record("2").copy(time = "2024-02-29 12:00:00")
        val bad = record("3").copy(time = "2025-04-31 12:00:00")
        injectLegacy(a, listOf(valid, bad))
        store.maintain("123") { records -> assertEquals(listOf(valid), records); records }
        assertEquals(listOf(valid), saved(a)!!.first)
        assertEquals(1, saved(a)!!.second.totalWishes)
        assertFailsWith<IllegalArgumentException> { store.maintain("123") { it + bad } }
        assertEquals(listOf(valid), saved(a)!!.first)
        injectLegacy(a, listOf(bad))
        store.maintain("123") { it }
        assertEquals(emptyList(), saved(a)!!.first)
        assertEquals(0, saved(a)!!.second.totalWishes)
    }

    @Test
    fun `initialization repairs legacy dates before records can reach analytics`() = database { a, _ ->
        val store = RecordStore(a)
        store.merge("123", listOf(record("1")), store.beginImport())
        injectLegacy(a, listOf(record("1"), record("2").copy(time = "2025-02-29 12:00:00")))
        store.initialize()
        assertEquals(listOf(record("1")), saved(a)!!.first)
        assertEquals(1, saved(a)!!.second.totalWishes)
    }

    @Test
    fun `overlapping independent connections merge against the latest committed records`() = database { a, b ->
        val initial = RecordStore(a)
        initial.merge("123", listOf(record("1")), initial.beginImport())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = RecordStore(a) { records ->
            entered.countDown()
            check(release.await(3, TimeUnit.SECONDS))
            records.calculateStat()
        }
        val second = RecordStore(b)
        val ticketA = first.beginImport()
        val ticketB = second.beginImport()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val one = pool.submit { first.merge("123", listOf(record("2")), ticketA) }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val two = pool.submit { second.merge("123", listOf(record("3")), ticketB) }
            release.countDown()
            one.get(5, TimeUnit.SECONDS)
            two.get(5, TimeUnit.SECONDS)
            val (records, stats) = saved(a)!!
            assertEquals(setOf("1", "2", "3"), records.map { it.recordID }.toSet())
            assertEquals(3, stats.totalWishes)
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test
    fun `delete invalidates older imports across connections but allows new imports`() = database { a, b ->
        val first = RecordStore(a)
        val second = RecordStore(b)
        val oldTicket = first.beginImport()
        first.merge("123", listOf(record("1")), oldTicket)
        second.delete("123")
        assertFailsWith<ImportInvalidatedException> { first.merge("123", listOf(record("2")), oldTicket) }
        assertNull(saved(a))
        assertNull(first.maintain("123") { error("Deleted accounts must not be maintained") })
        second.merge("123", listOf(record("3")), second.beginImport())
        assertFailsWith<ImportInvalidatedException> { first.merge("123", listOf(record("2")), oldTicket) }
        assertEquals(listOf("3"), saved(a)!!.first.map { it.recordID })
        first.merge("456", listOf(record("4")), oldTicket) // Other UIDs are unaffected.
    }

    @Test
    fun `failed maintenance rolls back and successful maintenance updates records and stats together`() = database { a, _ ->
        val store = RecordStore(a)
        store.merge("123", listOf(record("1")), store.beginImport())
        assertFailsWith<IllegalStateException> {
            store.maintain("123") { error("maintenance failed") }
        }
        assertEquals(1, saved(a)!!.second.totalWishes)
        store.maintain("123") { it + record("2") }
        assertEquals(2, saved(a)!!.first.size)
        assertEquals(2, saved(a)!!.second.totalWishes)
        val broken = RecordStore(a) { error("statistics failed") }
        assertFailsWith<IllegalStateException> { broken.merge("123", listOf(record("3")), store.beginImport()) }
        assertEquals(2, saved(a)!!.first.size)
    }
}
