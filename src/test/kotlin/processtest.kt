import model.GachaRecord
import model.parseJson
import analytics.calculateStat
import utilities.findLatestJson
import utilities.validate
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test

class processtest {

    @Test
    fun `program function flow check, importing file`() {
        val directory = Path.of(System.getProperty("user.home"), "Desktop")
        val jsonFPath = directory.findLatestJson().getOrNull() ?: return

        jsonFPath.validate()
            .onFailure { println(it); return }
            .onSuccess { path ->
                val jsonstring = path.readText()
                parseJson(jsonstring)
                    .onFailure { println(it); return@onSuccess }
                    .onSuccess { pair ->
                        val playerStat = pair.second.calculateStat()
                        println(playerStat)

                        val emptyStat = emptyList<GachaRecord>().calculateStat()
                        println(emptyStat)
                    }
            }
    }
}
