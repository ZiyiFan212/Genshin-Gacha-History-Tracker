package utilities.records

import model.GachaRecord

// sanitize the name of the item type (from zh-cn to en-us)
fun GachaRecord.sanitizeItemName(): String {
    return when (this.itemType) {
        "角色", "character", "Character" -> "character"
        "武器", "weapon", "Weapon" -> "weapon"
        else -> this.itemType.lowercase()
    }
}
