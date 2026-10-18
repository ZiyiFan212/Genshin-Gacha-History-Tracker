package ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import assets.IconManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

object IconBitmapCache {
    private const val MAX_ENTRIES = 128
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    suspend fun load(itemId: String): ImageBitmap? = withContext(Dispatchers.IO) {
        peek(itemId)?.let { return@withContext it }
        val decoded = withContext(Dispatchers.IO) { decode(itemId) } ?: return@withContext null
        // when decoding the same ID, first one wins.
        return@withContext synchronized(lock) { cache[itemId] ?: decoded.also { cache[itemId] = it } }
    }

    fun peek(itemId: String): ImageBitmap? = synchronized(lock) { cache[itemId] }

    fun clear() {
        synchronized(lock) { cache.clear() }
    }

    private fun decode(itemId: String): ImageBitmap? = runCatching {
        val path = IconManager.get(itemId).trimStart('/')
        val bytes = IconManager::class.java.classLoader.getResourceAsStream(path)?.readBytes() ?: return null
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}
