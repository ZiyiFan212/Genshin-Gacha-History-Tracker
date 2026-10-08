import model.GachaRecord
import storage.CsvExporter
import storage.HtmlExporter
import storage.UigfExporter
import validation.DataValidator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportPathTest {
    private val records = listOf(GachaRecord("301", "2025-01-01 12:00:00", "Test", "weapon", "11301", "1", 3))
    private val exporters: List<(String, Path) -> Result<Path>> = listOf(
        { uid, path -> CsvExporter.export(records, uid, path) },
        { uid, path -> HtmlExporter.export(records, uid, path) },
        { uid, path -> UigfExporter.export(uid to records, "v3.0", path) },
        { uid, path -> UigfExporter.export(uid to records, "v4.1", path) },
    )

    @Test
    fun `invalid UIDs are rejected before creating or overwriting files`() {
        val root = createTempDirectory("export-invalid-")
        try {
            val directory = Files.createDirectory(root.resolve("export"))
            val explicit = root.resolve("existing.txt")
            Files.writeString(explicit, "keep")
            val invalid = listOf("", " ", "12345678", "12345678901", "12345678a",
                "x/../../audit-out", "x\\..\\..\\audit-out", "123:456789", "123456789 ",
                " 123456789", "123456789\n", "１２３４５６７８９") +
                "<>:\"/\\|?*".map { "1234${it}6789" }
            for (uid in invalid) {
                assertTrue(DataValidator.validate(records, uid).hasErrors, uid)
                for (export in exporters) {
                    for (path in listOf(directory, explicit)) {
                        val result = export(uid, path)
                        assertTrue(result.exceptionOrNull() is IllegalArgumentException, uid)
                    }
                }
            }
            Files.list(directory).use { assertEquals(0L, it.count()) }
            Files.list(root).use { assertEquals(2L, it.count()) }
            assertEquals("keep", Files.readString(explicit))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun `valid UIDs preserve generated names and explicit output paths`() {
        assets.ItemTranslator.load().getOrThrow()
        val root = createTempDirectory("export-valid-")
        try {
            for (uid in listOf("123456789", "1234567890")) {
                assertFalse(DataValidator.validate(records, uid).hasErrors)
                exporters.forEachIndexed { index, export ->
                    val file = export(uid, root.resolve("unused").resolve("..")).getOrThrow()
                    assertEquals(root.toAbsolutePath().normalize(), file.parent)
                    val pattern = when (index) {
                        0 -> "Gacha_${uid}_[0-9]+\\.csv"
                        1 -> "Gacha_${uid}_[0-9]+\\.html"
                        2 -> "UIGF_v3\\.0_${uid}_[0-9]{12}\\.json"
                        else -> "UIGF_v4\\.1_${uid}_[0-9]{12}\\.json"
                    }
                    assertTrue(file.fileName.toString().matches(Regex(pattern)))
                    assertTrue(Files.isRegularFile(file))
                    val explicit = root.resolve("nested/custom-$uid-$index.txt")
                    assertEquals(explicit.toAbsolutePath().normalize(), export(uid, explicit).getOrThrow())
                    assertTrue(Files.isRegularFile(explicit))
                }
            }
        } finally { root.toFile().deleteRecursively() }
    }
}
