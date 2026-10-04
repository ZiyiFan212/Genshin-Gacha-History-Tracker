package model

data class MergeResult(val records: List<GachaRecord>, val stats: UserStatistics, val newCount: Int)
