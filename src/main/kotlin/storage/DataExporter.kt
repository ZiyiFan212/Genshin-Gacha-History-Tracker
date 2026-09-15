package storage

import assets.I18nManager
import assets.ItemTranslator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import model.GachaRecord
import model.sortedChronologicallyDescending
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

sealed interface GachaExporter {
    fun export(records: List<GachaRecord>, uid: String, path: Path = IOConfiguration.default_ExportPath, ignoreThreeStar: Boolean = false): Result<Path>
    val fileExtension: String
}

object CsvExporter : GachaExporter {

    override val fileExtension: String = "csv"

    override fun export(records: List<GachaRecord>, uid: String, path: Path, ignoreThreeStar: Boolean): Result<Path> =
        runCatching {
            val filtered = if (ignoreThreeStar) records.filter { it.rankType >= 4 } else records
            val header = listOf(
                I18nManager["export.csv_time"],
                I18nManager["export.csv_name"],
                I18nManager["export.csv_item_id"],
                I18nManager["export.csv_item_type"],
                I18nManager["export.csv_banner"],
                I18nManager["export.csv_rarity"],
                I18nManager["export.csv_localized"],
            ).joinToString(",")

            val rows = filtered.sortedChronologicallyDescending().joinToString("\n") { record ->
                val name = ItemTranslator[record.itemID]
                listOf(
                    record.time,
                    record.name,
                    record.itemID,
                    record.itemType,
                    record.gachaType,
                    record.rankType,
                    name,
                ).joinToString(",") { field -> "\"${field.toString().replace("\"", "\"\"")}\"" }
            }
            val file = resolveFile(path, uid, fileExtension)
            Files.createDirectories(file.parent)
            file.toFile().writeText("$header\n$rows")
            file
        }
}

object HtmlExporter : GachaExporter {

    override val fileExtension: String = "html"

