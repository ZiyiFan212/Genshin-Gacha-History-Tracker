import model.GachaRecord
import model.Severity
import validation.DataValidator
import validation.itemIdValidationError
import validation.recordIdValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordValidationTest {
    private val record = GachaRecord("301", "2025-01-01 12:00:00", "New item", "weapon", "11301", "1", 3)
    private fun validate(value: GachaRecord) = DataValidator.validate(listOf(value), "123456789") { true }

    @Test
    fun `ID rules reject signs whitespace overflow and non ASCII digits`() {
        for (id in listOf("", " ", "-1", "+1", "1.0", "abc", "１２３", "1\n")) {
            assertNotNull(recordIdValidationError(id), id)
            assertNotNull(itemIdValidationError(id), id)
        }
        for (id in listOf("1", "1757474400022964365", "18446744073709551615")) {
            assertNull(recordIdValidationError(id), id)
        }
        for (id in listOf("18446744073709551616", "000000000000000000001")) {
            assertNotNull(recordIdValidationError(id), id)
        }
        assertNull(itemIdValidationError("999999999999999999999999"))
    }

    @Test
    fun `invalid fields reject records and character second banner maps to 301`() {
        for (bad in listOf(record.copy(rankType = 2), record.copy(rankType = 6),
            record.copy(gachaType = "999"), record.copy(recordID = "abc"),
            record.copy(itemID = ""), record.copy(uigfGachaType = "400"),
            record.copy(gachaType = "400", uigfGachaType = "400"))) {
            assertTrue(validate(bad).hasErrors, bad.toString())
        }
        for (pool in listOf("100", "200", "301", "302", "400", "500")) {
            for (rank in 3..5) {
                assertFalse(validate(record.copy(gachaType = pool, rankType = rank,
                    uigfGachaType = if (pool == "400") "301" else pool)).hasErrors)
            }
        }
    }

    @Test
    fun `unknown item is a distinct warning and keeps original record`() {
        val unknown = record.copy(itemID = "9999999999999")
        val report = DataValidator.validate(listOf(unknown), "123456789") { false }
        assertFalse(report.hasErrors)
        assertEquals(Severity.WARN, report.issues.single().severity)
        assertEquals("Unknown item ID: 9999999999999", report.issues.single().message)
        assertEquals("9999999999999", unknown.itemID)
        assertTrue(validate(record).issues.isEmpty())
        DataValidator.validate(listOf(record.copy(itemID = "-1")), "123456789") {
            error("Invalid IDs must not be looked up")
        }
    }
}
