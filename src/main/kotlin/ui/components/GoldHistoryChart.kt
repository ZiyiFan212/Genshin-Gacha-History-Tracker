package ui.components

import analytics.GoldPullSegment
import analytics.PityState
import analytics.goldHistoryBanners
import assets.I18nManager
import assets.ItemTranslator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import utilities.AppConstants
import utilities.AppLogger

private const val MAX_PITY_SCALE = 90
private const val BAR_TRACK_WIDTH = 320
private const val ICON_SIZE = 52
private const val BAR_HEIGHT = 30
private const val CHARACTER_COL_WIDTH = 168

@Composable
fun GoldHistoryChart(
    goldHistory: Map<String, List<GoldPullSegment>>,
    pityState: PityState = PityState(0, 0, 0, 0, 0),
    modifier: Modifier = Modifier,
) {
    var bannerIndex by remember { mutableIntStateOf(0) }
    var animationKey by remember { mutableIntStateOf(0) }
    val banners = goldHistoryBanners
    val currentBanner = banners[bannerIndex]
    val segments = goldHistory[currentBanner].orEmpty()

    LaunchedEffect(bannerIndex) {
        animationKey++
    }

    val currentPityForBanner = when (currentBanner) {
        AppConstants.CHARACTER_EVENT_BANNER -> pityState.banner301
        AppConstants.WEAPON_EVENT_BANNER -> pityState.banner302
        AppConstants.CHRONICLE_EVENT_BANNER -> pityState.banner500
        AppConstants.STANDARD_EVENT_BANNER -> pityState.banner200
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(I18nManager["timeline.gold_history"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        BannerPagerHeader(
            bannerIndex = bannerIndex,
            bannerCount = banners.size,
            bannerLabel = I18nManager["banner.$currentBanner"],
            onPrevious = { if (bannerIndex > 0) bannerIndex-- },
            onNext = { if (bannerIndex < banners.lastIndex) bannerIndex++ },
        )

        ChartLegend()

        if (segments.isEmpty()) {
            EmptyBannerTable()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEachIndexed { index, segment ->
                    val currentPityIndex = if (index == 0 && currentPityForBanner != null) index else -1
                    if (currentPityIndex == 0) {
                        CurrentPityRow(
                            currentPity = currentPityForBanner!!,
                            index = 0,
                            animationKey = animationKey,
                        )
                    }
                    GoldPullRow(
                        segment = segment,
                        index = if (currentPityForBanner != null) index + 1 else index,
                        animationKey = animationKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentPityRow(
    currentPity: Int,
    index: Int = 0,
    animationKey: Int = 0,
) {
    val targetFraction = (currentPity.coerceIn(1, MAX_PITY_SCALE).toFloat() / MAX_PITY_SCALE).coerceIn(0.05f, 1f)
    var animationStarted by remember(animationKey) { mutableStateOf(false) }

    LaunchedEffect(animationKey) {
        animationStarted = false
    }

    LaunchedEffect(animationKey, index) {
        kotlinx.coroutines.delay((index * 50).toLong())
        animationStarted = true
    }

    val barFraction by animateFloatAsState(
        targetValue = if (animationStarted) targetFraction else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "current_pity_bar_${animationKey}_$index",
    )

    val barColor = pityBarColor(currentPity)

    Row(
        Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(CHARACTER_COL_WIDTH.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(ICON_SIZE.dp))
            Text(
                I18nManager["timeline.current_pity"],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Box(
            Modifier
                .width(BAR_TRACK_WIDTH.dp)
                .height(BAR_HEIGHT.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barFraction)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor),
                contentAlignment = Alignment.Center,
            ) {
                if (barFraction > 0.15f) {
                    Text(
                        "$currentPity",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (barFraction <= 0.15f) {
                Text(
                    "$currentPity",
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Text(
            I18nManager["timeline.current_pity"],
            Modifier.weight(1f).padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun BannerPagerHeader(
    bannerIndex: Int,
    bannerCount: Int,
    bannerLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = bannerIndex > 0) {
            Icon(Icons.Default.ChevronLeft, contentDescription = I18nManager["timeline.prev_banner"])
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(bannerLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${bannerIndex + 1} / $bannerCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        IconButton(onClick = onNext, enabled = bannerIndex < bannerCount - 1) {
            Icon(Icons.Default.ChevronRight, contentDescription = I18nManager["timeline.next_banner"])
        }
    }
}

@Composable
private fun ChartLegend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendChip(I18nManager["timeline.pity_0_40"], pityBarColor(30))
        LegendChip(I18nManager["timeline.pity_40_70"], pityBarColor(55))
        LegendChip(I18nManager["timeline.pity_70_90"], pityBarColor(80))
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.height(10.dp).width(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun EmptyBannerTable() {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            TableHeaderRow()
            Box(
                Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    I18nManager["timeline.gold_empty"],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun TableHeaderRow() {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(I18nManager["timeline.col_character"], Modifier.width(CHARACTER_COL_WIDTH.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(I18nManager["timeline.col_pity"], Modifier.width(BAR_TRACK_WIDTH.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(I18nManager["timeline.col_time"], Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun GoldPullRow(
    segment: GoldPullSegment,
    index: Int = 0,
    animationKey: Int = 0,
) {
    val targetFraction = (segment.pity.coerceIn(1, MAX_PITY_SCALE).toFloat() / MAX_PITY_SCALE).coerceIn(0.05f, 1f)
    var animationStarted by remember(animationKey) { mutableStateOf(false) }

    LaunchedEffect(animationKey) {
        animationStarted = false
    }

    LaunchedEffect(animationKey, index) {
        kotlinx.coroutines.delay((index * 50).toLong())
        animationStarted = true
    }

    val barFraction by animateFloatAsState(
        targetValue = if (animationStarted) targetFraction else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "bar_${animationKey}_$index",
    )

    val barColor = pityBarColor(segment.pity)

    Row(
        Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(CHARACTER_COL_WIDTH.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemIcon(segment.itemId, Modifier.size(ICON_SIZE.dp), showFallback = false)
            Column {
                Text(
                    ItemTranslator[segment.itemId],
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (segment.isStandardLoss) {
                    Text(
                        I18nManager["timeline.lost_5050"],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                } else if (segment.guaranteeType == AppConstants.GuaranteeType.GUARANTEED) {
                    Text(
                        I18nManager["timeline.guaranteed"],
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD700),
                    )
                } else if (segment.guaranteeType == AppConstants.GuaranteeType.CAPTURE_RADIANCE){
                    Text(
                        I18nManager["timeline.capture_radiance"],
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0x80FF7F7F),
                    )

                }
            }
        }

        Box(
            Modifier
                .width(BAR_TRACK_WIDTH.dp)
                .height(BAR_HEIGHT.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barFraction)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor),
                contentAlignment = Alignment.Center,
            ) {
                if (barFraction > 0.15f) {
                    Text(
                        "${segment.pity}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (barFraction <= 0.15f) {
                Text(
                    "${segment.pity}",
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Text(
            segment.time.take(16),
            Modifier.weight(1f).padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
    }
}

fun pityBarColor(pity: Int): Color = when {
    pity <= 40 -> Color(0xFF4CAF50)
    pity <= 70 -> Color(0xFFFFC107)
    else -> Color(0xFFE53935)
}