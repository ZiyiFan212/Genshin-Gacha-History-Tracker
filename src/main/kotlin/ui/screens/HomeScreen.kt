package ui.screens

import analytics.PityState
import analytics.StreakAnalysis
import assets.I18nManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import model.UserStatistics
import ui.components.SectionTitle
import ui.components.StatRow

@Composable
fun HomeScreen(
    stats: UserStatistics?,
    pity: PityState?,
    streak: StreakAnalysis?,
    uid: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(I18nManager["nav.home"])
        Text(
            uid?.let { "${I18nManager["home.uid"]}: $it" } ?: I18nManager["home.no_uid"],
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (pity != null) {
            SectionTitle(I18nManager["home.current_pity"])
            StatRow(
                listOf(
                    I18nManager["banner.301"] to "${pity.banner301}",
                    I18nManager["banner.302"] to "${pity.banner302}",
                    I18nManager["banner.500"] to "${pity.banner500}",
                    I18nManager["banner.200"] to "${pity.banner200}",
                )
            )
        }

        streak?.let {
            SectionTitle(I18nManager["stats.streak"])
            StatRow(
                listOf(
                    I18nManager["stats.max_consecutive_up"] to "${it.maxConsecutiveUp}",
                    I18nManager["stats.max_consecutive_loss"] to "${it.maxConsecutiveLoss}",
                )
            )
        }

        if (stats != null) {
            SectionTitle(I18nManager["home.overview"])
            StatRow(
                listOf(
                    I18nManager["stats.total_wishes"] to "${stats.totalWishes}",
                    I18nManager["stats.limited_wishes"] to "${stats.totalWishesLim}",
                    I18nManager["stats.primogems"] to "${stats.totalPrimo}",
                )
            )
            StatRow(
                listOf(
                    I18nManager["stats.five_star"] to "${stats.totalFiveStars}",
                    I18nManager["stats.four_star"] to "${stats.totalFourStars}",
                    I18nManager["stats.win_rate"] to "%.1f%%".format(stats.winRate),
                )
            )
            StatRow(
                listOf(
                    I18nManager["stats.avg_pity"] to "%.1f".format(stats.avgPity),
                    I18nManager["stats.avg_pity_lim"] to "%.1f".format(stats.avgPityLim),
                )
            )
        }
    }
}