package ui.screens

import analytics.TimelineEntry
import assets.I18nManager
import assets.ItemTranslator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.components.ItemIcon
import ui.components.SectionTitle
import ui.theme.RankColor
import utilities.AppConstants

@Composable
fun TimelineScreen(
    timeline: List<TimelineEntry>,
    showAllItems: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val filteredTimeline = if (showAllItems) timeline else timeline.filter { it.record.rankType >= 4 }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        SectionTitle(I18nManager["nav.timeline"])

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (filteredTimeline.isEmpty()) {
                item {
                    Text(
                        I18nManager["timeline.empty"],
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                items(filteredTimeline.take(500)) { entry ->
                    TimelineCard(entry)
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(entry: TimelineEntry) {
    val record = entry.record
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
    ) {
        Row(
            Modifier.padding(10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemIcon(record.itemID, Modifier.padding(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ItemTranslator[record.itemID],
                    fontWeight = FontWeight.Bold,
                    color = RankColor(record.rankType),
                )
                Text(
                    record.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
                Text(
                    "${I18nManager["banner.${record.gachaType}"]} · ${I18nManager["home.current_pity"]}: ${entry.pityAtPull}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                if (entry.guaranteeType != AppConstants.GuaranteeType.NONE) {
                    Text(
                        guaranteeLabel(entry.guaranteeType),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text("${record.rankType}★", color = RankColor(record.rankType), fontWeight = FontWeight.Bold)
        }
    }
}

private fun guaranteeLabel(type: AppConstants.GuaranteeType): String = when (type) {
    AppConstants.GuaranteeType.WON_FIFTY_FIFTY -> I18nManager["timeline.won_5050"]
    AppConstants.GuaranteeType.LOST_FIFTY_FIFTY -> I18nManager["timeline.lost_5050"]
    AppConstants.GuaranteeType.GUARANTEED -> I18nManager["timeline.guaranteed"]
    AppConstants.GuaranteeType.STANDARD -> I18nManager["timeline.standard"]
    AppConstants.GuaranteeType.CAPTURE_RADIANCE -> I18nManager["timeline.capture_radiance"]
    AppConstants.GuaranteeType.NONE -> ""
}