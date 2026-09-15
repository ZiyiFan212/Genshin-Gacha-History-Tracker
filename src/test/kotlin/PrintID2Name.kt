import assets.ItemTranslator
import kotlin.test.Test
import kotlin.test.assertEquals

class PrintID2Name {

    @Test
    fun check() {
        ItemTranslator.load().getOrThrow()
        assertEquals("14401", ItemTranslator.getIdByName("西风秘典"))
        assertEquals("14401", ItemTranslator.getIdByName("Favonius Codex"))
    }
}
