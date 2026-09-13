package ui.screens

import assets.I18nManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.SectionTitle

@Composable
fun ExportScreen(
    hasData: Boolean,
    onExportUigfV3: () -> Unit,
    onExportUigfV4: () -> Unit,
    onExportExcel: () -> Unit,
    onExportCsv: () -> Unit,
    onExportHtml: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(I18nManager["nav.export"])
        Text(
            I18nManager["export.description"],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (!hasData) {
            Text(
                I18nManager["export.no_data"],
                color = MaterialTheme.colorScheme.onBackground,
            )
            return
        }

        Button(onClick = onExportUigfV3, modifier = Modifier.fillMaxWidth()) {
            Text(I18nManager["export.uigf_v3"])
        }
        Button(onClick = onExportUigfV4, modifier = Modifier.fillMaxWidth()) {
            Text(I18nManager["export.uigf_v4"])
        }
        Button(onClick = onExportExcel, modifier = Modifier.fillMaxWidth()) {
            Text(I18nManager["export.excel"])
        }
        Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
            Text(I18nManager["export.csv"])
        }
        Button(onClick = onExportHtml, modifier = Modifier.fillMaxWidth()) {
            Text(I18nManager["export.html"])
        }
    }
}