import model.GachaRecord
import validation.isValidTime
import utilities.records.mergeWith
import validation.DataValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StrictRecordTimeTest {
    @Test
    fun `shape and calendar validity are both required`() {
        val cases = mapOf(
            "2025-02-30 12:00:00" to false,
            "2025-04-31 12:00:00" to false,
            "2024-02-29 12:00:00" to true,
            "2025-02-29 12:00:00" to false,
            "2025-01-01 24:00:00" to false,
            "" to false,
            " 2025-01-01 12:00:00" to false,
            "2025-01-01 12:00:00 " to false,
            "2025-01-01  12:00:00" to false,
        )
        for ((time, valid) in cases) {
            val record = GachaRecord("301", time, "Test", "weapon", "11301", "1", 3)
            assertEquals(valid, time.isValidTime(), time)
            assertEquals(!valid, DataValidator.validate(listOf(record), "123456789") { true }.hasErrors, time)
            if (valid) assertEquals(time, listOf(record).mergeWith(emptyList()).single().time)
            else assertFailsWith<IllegalArgumentException>(time) { listOf(record).mergeWith(emptyList()) }
        }
        assertFalse((null as String?).isValidTime())
    }
}
