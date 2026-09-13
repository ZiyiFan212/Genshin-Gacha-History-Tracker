package ui

import assets.I18nManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
//import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Frame
import utilities.AppLogger
import ui.components.MessageBanner
import ui.screens.CalendarScreen
import ui.screens.CaptureScreen
import ui.screens.ExportScreen
import ui.screens.HomeScreen
import ui.screens.ImportScreen
import ui.screens.SettingsScreen
import ui.screens.StatsScreen
import ui.screens.TimelineScreen
import ui.theme.GenshinTheme
import utilities.PreferencesManager
import utilities.ThemeModeManager

@Composable
fun FrameWindowScope.App(viewModel: AppViewModel,
        onExit: () -> Unit,
        windowState: WindowState,
        td: PreferencesManager.ThemeDetector = remember {
            PreferencesManager.CustomizedThemeDetector()
        }
) {
    val state by viewModel.state.collectAsState()


    val isDarkTheme = when (state.themeMode) {
        ThemeModeManager.LIGHT -> false
        ThemeModeManager.DARK -> true
        ThemeModeManager.SYSTEM -> td.isSystemInDarkTheme()
    }

    GenshinTheme(darkTheme = isDarkTheme) {
        if (state.isLoading && state.uids.isEmpty()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(I18nManager["appUI.loading"], modifier = Modifier.padding(top = 16.dp))
                }
            }
            return@GenshinTheme
        }

        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            IntelliJToolBar(
                title = I18nManager["appUI.title"],
                uids = state.uids,
                selectedUid = state.selectedUid,
                onSelectUid = viewModel::selectUid,
                onExit = {
                    viewModel.shutdown()
                    onExit()
                },
                windowState = windowState,
            )

            Row(Modifier.weight(1f).fillMaxWidth()) {
                IntelliJSidebar(
                    currentScreen = state.currentScreen,
                    onNavigate = viewModel::navigate,
                )

                Column(
                    Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background),
                ) {
                    MessageBanner(
                        message = state.error ?: state.message,
                        isError = state.error != null,
                        onDismiss = viewModel::clearMessage,
                    )

                    if (state.isLoading) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        when (state.currentScreen) {
                            AppScreen.HOME -> HomeScreen(
                                stats = state.stats,
                                pity = state.pityState,
                                streak = state.streakAnalysis,
                                uid = state.selectedUid,
                                modifier = Modifier.weight(1f),
                            )
                            AppScreen.CAPTURE -> CaptureScreen(
                                isCapturing = state.isCapturing,
                                capturePhase = state.capturePhase,
                                onCapture = viewModel::captureFromProxy,
                                modifier = Modifier.weight(1f),
                            )
                            AppScreen.IMPORT -> ImportScreen(state.lastImport, viewModel::importFromPath, Modifier.weight(1f))
                            AppScreen.STATS -> StatsScreen(
                                bannerStats = viewModel.bannerStats,
                                luck = state.luck,
                                consumption = state.consumption,
                                streak = state.streakAnalysis,
                                goldHistory = viewModel.goldHistory,
                                pityState = state.pityState ?: analytics.PityState(0, 0, 0, 0, 0),
                                totalWishes = state.stats?.totalWishes ?: 0,
                                longestNoPullDays = viewModel.longestNoPullDays,
                                limitedWeaponAvgPity = viewModel.limitedWeaponAvgPity,
                                upRatio = viewModel.upRatio,
                                modifier = Modifier.weight(1f),
                            )
                            AppScreen.TIMELINE -> TimelineScreen(
                                timeline = viewModel.timeline,
                                showAllItems = state.showAllItems,
                                modifier = Modifier.weight(1f),
                            )
                            AppScreen.CALENDAR -> CalendarScreen(viewModel.calendarDays, Modifier.weight(1f))
                            AppScreen.EXPORT -> ExportScreen(
                                hasData = state.records.isNotEmpty(),
                                onExportUigfV3 = { viewModel.exportUIGF("v3.0") },
                                onExportUigfV4 = { viewModel.exportUIGF("v4.0") },
                                onExportExcel = viewModel::exportExcel,
                                onExportCsv = viewModel::exportCsv,
                                onExportHtml = viewModel::exportHtml,
                                modifier = Modifier.weight(1f),
                            )
                            AppScreen.SETTINGS -> SettingsScreen(
                                themeMode = state.themeMode,
                                language = state.language,
                                showAllItems = state.showAllItems,
                                ignoreThreeStarExport = state.ignoreThreeStarExport,
                                onThemeModeChange = viewModel::setThemeMode,
                                onLanguageChange = viewModel::setLanguage,
                                onShowAllItemsChange = viewModel::setShowAllItems,
                                onIgnoreThreeStarExportChange = viewModel::setIgnoreThreeStarExport,
                                onUpdateStats = viewModel::updateStats,
                                onUpdateRecords = viewModel::updateRecords,
                                onDeleteUid = viewModel::deleteCurrentUid,
                                onExit = {
                                    viewModel.shutdown()
                                    onExit()
                                },
                                hasUid = state.selectedUid != null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            IntelliJStatusBar(
                uid = state.selectedUid,
                recordCount = state.records.size,
                isCapturing = state.isCapturing,
            )
        }
    }
}