    override fun export(records: List<GachaRecord>, uid: String, path: Path, ignoreThreeStar: Boolean): Result<Path> =
        runCatching {
            val filtered = if (ignoreThreeStar) records.filter { it.rankType >= 4 } else records
            val sorted = filtered.sortedChronologicallyDescending()

            val total = sorted.size
            val fiveStarCount = sorted.count { it.rankType == 5 }
            val fourStarCount = sorted.count { it.rankType == 4 }
            val threeStarCount = sorted.count { it.rankType == 3 }

            // Map 301+400 → "301" for merged character pool display
            val merged: List<Pair<String, GachaRecord>> = sorted.map { record ->
                val code = if (record.gachaType == "400") "301" else record.gachaType
                code to record
            }

            val bannerStats = merged.groupBy({ it.first }, { it.second })
                .mapValues { (_, items) ->
                    Triple(items.size, items.count { it.rankType == 5 }, items.count { it.rankType == 4 })
                }

            val bannerTableRows = bannerStats.entries.sortedBy { it.key }.joinToString("\n") { (code, stats) ->
                val bannerName = I18nManager["banner.$code"]
                """<tr data-banner="$code"><td>$bannerName</td><td>${stats.first}</td><td>${stats.second}</td><td>${stats.third}</td></tr>"""
            }

            val bannerOptions = buildString {
                appendElement("all", I18nManager["export.html_all_banners"])
                bannerStats.keys.sorted().forEach { code ->
                    val name = I18nManager["banner.$code"]
                    appendElement(code, name)
                }
            }

            val rarityOptions = buildString {
                appendElement("all", I18nManager["export.html_all_rarities"])
                appendElement("5", I18nManager["export.html_five_star"])
                appendElement("4", I18nManager["export.html_four_star"])
                if (!ignoreThreeStar) appendElement("3", I18nManager["export.html_three_star"])
            }

            val headerTime = I18nManager["export.html_time"]
            val headerItem = I18nManager["export.html_item"]
            val headerBanner = I18nManager["export.html_filter_banner"]
            val headerRarity = I18nManager["export.html_filter_rarity"]

            val rows = sorted.joinToString("\n") { record ->
                val localized = ItemTranslator[record.itemID]
                val rankClass = when (record.rankType) {
                    5 -> "five"
                    4 -> "four"
                    else -> "three"
                }
                val displayCode = if (record.gachaType == "400") "301" else record.gachaType
                val displayName = I18nManager["banner.$displayCode"]
                """<tr class="$rankClass" data-banner="$displayCode" data-rarity="${record.rankType}"><td>${record.time}</td><td>$localized</td><td>$displayName</td><td>${record.rankType}★</td></tr>"""
            }

            val title = I18nManager["export.html_title"]
            val totalLabel = I18nManager["export.html_total"]
            val byBannerLabel = I18nManager["export.html_by_banner"]
            val bannerLabel = I18nManager["export.html_filter_banner"]
            val rarityLabel = I18nManager["export.html_filter_rarity"]
            val fiveStarLabel = I18nManager["export.html_five_star"]
            val fourStarLabel = I18nManager["export.html_four_star"]
            val threeStarLabel = I18nManager["export.html_three_star"]

            val html = """
<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>$title UID $uid</title>
<style>
  body{font-family:sans-serif;margin:2rem;background:#1a1a2e;color:#eee}
  table{border-collapse:collapse;width:100%;margin-top:1rem}
  th,td{border:1px solid #444;padding:8px;text-align:left}
  th{background:#16213e}
  .five{color:gold}.four{color:#c77dff}.three{color:#90e0ef}
  h1{color:#90e0ef}
  h2{color:#c77dff;margin-top:2rem}
  .stats{display:flex;gap:2rem;margin:1rem 0;flex-wrap:wrap}
  .stats div{background:#16213e;padding:1rem;border-radius:8px}
  .filters{display:flex;gap:1rem;margin:1rem 0}
  select{background:#16213e;color:#eee;border:1px solid #444;padding:6px;border-radius:4px}
  .hidden{display:none}
</style></head>
<body>
<h1>$title — UID $uid</h1>
<div class="stats">
  <div><b>$totalLabel:</b> $total</div>
  <div><b>$fiveStarLabel:</b> $fiveStarCount</div>
  <div><b>$fourStarLabel:</b> $fourStarCount</div>
  ${if (!ignoreThreeStar) "<div><b>$threeStarLabel:</b> $threeStarCount</div>" else ""}
</div>
<h2>$byBannerLabel</h2>
<table>
<thead><tr><th>$headerBanner</th><th>$totalLabel</th><th>$fiveStarLabel</th><th>$fourStarLabel</th></tr></thead>
<tbody>$bannerTableRows</tbody>
</table>
<div class="filters">
  <label>$bannerLabel: <select id="bannerFilter">$bannerOptions</select></label>
  <label>$rarityLabel: <select id="rarityFilter">$rarityOptions</select></label>
</div>
<table id="gachaTable">
<thead><tr><th>$headerTime</th><th>$headerItem</th><th>$headerBanner</th><th>$headerRarity</th></tr></thead>
<tbody>$rows</tbody>
</table>
<script>
  function filterTable() {
    var banner = document.getElementById("bannerFilter").value;
    var rarity = document.getElementById("rarityFilter").value;
    var rows = document.querySelectorAll("#gachaTable tbody tr");
    rows.forEach(function(row) {
      var bMatch = banner === "all" || row.dataset.banner === banner;
      var rMatch = rarity === "all" || row.dataset.rarity === rarity;
      row.classList.toggle("hidden", !(bMatch && rMatch));
    });
  }
  document.getElementById("bannerFilter").addEventListener("change", filterTable);
  document.getElementById("rarityFilter").addEventListener("change", filterTable);
</script>
</body></html>
            """.trimIndent()

            val file = if (Files.isDirectory(path)) {
                path.resolve("Gacha_${uid}_${Instant.now().epochSecond}.html")
            } else path
            Files.createDirectories(file.parent)
            file.toFile().writeText(html)
            file
        }

    private fun StringBuilder.appendElement(value: String, label: String) {
        append("""<option value="$value">$label</option>""")
    }
}

object UigfExporter : GachaExporter {

    private val json = Json {
        prettyPrint = true; encodeDefaults = true
    }
    override val fileExtension: String = ".json"

