package ui.screens

import analytics.BannerStats
import analytics.GoldPullSegment
import analytics.LuckAnalysis
import analytics.MonthlyConsumption
import analytics.PityState
import analytics.StreakAnalysis
import assets.I18nManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.components.GoldHistoryChart
import ui.components.SectionTitle

@Composable
fun StatsScreen(
    bannerStats: List<BannerStats>,
    luck: LuckAnalysis?,
    consumption: List<MonthlyConsumption>,
    streak: StreakAnalysis?,
    goldHistory: Map<String, List<GoldPullSegment>> = emptyMap(),
    pityState: PityState = PityState(0, 0, 0, 0, 0),
    totalWishes: Int = 0,
    longestNoPullDays: Int = 0,
    limitedWeaponAvgPity: Double = 0.0,
    upRatio: Pair<Double, Double> = Double.NaN to Double.NaN,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(I18nManager["nav.stats"])

        StatsOverviewGrid(
            luck = luck,
            streak = streak,
            totalWishes = totalWishes,
            longestNoPullDays = longestNoPullDays,
            limitedWeaponAvgPity = limitedWeaponAvgPity,
            upRatio = upRatio,
        )

        SectionTitle(I18nManager["stats.gold_history_section"])
        GoldHistoryChart(goldHistory, pityState, Modifier.fillMaxWidth())

        bannerStats.forEach { banner ->
            BannerStatsCard(banner)
        }

        if (consumption.isNotEmpty()) {
            MonthlyConsumptionSection(consumption)
        }
    }
}

@Composable
private fun StatsOverviewGrid(
    luck: LuckAnalysis?,
    streak: StreakAnalysis?,
    totalWishes: Int,
    longestNoPullDays: Int,
    limitedWeaponAvgPity: Double,
    upRatio: Pair<Double, Double>,
) {
    val summaryText = luck?.summary?.let { I18nManager[it] } ?: I18nManager["stats.no_data"]
    val winRate = luck?.winRate ?: 0.0
    val upAvgPity = luck?.avgPity ?: 0.0

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            I18nManager["stats.luck_prefix"],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            summaryText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatsMetricCell(
                value = "%.1f%%".format(winRate),
                label = I18nManager["stats.not_guarantee_rate"],
                modifier = Modifier.weight(1f),
            )
            StatsMetricCell(
                value = totalWishes.toString(),
                label = I18nManager["stats.total_wishes_label"],
                modifier = Modifier.weight(1f),
            )
            StatsMetricCell(
                value = "%.1f${I18nManager["stats.pity_unit"]}".format(upAvgPity),
                label = I18nManager["stats.up_char_avg"],
                modifier = Modifier.weight(1f),
            )
            StatsMetricCell(
                value = "%.1f${I18nManager["stats.pity_unit"]}".format(limitedWeaponAvgPity),
                label = I18nManager["stats.limited_weapon_avg"],
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatsMetricCell(
                value = "${streak?.maxConsecutiveUp ?: 0}",
                label = I18nManager["stats.max_consecutive_up"],
                modifier = Modifier.weight(1f),
            )
            StatsMetricCell(
                value = "${streak?.maxConsecutiveLoss ?: 0}",
                label = I18nManager["stats.max_consecutive_loss"],
                modifier = Modifier.weight(1f),
            )
            StatsMetricCell(
                value = "${longestNoPullDays}${I18nManager["stats.day_unit"]}",
                label = I18nManager["stats.longest_no_pull"],
                modifier = Modifier.weight(1f),
            )
            StatsMetricCell(
                value = if (upRatio.first.isNaN()) "N/A" else "${upRatio.first.toInt()}:${upRatio.second.toInt()}",
                label = I18nManager["stats.up_ratio"],
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatsMetricCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun MonthlyConsumptionSection(consumption: List<MonthlyConsumption>) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (expanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                I18nManager["stats.monthly_consumption"],
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (expanded) {
            Column(
                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                consumption.take(12).forEach { month ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            month.month,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "${month.wishes} ${I18nManager["stats.wishes"]} · ${I18nManager["stats.primogems"]}: ${month.primogems}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerStatsCard(banner: BannerStats) {
    val bannerName = I18nManager["banner.${banner.bannerCode}"]
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            bannerName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MiniStat(I18nManager["stats.total_wishes"], "${banner.totalWishes}")
            MiniStat(I18nManager["stats.four_star"], "${banner.fourStars}")
            MiniStat(I18nManager["stats.five_star"], "${banner.fiveStars}")
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MiniStat(I18nManager["stats.win_rate"], "%.1f%%".format(banner.winRate))
            MiniStat(
                if (banner.bannerCode == "301") I18nManager["stats.up_avg_pity"]
                else I18nManager["stats.avg_pity"],
                "%.1f".format(banner.avgPity)
            )
            MiniStat(I18nManager["home.current_pity"], "${banner.currentPity}")
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}