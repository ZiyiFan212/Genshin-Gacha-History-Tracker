import assets.I18nManager
import assets.IconManager
import assets.ItemTranslator
import kotlinx.coroutines.runBlocking
import storage.IOConfiguration
import storage.UigfExporter
import model.GachaRecord
import model.parseJson
import utilities.AppBootstrap
import utilities.PreferencesManager
import analytics.calculateStat
import utilities.mergeWith
import utilities.validate
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafetyTest {

    private val sampleUigfV3 = """
        {
          "info": { "uid": "123456789" },
          "list": [
            {
              "gacha_type": "301",
              "time": "2024-01-01 12:00:00",
              "name": "Jean",
              "item_type": "角色",
              "item_id": "10000003",
              "id": "1",
              "rank_type": "5"
            }
          ]
        }
    """.trimIndent()

    private val sampleUigfV4 = """
        {
          "info": { "uid": "987654321" },
          "records": [
            {
              "gacha_type": "302",
              "time": "2024-02-01 12:00:00",
              "name": "Skyward Spine",
              "item_type": "weapon",
              "item_id": "11502",
              "id": "2",
              "rank_type": 5
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parseJson supports v3 and v4 and rejects empty records`() {
        val v3 = parseJson(sampleUigfV3).getOrThrow()
        assertEquals("123456789", v3.first)
        assertEquals(1, v3.second.size)
        assertEquals("character", v3.second.first().itemType)

        val v4 = parseJson(sampleUigfV4).getOrThrow()
        assertEquals("987654321", v4.first)
        assertEquals(1, v4.second.size)

        val empty = parseJson("""{"info":{"uid":"1"},"list":[]}""")
        assertTrue(empty.isFailure)
    }

    @Test
    fun `calculateStat handles empty list safely`() {
        val stats = emptyList<GachaRecord>().calculateStat()
        assertEquals(0, stats.totalWishes)
        assertEquals(0.0, stats.winRate)
        assertEquals(0.0, stats.avgPity)
        assertEquals(0.0, stats.avgPityLim)
    }

    @Test
    fun `mergeWith deduplicates blank record ids by composite key`() {
        val a = GachaRecord("301", "2024-01-01 12:00:00", "A", "character", "1", "", 5)
        val b = GachaRecord("301", "2024-01-02 12:00:00", "B", "character", "2", "", 4)
        val merged = listOf(a).mergeWith(listOf(b))
        assertEquals(2, merged.size)
    }

    @Test
    fun `bootstrap initializes all singletons`() = runBlocking {
        val result = AppBootstrap.initialize("en")
        assertTrue(result.isSuccess)
        assertTrue(AppBootstrap.isInitialized())
        assertTrue(I18nManager.isLoaded())
        assertTrue(ItemTranslator.isLoaded())
        assertTrue(IconManager.isInitialized())
    }

    @Test
    fun `UigfExporter writes file into directory`() {
        val tempExport = createTempDirectory("export-test")
        val records = parseJson(sampleUigfV3).getOrThrow()
        val result = UigfExporter.export(records, "v3.0", tempExport)
        assertTrue(result.isSuccess)
        val output = result.getOrThrow()
        assertTrue(Files.exists(output))
        assertTrue(Files.size(output) > 0)
    }

    @Test
    fun `UigfExporter rejects invalid version`() {
        val records = parseJson(sampleUigfV3).getOrThrow()
        val result = UigfExporter.export(records, "v9.9", createTempDirectory("bad-version"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `path validate rejects empty json file`() {
        val tempDir = createTempDirectory("validate-test")
        val emptyJson = tempDir.resolve("empty.json")
        emptyJson.writeText("")
        val result = emptyJson.validate()
        assertTrue(result.isFailure)
    }

    @Test
    fun `preferences save works without prior load in memory`() = runBlocking {
        IOConfiguration.initialize().getOrThrow()
        val result = PreferencesManager.saveUserPreferences(false, "zh")
        assertTrue(result.isSuccess)
        assertEquals("zh", PreferencesManager.getLanguageOrDefault())
    }

    @Test
    fun `i18n sheetName sanitizes illegal excel characters`() = runBlocking {
        AppBootstrap.initialize("en").getOrThrow()
        val sheetName = I18nManager.sheetName("banner.301")
        assertFalse(sheetName.contains(":"))
        assertTrue(sheetName.length <= 31)
    }
}
