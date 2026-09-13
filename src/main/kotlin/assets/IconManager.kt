package assets

import java.io.File
import java.net.JarURLConnection
import java.util.MissingResourceException
import java.util.jar.JarFile

object IconManager {

    private const val ICON_DIR = "/gacha-assets/icons/"
    private const val FALLBACK_ICON = "/gacha-assets/fallback.png"

    private val cache = HashMap<String, String>()
    private var fallbackPath: String = FALLBACK_ICON
    private var initialized = false

    fun isInitialized(): Boolean = initialized

    fun init(): Result<Unit> = runCatching {
        val classLoader = IconManager::class.java.classLoader
        resolveResource(FALLBACK_ICON)
            ?: throw MissingResourceException(
                "Fallback icon not found at $FALLBACK_ICON", "Icon", FALLBACK_ICON
            )
        fallbackPath = FALLBACK_ICON
        cache.clear()

        runCatching { enumerateIconIds(classLoader) }
            .getOrDefault(emptySet())
            .forEach { itemId -> cache[itemId] = "$ICON_DIR$itemId.png" }

        initialized = true
    }

    fun get(itemId: String): String {
        check(initialized) { "Icon has not been initialized" }
        return cache.getOrDefault(itemId, fallbackPath)
    }

    private fun resolveResource(path: String): java.net.URL? {
        val normalized = path.trimStart('/')
        return classLoaderResource(normalized)
    }

    private fun classLoaderResource(normalized: String): java.net.URL? {
        val classLoader = IconManager::class.java.classLoader
        return classLoader.getResource(normalized)
    }

    private fun enumerateIconIds(classLoader: ClassLoader): Set<String> {
        val ids = mutableSetOf<String>()
        val resourceName = ICON_DIR.trimStart('/')

        classLoader.getResources(resourceName).toList().forEach { url ->
            when (url.protocol) {
                "file" -> {
                    File(url.toURI()).listFiles()
                        ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
                        ?.forEach { file -> ids.add(file.nameWithoutExtension) }
                }
                "jar" -> {
                    val connection = url.openConnection() as JarURLConnection
                    connection.jarFile.use { jar -> collectIdsFromJar(jar, resourceName, ids) }
                }
            }
        }

        return ids
    }

    private fun collectIdsFromJar(jar: JarFile, prefix: String, ids: MutableSet<String>) {
        jar.entries().asSequence()
            .filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".png") }
            .forEach { entry ->
                val fileName = entry.name.removePrefix(prefix).removePrefix("/")
                ids.add(fileName.removeSuffix(".png"))
            }
    }
}
