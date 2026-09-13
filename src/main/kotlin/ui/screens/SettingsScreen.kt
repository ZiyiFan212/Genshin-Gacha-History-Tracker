package ui.screens

import assets.I18nManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.SectionTitle
import utilities.ThemeModeManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeModeManager,
    language: String,
    showAllItems: Boolean,
    ignoreThreeStarExport: Boolean,
    onThemeModeChange: (ThemeModeManager) -> Unit,
    onLanguageChange: (String) -> Unit,
    onShowAllItemsChange: (Boolean) -> Unit,
    onIgnoreThreeStarExportChange: (Boolean) -> Unit,
    onUpdateStats: () -> Unit,
    onUpdateRecords: () -> Unit,
    onDeleteUid: () -> Unit,
    onExit: () -> Unit,
    hasUid: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(I18nManager["nav.settings"])

        Text(
            I18nManager["settings.theme"],
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeModeManager.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(themeModeLabel(mode)) },
                )
            }
        }

        Text(
            I18nManager["settings.language"],
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = language == "en",
                onClick = { onLanguageChange("en") },
                label = { Text("English") },
            )
            FilterChip(
                selected = language == "zh",
                onClick = { onLanguageChange("zh") },
                label = { Text("中文") },
            )
        }

        Text(
            I18nManager["settings.timeline_display"],
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = showAllItems,
                onClick = { onShowAllItemsChange(true) },
                label = { Text(I18nManager["settings.show_all_items"]) },
            )
            FilterChip(
                selected = !showAllItems,
                onClick = { onShowAllItemsChange(false) },
                label = { Text(I18nManager["settings.show_4_5_star"]) },
            )
        }

        Text(
            I18nManager["export.csv_banner"],
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = !ignoreThreeStarExport,
                onClick = { onIgnoreThreeStarExportChange(false) },
                label = { Text(I18nManager["settings.include_3star_export"]) },
            )
            FilterChip(
                selected = ignoreThreeStarExport,
                onClick = { onIgnoreThreeStarExportChange(true) },
                label = { Text(I18nManager["settings.ignore_3star_export"]) },
            )
        }

        if (hasUid) {
            Button(
                onClick = onUpdateStats,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(I18nManager["settings.update_stats"])
            }

            Button(
                onClick = onUpdateRecords,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(I18nManager["settings.update_records"])
            }

            Button(
                onClick = onDeleteUid,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(I18nManager["settings.delete_uid"])
            }
        }

        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text(I18nManager["appUI.exit"])
        }
    }
}

private fun themeModeLabel(mode: ThemeModeManager): String = when (mode) {
    ThemeModeManager.LIGHT -> I18nManager["settings.theme_light"]
    ThemeModeManager.DARK -> I18nManager["settings.theme_dark"]
    ThemeModeManager.SYSTEM -> I18nManager["settings.theme_system"]
}