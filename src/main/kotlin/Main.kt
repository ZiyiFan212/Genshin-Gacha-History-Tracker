import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ui.App
import ui.AppViewModel

fun main() = application {
    val viewModel = remember { AppViewModel() }
    LaunchedEffect(Unit) { viewModel.initialize() }
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

    Window(
        onCloseRequest = {
            viewModel.shutdown()
            exitApplication()
        },
        title = "抽卡助手",
        undecorated = true,
        state = windowState,
    ) {
        App(
            viewModel,
            onExit = ::exitApplication,
            windowState = windowState,
        )
    }
}