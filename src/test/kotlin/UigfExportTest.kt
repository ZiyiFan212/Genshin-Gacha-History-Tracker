import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import model.GachaRecord
import model.parseJson
import storage.UigfExporter
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UigfExportTest {
    @Test
    fun `v41 exports nested account with explicit locale and timezone and round trips`() {
        val directory = createTempDirectory("uigf-v41-")
        try {
            val source = record("400")
            val path = UigfExporter.export("600000001" to listOf(source), "v4.1", directory, "zh-cn", 8).getOrThrow()
            val root = Json.parseToJsonElement(path.readText()).jsonObject
            assertEquals(setOf("info", "hk4e"), root.keys)
            val info = root.getValue("info").jsonObject
            assertEquals(setOf("export_timestamp", "export_app", "export_app_version", "version"), info.keys)
            assertEquals("v4.1", info.getValue("version").jsonPrimitive.content)
            assertFalse(info.getValue("export_timestamp").jsonPrimitive.isString)
            val account = root.getValue("hk4e").jsonArray.single().jsonObject
            assertEquals(setOf("uid", "timezone", "lang", "list"), account.keys)
            assertEquals("600000001", account.getValue("uid").jsonPrimitive.content)
            assertEquals("zh-cn", account.getValue("lang").jsonPrimitive.content)
            assertFalse(account.getValue("timezone").jsonPrimitive.isString)
            assertEquals(8, account.getValue("timezone").jsonPrimitive.int)
            val item = account.getValue("list").jsonArray.single().jsonObject
            assertEquals(setOf("uigf_gacha_type", "gacha_type", "item_id", "time", "name", "item_type", "rank_type", "id"), item.keys)
            item.values.forEach { assertTrue(it.jsonPrimitive.isString) }
            assertEquals("400", item.getValue("gacha_type").jsonPrimitive.content)
            assertEquals("301", item.getValue("uigf_gacha_type").jsonPrimitive.content)
            assertEquals("4", item.getValue("rank_type").jsonPrimitive.content)
            assertEquals("武器", item.getValue("item_type").jsonPrimitive.content)
            assertEquals("600000001" to listOf(source), parseJson(path.readText()).getOrThrow())
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun `v41 imports reference record and rejects ambiguous or empty accounts`() {
        val account = """{"uid":"184623965","timezone":8,"lang":"zh-cn","list":[{
            "uigf_gacha_type":"200","gacha_type":"200","item_id":"14401",
            "time":"2025-09-10 11:50:03","name":"西风秘典","item_type":"武器",
            "rank_type":"4","id":"1757474400022964365"}]}"""
        val info = """"info":{"export_timestamp":1789316621,"export_app":"genshin-wish-export","export_app_version":"v0.12.1","version":"v4.1"}"""
        assertEquals("184623965" to listOf(record()), parseJson("{$info,\"hk4e\":[$account]}").getOrThrow())
        assertTrue(parseJson("{$info,\"hk4e\":[$account,$account]}").isFailure)
        assertTrue(parseJson("{$info,\"hk4e\":[]}").isFailure)
        assertTrue(parseJson("{$info,\"hk4e\":[{\"uid\":\"184623965\",\"list\":[]}]}").isFailure)
    }

    private fun record(pool: String = "200", id: String = "1757474400022964365") = GachaRecord(
        gachaType = pool, time = "2025-09-10 11:50:03", name = "西风秘典",
        itemType = "weapon", itemID = "14401", recordID = id, rankType = 4,
    )

    @Test
    fun `v3 includes metadata and string fields and round trips`() {
        val directory = createTempDirectory("uigf-v3-")
        try {
            val source = record()
            val path = UigfExporter.export("184623965" to listOf(source), "v3.0", directory, "zh-cn").getOrThrow()
            assertFalse(path.fileName.toString().any { it in "<>:\"/\\|?*" })
            val root = Json.parseToJsonElement(path.readText()).jsonObject
            assertEquals(setOf("info", "list"), root.keys)
            val info = root.getValue("info").jsonObject
            assertEquals(setOf("uid", "lang", "export_time", "export_timestamp", "export_app", "export_app_version", "uigf_version", "region_time_zone"), info.keys)
            assertEquals("zh-cn", info.getValue("lang").jsonPrimitive.content)
            assertEquals("v3.0", info.getValue("uigf_version").jsonPrimitive.content)
            assertEquals("Genshin-Analyzer-NEXT", info.getValue("export_app").jsonPrimitive.content)
            assertFalse(info.getValue("region_time_zone").jsonPrimitive.isString)
            assertEquals(8, info.getValue("region_time_zone").jsonPrimitive.int)
            assertFalse(info.getValue("export_timestamp").jsonPrimitive.isString)
            val date = LocalDateTime.parse(info.getValue("export_time").jsonPrimitive.content, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            assertEquals(info.getValue("export_timestamp").jsonPrimitive.long, date.toEpochSecond(ZoneOffset.ofHours(8)))
            val item = root.getValue("list").jsonArray.single().jsonObject
            for (field in listOf("gacha_type", "uigf_gacha_type", "time", "name", "item_type", "item_id", "rank_type", "id")) {
                assertTrue(item.getValue(field).jsonPrimitive.isString, "$field must be a JSON string")
            }
            assertFalse("uidf_gacha_type" in item)
            assertEquals("200", item.getValue("uigf_gacha_type").jsonPrimitive.content)
            assertEquals("4", item.getValue("rank_type").jsonPrimitive.content)
            assertEquals("武器", item.getValue("item_type").jsonPrimitive.content)
            assertEquals(source.name, item.getValue("name").jsonPrimitive.content)
            assertEquals(source.time, item.getValue("time").jsonPrimitive.content)
            assertEquals("184623965" to listOf(source), parseJson(path.readText()).getOrThrow())
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun `all pools emit UIGF type and event two maps to shared pool`() {
        val directory = createTempDirectory("uigf-pools-")
        try {
            val pools = listOf("100", "200", "301", "400", "302", "500")
            val records = pools.mapIndexed { index, pool -> record(pool, (index + 1).toString()) }
            // Export must correct even an explicitly supplied unmerged value from older data.
            val path = UigfExporter.export("259231502" to records.map { it.copy(uigfGachaType = it.gachaType) }, "v3.0", directory, "en-us").getOrThrow()
            val items = Json.parseToJsonElement(path.readText()).jsonObject.getValue("list").jsonArray
            assertEquals(pools, items.map { it.jsonObject.getValue("gacha_type").jsonPrimitive.content })
            assertEquals(listOf("100", "200", "301", "301", "302", "500"), items.map { it.jsonObject.getValue("uigf_gacha_type").jsonPrimitive.content })
            assertEquals("Favonius Codex", items.first().jsonObject.getValue("name").jsonPrimitive.content)
            assertEquals("Weapon", items.first().jsonObject.getValue("item_type").jsonPrimitive.content)
            assertEquals("301", record("400").uigfGachaType)
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun `timezone uses server UID mapping with explicit override`() {
        val directory = createTempDirectory("uigf-zones-")
        try {
            for ((uid, expected) in mapOf("600000001" to -5, "700000001" to 1, "800000001" to 8, "259231502" to 8)) {
                val path = UigfExporter.export(uid to listOf(record()), "v3.0", directory).getOrThrow()
                val info = Json.parseToJsonElement(path.readText()).jsonObject.getValue("info").jsonObject
                assertEquals(expected, info.getValue("region_time_zone").jsonPrimitive.int)
            }
            val path = UigfExporter.export("600000001" to listOf(record()), "v3.0", directory, regionTimeZone = 8).getOrThrow()
            assertEquals(8, Json.parseToJsonElement(path.readText()).jsonObject.getValue("info").jsonObject.getValue("region_time_zone").jsonPrimitive.int)
        } finally { directory.toFile().deleteRecursively() }
    }
}
