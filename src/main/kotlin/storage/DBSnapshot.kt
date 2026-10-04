package storage

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection

internal object DBSnapshot {
    fun write(connection: Connection, target: Path) {
        require(!Files.exists(target)) { "Backup destination already exists: $target" }
        // vacuum is efficient and won't interrupt current service
        connection.prepareStatement("VACUUM INTO ?").use { statement ->
            statement.setString(1, target.toAbsolutePath().toString())
            statement.execute()
        }
    }
}
