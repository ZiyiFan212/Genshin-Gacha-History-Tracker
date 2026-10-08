package utilities.records

import model.GachaRecord

fun compareGachaRecordIds(a: String, b: String): Int {
    if (a.isBlank() && b.isBlank()) return 0
    if (a.isBlank()) return -1
    if (b.isBlank()) return 1
    val aNum = a.toULongOrNull()
    val bNum = b.toULongOrNull()
    if (aNum != null && bNum != null) return aNum.compareTo(bNum)
    return a.compareTo(b)
}

fun GachaRecord.compareChronologically(other: GachaRecord): Int {
    if (recordID.isNotBlank() && other.recordID.isNotBlank()) {
        val byId = compareGachaRecordIds(recordID, other.recordID)
        if (byId != 0) return byId
    }
    return time.compareTo(other.time)
}

val gachaRecordChronologicalComparator: Comparator<GachaRecord> =
    Comparator { a, b -> a.compareChronologically(b) }

fun List<GachaRecord>.sortedChronologically(): List<GachaRecord> =
    sortedWith(gachaRecordChronologicalComparator)

fun List<GachaRecord>.sortedChronologicallyDescending(): List<GachaRecord> =
    sortedWith(gachaRecordChronologicalComparator.reversed())
