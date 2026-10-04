import storage.DBSnapshot
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseSnapshotTest {
    @Test
    fun `snapshot includes committed WAL data and excludes uncommitted changes`() {
        val dir = createTempDirectory("wal-backup-")
        try {
            val source = dir.resolve("source.db")
            val target = dir.resolve("snapshot.db")
            DriverManager.getConnection("jdbc:sqlite:$source").use { conn ->
                conn.createStatement().use { sql ->
                    sql.execute("PRAGMA journal_mode=WAL")
                    sql.execute("PRAGMA wal_autocheckpoint=0")
                    sql.execute("CREATE TABLE records (id INTEGER PRIMARY KEY)")
                    sql.execute("PRAGMA wal_checkpoint(TRUNCATE)")
                    sql.execute("INSERT INTO records VALUES (1)")
                }
                assertTrue(Files.size(dir.resolve("source.db-wal")) > 0)
                DriverManager.getConnection("jdbc:sqlite:$source").use { writer ->
                    writer.autoCommit = false
                    writer.createStatement().use { it.execute("INSERT INTO records VALUES (2)") }
                    DBSnapshot.write(conn, target)
                    writer.rollback()
                }
                DriverManager.getConnection("jdbc:sqlite:$target").use { copy ->
                    copy.createStatement().use { sql ->
                        sql.executeQuery("SELECT COUNT(*) FROM records").use {
                            it.next()
                            assertEquals(1, it.getInt(1))
                        }
                        sql.executeQuery("PRAGMA integrity_check").use {
                            it.next()
                            assertEquals("ok", it.getString(1))
                        }
                    }
                }
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
