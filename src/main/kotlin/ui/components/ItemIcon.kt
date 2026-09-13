package ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun ItemIcon(itemId: String, modifier: Modifier = Modifier.size(48.dp), showFallback: Boolean = true) {
    var bitmap by remember(itemId) { mutableStateOf<ImageBitmap?>(IconBitmapCache.peek(itemId)) }
    var loading by remember(itemId) { mutableStateOf(bitmap == null) }

    LaunchedEffect(itemId) {
        if (bitmap == null) {
            loading = true
            bitmap = IconBitmapCache.load(itemId)
            loading = false
        }
    }

    when {
        bitmap != null -> Image(bitmap = bitmap!!, contentDescription = itemId, modifier = modifier)
        loading -> CircularProgressIndicator(
            modifier = modifier,
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
        else -> if (showFallback) {
            Text("?", modifier = modifier, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        } else {
            Box(modifier = modifier)
        }
    }
}