    @Serializable
    private data class UigfV3Info(
        val uid: String,
        val lang: String = "en-us",
        @SerialName("export_time") val exportTime: String =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.now()),
        @SerialName("export_timestamp") val exportTimestamp: Long = Instant.now().epochSecond,
        @SerialName("export_app") val exportApp: String = "Genshin-Analyzer-NEXT",
        @SerialName("export_app_version") val exportAppVersion: String = "v0.1",
        @SerialName("uigf_version") val uigfVersion: String,
        @SerialName("region_time_zone") val timeZone: Int = serverTimeZone(uid)
    )

    @Serializable
    private data class UigfV3Record(
        @SerialName("gacha_type") val gachaType: String,
        val time: String,
        val name: String,
        @SerialName("item_type") val itemType: String,
        @SerialName("item_id") val itemId: String,
        @SerialName("rank_type") val rankType: String,
        val id: String,
        @SerialName("uigf_gacha_type") val uigfGachaType: String,
    )

    @Serializable
    private data class UigfV41Info(
        @SerialName("export_timestamp") val exportTimestamp: Long = Instant.now().epochSecond,
        @SerialName("export_app") val exportApp: String = "Genshin-Analyzer-NEXT",
        @SerialName("export_app_version") val exportAppVersion: String = "v0.1",
        @SerialName("version") val UigfVersion: String = "v4.1",
    )

    @Serializable
    private data class UigfV41Hk4e(
        val uid: String,
        @SerialName("timezone") val timeZone: Int,
        @SerialName("lang") val language: String,
        val list: List<UigfV41Record>
    )

    @Serializable
    private data class UigfV41Record(
        @SerialName("uigf_gacha_type") val uigfGachaType: String,
        @SerialName("gacha_type") val gachaType: String,
        @SerialName("item_id") val itemId: String,
        val time: String,
        val name: String,
        @SerialName("item_type") val itemType: String,
        @SerialName("rank_type") val rankType: String,
        val id: String,
    )

    override fun export(records: List<GachaRecord>, uid: String, path: Path,
                        ignoreThreeStar: Boolean): Result<Path> = export(uid to records,"v3.0", path)

    fun export(
        pair: Pair<String, List<GachaRecord>>,
        version: String,
        path: Path = IOConfiguration.default_ExportPath,
        language: String = if (I18nManager.currentLocale == "zh") "zh-cn" else "en-us",
        regionTimeZone: Int = serverTimeZone(pair.first),
    ): Result<Path> = runCatching {

        require(version == "v3.0" || version == "v4.1") { "Unsupported UIGF version: $version" }
        require(pair.second.isNotEmpty()) { "Cannot export empty gacha records" }
        require(language in setOf("zh-cn", "en-us")) { "Unsupported export language: $language" }
        require(regionTimeZone in -12..14) { "Invalid server timezone: $regionTimeZone" }

        val exportedAt = Instant.now()
        val finalJsonMap: JsonObject = if (version == "v3.0") {
            val v3infoObj = json.encodeToJsonElement(UigfV3Info(
                uid = pair.first,
                lang = language,
                exportTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneOffset.ofHours(regionTimeZone)).format(exportedAt),
                exportTimestamp = exportedAt.epochSecond,
                uigfVersion = version,
                timeZone = regionTimeZone
            ))
            val v3recordObj = json.encodeToJsonElement(pair.second.map { record ->
                    UigfV3Record(
                        gachaType = record.gachaType,
                        time = record.time,
                        name = ItemTranslator.getExportName(record.itemID, language) ?: record.name,
                        itemType = typeNameSanitizer(record.itemType, language),
                        itemId = record.itemID,
                        rankType = record.rankType.toString(),
                        id = record.recordID,
                        uigfGachaType = if (record.gachaType == "400") "301" else record.gachaType,
                    )
                })

            buildJsonObject {
                put("info", v3infoObj)
                put("list", v3recordObj)
            }
        } else {
            val v41InfoObj = json.encodeToJsonElement(UigfV41Info(exportTimestamp = exportedAt.epochSecond))
            val v41List = pair.second.map {
                record -> UigfV41Record(
                uigfGachaType = if (record.gachaType == "400") "301" else record.gachaType,
                gachaType = record.gachaType,
                time = record.time,
                name = ItemTranslator.getExportName(record.itemID, language) ?: record.name,
                itemType = typeNameSanitizer(record.itemType, language),
                itemId = record.itemID,
                rankType = record.rankType.toString(),
                id = record.recordID
            )

            }
            val v41Hk4eObj = json.encodeToJsonElement(UigfV41Hk4e(
                uid = pair.first, timeZone = regionTimeZone, language = language, list = v41List
            ))
            buildJsonObject {
                put("info", v41InfoObj)
                put("hk4e", JsonArray(listOf(v41Hk4eObj)))
            }
        }

        val jsonStr = json.encodeToString(JsonObject.serializer(), finalJsonMap)
        val outputFile = resolveExportFile(path, pair.first, version)
        outputFile.toAbsolutePath().parent?.let(Files::createDirectories)
        outputFile.toFile().writeText(jsonStr)
        outputFile
    }


    // UIGF v3 fallback when no explicit server timezone is available!!
    private fun serverTimeZone(uid: String): Int  {
        return when (uid.firstOrNull()) {
            '6' -> -5
            '7' -> 1
            else -> 8
        }
    }

    private fun typeNameSanitizer(input: String, lang: String): String =
        when (input.lowercase()) {
            "角色", "character" -> if (lang == "zh-cn") "角色" else "Character"
            "武器", "weapon" -> if (lang == "zh-cn") "武器" else "Weapon"
            else -> input
        }

    private fun resolveExportFile(path: Path, uid: String, version: String): Path {
        return if (Files.isDirectory(path)) {
            val fileName = "UIGF_${version}_${uid}_${DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC).format(Instant.now())}${fileExtension}"
            path.resolve(fileName)
        } else {
            path
        }
    }
}

fun resolveFile(path: Path, uid: String, fileType: String): Path {
    return if (Files.isDirectory(path)) {
        path.resolve("Gacha_${uid}_${Instant.now().epochSecond}.$fileType")
    } else path
}
