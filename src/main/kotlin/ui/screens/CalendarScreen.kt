package ui.screens

import analytics.CalendarDay
import analytics.CalendarItem
import assets.I18nManager
import assets.ItemTranslator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.components.ItemIcon
import ui.components.SectionTitle
import ui.theme.RankColor
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    calendarDays: Map<String, CalendarDay>,
    modifier: Modifier = Modifier,
) {
    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(I18nManager["nav.calendar"])

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { yearMonth = yearMonth.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null)
            }
            Text(
                "${yearMonth.year}-${yearMonth.monthValue.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = { yearMonth = yearMonth.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
            ).forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MonthGrid(
            yearMonth = yearMonth,
            calendarDays = calendarDays,
            selectedDate = selectedDate,
            onDayClick = { date -> selectedDate = date },
        )

        selectedDate?.let { date ->
            DayDetailPanel(calendarDays[date], date)
        }

        val monthPrefix = "${yearMonth.year}-${yearMonth.monthValue.toString().padStart(2, '0')}"
        val monthDays = calendarDays.filter { it.key.startsWith(monthPrefix) }
        if (monthDays.isNotEmpty()) {
            SectionTitle(I18nManager["calendar.month_summary"])
            val totalPulls = monthDays.values.sumOf { it.pullCount }
            val totalFive = monthDays.values.sumOf { it.fiveStars }
            val totalPrimogems = monthDays.values.sumOf { it.primogems }
            Text(
                "${I18nManager["stats.total_wishes"]}: $totalPulls · " +
                    "${I18nManager["stats.primogems"]}: $totalPrimogems · " +
                    "${I18nManager["stats.five_star"]}: $totalFive",
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    calendarDays: Map<String, CalendarDay>,
    selectedDate: String?,
    onDayClick: (String) -> Unit,
) {
    val firstDay = yearMonth.atDay(1)
    val startOffset = (firstDay.dayOfWeek.value + 6) % 7
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = ((startOffset + daysInMonth + 6) / 7) * 7

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (week in 0 until totalCells / 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (dayOfWeek in 0 until 7) {
                    val cellIndex = week * 7 + dayOfWeek
                    val dayNumber = cellIndex - startOffset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (dayNumber in 1..daysInMonth) {
                            val key = yearMonth.atDay(dayNumber).toString()
                            DayCellContent(
                                day = dayNumber,
                                data = calendarDays[key],
                                isSelected = key == selectedDate,
                                onClick = { onDayClick(key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCellContent(
    day: Int,
    data: CalendarDay?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        data == null -> MaterialTheme.colorScheme.surface
        data.fiveStars > 0 -> Color(0xFFFFD700).copy(alpha = 0.3f)
        data.fourStars > 0 -> Color(0xFFBA68C8).copy(alpha = 0.25f)
        data.pullCount > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val borderWidth = if (isSelected) 2.dp else 0.5.dp

    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .background(bg, RoundedCornerShape(3.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(3.dp))
            .padding(2.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$day",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    data != null && data.fiveStars > 0 -> Color(0xFFFFD700)
                    data != null && data.fourStars > 0 -> Color(0xFFE0C8F0)
                    else -> MaterialTheme.colorScheme.onBackground
                },
            )
            if (data != null && data.pullCount > 0) {
                Text(
                    "${data.pullCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
                if (data.fiveStars > 0) {
                    Text(
                        "★${data.fiveStars}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD700),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDetailPanel(data: CalendarDay?, date: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            date,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (data == null || data.pullCount == 0) {
            Text(
                I18nManager["calendar.no_pulls"],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DetailStat(I18nManager["calendar.day_pulls"], "${data.pullCount}")
            DetailStat(I18nManager["calendar.day_spent"], "${data.primogems}")
            DetailStat(I18nManager["stats.five_star"], "${data.fiveStars}")
            DetailStat(I18nManager["stats.four_star"], "${data.fourStars}")
        }

        val fiveStarItems = data.items.filter { it.rankType == 5 }
        val fourStarItems = data.items.filter { it.rankType == 4 }

        if (fiveStarItems.isNotEmpty()) {
            ItemRankSection(I18nManager["calendar.five_star_obtained"], fiveStarItems)
        }
        if (fourStarItems.isNotEmpty()) {
            ItemRankSection(I18nManager["calendar.four_star_obtained"], fourStarItems)
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
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
private fun ItemRankSection(title: String, items: List<CalendarItem>) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemIcon(item.itemId, Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ItemTranslator[item.itemId],
                        fontWeight = FontWeight.Bold,
                        color = RankColor(item.rankType),
                    )
                    Text(
                        "${I18nManager["banner.${item.gachaType}"]} · ${item.rankType}★",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}