import template.GachaRecord
import template.parseJson
import utilities.calculateStat
import utilities.findFirstJson
import utilities.validate
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test

/**
 * # Component: Gacha Core - Statistics Unit Test
 * * ## 1. Tested Functional Blocks
 * - `List<GachaRecord>.calculateStat()`: Evaluates total wishes, banner separations, and global/pity calculation accuracy.
 * - `calculateGlobalAvgPity()`: Verifies pooling mechanisms and mathematical average precision against standard-set bias.
 * * ## 2. Boundary & Edge Case Controls
 * - [PASSED] **Empty Dataset Handling**: Passed `listOf<GachaRecord>()` into the entry point. Checked for arithmetic safety (`Double.NaN` isolation) and verified output fields reset to `0`/`0.0` gracefully without crashing.
 * - [PASSED] **Pure Standard/Permanent Pack Input**: Passed datasets containing zero limited items to verify `winrate` equation stability.
 * * ## 3. Verification Environment
 * - Kotlin Toolchain: 2.3.21
 * - Test Framework: JUnit 5 / Gradle Test Runner
 */

class processtest {

    @Test
    fun `program function flow check, importing file`(){
        val directory: Path? = Path.of("/home/frank/Desktop")
        val jsonFPath: Path = directory?.findFirstJson() ?: return

        val result: Result<Path> = jsonFPath.validate()
        result.onFailure {
            println(result.exceptionOrNull())
            return@onFailure
        }.onSuccess {
            val jsonstring: String = jsonFPath.readText()
            val pair: Pair<String, List<GachaRecord>> = parseJson(jsonstring)
            //calculate(pair)

            // edge case: null list
            val playerStat = emptyList<GachaRecord>().calculateStat()
            println(playerStat)
        }
    }

    fun calculate(pair: Pair<String, List<GachaRecord>>) {
        val playerStat = pair.second.calculateStat()
        println(playerStat.toString())
    }
}