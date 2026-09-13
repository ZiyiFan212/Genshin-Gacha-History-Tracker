package core

import java.io.File

object NodeLocator {

    /**
     * Find the executable Node.js in the directory and return the absolute path.
     * Prioritizing the node in ./gradle directory.
     */
    fun resolveNodejsEnvironment(): String {
        val userDir = File(System.getProperty("user.dir"))
        val nodeDir = userDir.resolve(".gradle/nodejs")
        if (nodeDir.isDirectory) {
            val latestBinaryNodeFile = nodeDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("node-") }
                ?.maxByOrNull { it.name }

            if (latestBinaryNodeFile != null) {
                val nodeExecutablePath = if (System.getProperty("os.name").lowercase().contains("win")){
                    latestBinaryNodeFile.resolve("node.exe")
                } else {
                    latestBinaryNodeFile.resolve("bin/node")
                }

                if (nodeExecutablePath.exists() && nodeExecutablePath.canExecute()) {
                    return nodeExecutablePath.absolutePath
                }
            }

        }
        return "node"
    }

    fun resolveProxyScriptPath(): File {
        return File(System.getProperty("user.dir")).resolve("proxy/proxy.js")
    }


}