package ui.screens

import assets.I18nManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.ImportResult
import ui.components.SectionTitle
import java.awt.FileDialog
import java.nio.file.Path
import java.nio.file.Paths

@Composable
fun ImportScreen(
    lastImport: ImportResult?,
    onImport: (Path) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(I18nManager["nav.import"])
        Text(
            I18nManager["import.description"],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Button(onClick = {
            val dialog = FileDialog(null as java.awt.Frame?, I18nManager["import.select_file"], FileDialog.LOAD)
            dialog.file = "*.json"
            dialog.isVisible = true
            val file = dialog.file
            val dir = dialog.directory
            if (file != null && dir != null) {
                onImport(Paths.get(dir, file))
            }
        }) {
            Text(I18nManager["import.select_file"])
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                I18nManager["import.steps_title"],
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text("1. ${I18nManager["import.step1"]}", color = MaterialTheme.colorScheme.onBackground)
            Text("2. ${I18nManager["import.step2"]}", color = MaterialTheme.colorScheme.onBackground)
            Text("3. ${I18nManager["import.step3"]}", color = MaterialTheme.colorScheme.onBackground)
            Text("4. ${I18nManager["import.step4"]}", color = MaterialTheme.colorScheme.onBackground)
        }

        lastImport?.let { result ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    I18nManager["import.last_result"],
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text("${I18nManager["home.uid"]}: ${result.uid}", color = MaterialTheme.colorScheme.onBackground)
                Text("${I18nManager["import.new_records"]}: ${result.newRecords}", color = MaterialTheme.colorScheme.onBackground)
                Text("${I18nManager["import.total_records"]}: ${result.totalRecords}", color = MaterialTheme.colorScheme.onBackground)
                if (result.validation.warnCount > 0) {
                    Text("${I18nManager["import.warnings"]}: ${result.validation.warnCount}", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}