@Composable
private fun FrameWindowScope.IntelliJToolBar(
    title: String,
    uids: List<String>,
    selectedUid: String?,
    onSelectUid: (String) -> Unit,
    onExit: () -> Unit,
    windowState: WindowState,
) {
    WindowDraggableArea(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .height(40.dp)
            .padding(horizontal = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                    WindowPlacement.Floating
                                } else {
                                    WindowPlacement.Maximized
                                }
                            }
                        )
                    },
            )

            Spacer(Modifier.weight(1f))

            UidSelector(uids = uids, selectedUid = selectedUid, onSelect = onSelectUid)

            Spacer(Modifier.width(4.dp))

            ToolbarIconButton(
                icon = Icons.Default.Minimize,
                contentDescription = I18nManager["appUI.minimize"],
                onClick = {
                    AppLogger.info("Window minimized via toolbar button")
                    window.extendedState = Frame.ICONIFIED
                },
            )

            ToolbarIconButton(
                icon = if (windowState.placement == WindowPlacement.Maximized) Icons.Default.CropSquare else Icons.Default.Fullscreen,
                contentDescription = if (windowState.placement == WindowPlacement.Maximized) I18nManager["appUI.restore"] else I18nManager["appUI.maximize"],
                onClick = {
                    AppLogger.info("Window maximize/restore clicked, current placement: ${windowState.placement}, extendedState: ${window.extendedState}")
                    if (window.extendedState == Frame.ICONIFIED) {
                        window.extendedState = Frame.NORMAL
                    }
                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                },
            )

            ToolbarIconButton(
                icon = Icons.Default.Close,
                contentDescription = I18nManager["appUI.exit"],
                onClick = {
                    AppLogger.info("Window close button clicked")
                    onExit()
                },
                isDanger = true,
            )
        }
    }

    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isDanger: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .width(46.dp)
            .height(32.dp)
            .background(
                color = if (isDanger && isHovered) Color(0xFFE81123) else Color.Transparent,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(4.dp),
            tint = when {
                isDanger && isHovered -> Color.White
                isDanger -> Color(0xFFE81123)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun IntelliJSidebar(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(vertical = 4.dp),
    ) {
        SidebarItem(
            screen = AppScreen.HOME,
            label = I18nManager["nav.home"],
            icon = Icons.Default.Home,
            isSelected = currentScreen == AppScreen.HOME,
            onNavigate = onNavigate,
        )
        SidebarItem(
            screen = AppScreen.CAPTURE,
            label = I18nManager["nav.capture"],
            icon = Icons.Default.Wifi,
            isSelected = currentScreen == AppScreen.CAPTURE,
            onNavigate = onNavigate,
        )
        SidebarItem(
            screen = AppScreen.IMPORT,
            label = I18nManager["nav.import"],
            icon = Icons.Default.FileUpload,
            isSelected = currentScreen == AppScreen.IMPORT,
            onNavigate = onNavigate,
        )
        SidebarItem(
            screen = AppScreen.STATS,
            label = I18nManager["nav.stats"],
            icon = Icons.Default.Analytics,
            isSelected = currentScreen == AppScreen.STATS,
            onNavigate = onNavigate,
        )
        SidebarItem(
            screen = AppScreen.TIMELINE,
            label = I18nManager["nav.timeline"],
            icon = Icons.Default.Timeline,
            isSelected = currentScreen == AppScreen.TIMELINE,
            onNavigate = onNavigate,
        )
        SidebarItem(
            screen = AppScreen.CALENDAR,
            label = I18nManager["nav.calendar"],
            icon = Icons.Default.CalendarMonth,
            isSelected = currentScreen == AppScreen.CALENDAR,
            onNavigate = onNavigate,
        )
        SidebarItem(
            screen = AppScreen.EXPORT,
            label = I18nManager["nav.export"],
            icon = Icons.Default.FileDownload,
            isSelected = currentScreen == AppScreen.EXPORT,
            onNavigate = onNavigate,
        )
        SidebarItem(
            screen = AppScreen.SETTINGS,
            label = I18nManager["nav.settings"],
            icon = Icons.Default.Settings,
            isSelected = currentScreen == AppScreen.SETTINGS,
            onNavigate = onNavigate,
        )
    }
}

@Composable
private fun SidebarItem(
    screen: AppScreen,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onNavigate: (AppScreen) -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        Color.Transparent
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(backgroundColor)
            .clickable { onNavigate(screen) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.width(16.dp).height(16.dp),
            tint = textColor,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

@Composable
private fun IntelliJStatusBar(
    uid: String?,
    recordCount: Int,
    isCapturing: Boolean,
) {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .height(24.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCapturing) {
            Text(
                text = "●",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(4.dp))
        }
        uid?.let {
            Text(
                text = "${I18nManager["status.uid"]}: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = "${I18nManager["status.records"]}: $recordCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Genshin-Tracker NEXT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun UidSelector(uids: List<String>, selectedUid: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                selectedUid ?: I18nManager["home.no_uid"],
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uids.forEach { uid ->
                DropdownMenuItem(
                    text = { Text(uid) },
                    onClick = {
                        expanded = false
                        onSelect(uid)
                    },
                )
            }
        }
    }
}