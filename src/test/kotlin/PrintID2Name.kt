import assets.ItemTranslator
import kotlin.test.Test

class PrintID2Name {

    @Test
    fun check() {
        run {
            ItemTranslator.load()
        }.onFailure {
            println("failed to initialize: $it")
        }

        val map = ItemTranslator.returnMap()
        map.forEach { string, string1 ->  println("key: $string, value: $string1")}
    }
}