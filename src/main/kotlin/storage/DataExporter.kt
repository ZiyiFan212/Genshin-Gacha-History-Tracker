package storage

import assets.I18nManager
import assets.ItemTranslator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import model.GachaRecord
import model.sortedChronologicallyDescending
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

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

    override val fileExtension: String = "json"

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

    override fun export(records: List<GachaRecord>, uid: String, path: Path, ignoreThreeStar: Boolean): Result<Path> =
        export(uid to records, "v4.0", path)

    fun export(
        pair: Pair<String, List<GachaRecord>>,
        version: String,
        path: Path = IOConfiguration.default_ExportPath
    ): Result<Path> = runCatching {
        require(version == "v3.0" || version == "v4.0") { "Unsupported UIGF version: $version" }
        require(pair.second.isNotEmpty()) { "Cannot export empty gacha records" }

        val infoObj = json.encodeToJsonElement(UIGFExportInfo(uid = pair.first, uigfVersion = version))
        val recordsArr = json.encodeToJsonElement(pair.second)
        val key = if (version == "v3.0") "list" else "records"
        val finalJsonMap = buildJsonObject {
            put("info", infoObj)
            put(key, recordsArr)
        }

        val jsonStr = json.encodeToString(JsonObject.serializer(), finalJsonMap)
        val outputFile = resolveExportFile(path, pair.first)
        Files.createDirectories(outputFile.parent)
        outputFile.toFile().writeText(jsonStr)
        outputFile
    }

    private fun resolveExportFile(path: Path, uid: String): Path {
        return if (Files.isDirectory(path)) {
            val fileName = "UIGF_${uid}_${Instant.now().epochSecond}.json"
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