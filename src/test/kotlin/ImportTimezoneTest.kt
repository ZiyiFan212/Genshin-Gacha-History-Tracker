import model.parseJson
import utilities.inferredServerTimezone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportTimezoneTest {
    private val records = """[{"gacha_type":"301","time":"2025-01-01 00:30:00","name":"Test","item_type":"weapon","item_id":"11301","id":"1","rank_type":3}]"""
    private fun archive(v4: Boolean, uid: String, field: String?) : String {
        val metadata = "\"uid\":\"$uid\"" + (field?.let {
            ",\"${if (v4) "timezone" else "region_time_zone"}\":$it"
        } ?: "")
        return if (v4) "{\"hk4e\":[{$metadata,\"list\":$records}]}"
        else "{\"info\":{$metadata},\"list\":$records}"
    }

    @Test
    fun `explicit timezone takes precedence and invalid metadata is rejected`() {
        for (v4 in listOf(false, true)) {
            for (uid in listOf("123456789", "600000001", "700000001")) {
                val parsed = parseJson(archive(v4, uid, "8")).getOrThrow()
                assertEquals("2025-01-01 00:30:00", parsed.second.single().time)
                for (offset in listOf("-5", "1", "0", "9", "null", "\"8\"", "8.5", "{}")) {
                    assertTrue(parseJson(archive(v4, uid, offset)).isFailure, "$v4 $uid $offset")
                }
            }
        }
    }

    @Test
    fun `only legacy format permits timezone inference`() {
        assertEquals(-5, inferredServerTimezone("600000001"))
        assertEquals(1, inferredServerTimezone("700000001"))
        assertEquals(8, inferredServerTimezone("123456789"))
        for (uid in listOf("123456789", "800000001")) {
            assertTrue(parseJson(archive(false, uid, null)).isSuccess)
            assertTrue(parseJson(archive(true, uid, null)).isFailure)
        }
        for (uid in listOf("600000001", "700000001")) {
            assertTrue(parseJson(archive(false, uid, null)).isFailure)
        }
    }
}
