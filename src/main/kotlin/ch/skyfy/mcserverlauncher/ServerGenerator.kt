package ch.skyfy.mcserverlauncher

import ch.skyfy.mcserverlauncher.MCServerLauncher.Companion.BASE_JAVA_FOLDER
import net.lingala.zip4j.ZipFile
import org.apache.commons.io.FileUtils
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

class ServerGenerator private constructor(
    private val fabricInstallerVersion: String,
    private val fabricLoaderVersion: String,
    private val minecraftVersions: List<String>,
    private val javaVersions: List<MCServerLauncher.Java>,
    private val flags: List<MCServerLauncher.Flag>,
    private val memories: Set<String>,
    private val embedJava: Boolean,
    private val destinationFolder: Path
) {

    data class Builder(
        private var fabricInstallerVersion: String = "0.11.2",
        private var fabricLoaderVersion: String = "0.14.21",
        private var minecraftVersions: List<String> = listOf("1.19.4"),
        private var javaVersions: List<MCServerLauncher.Java> = MCServerLauncher.Java.values().toList(),
        private var flags: List<MCServerLauncher.Flag> = MCServerLauncher.Flag.values().toList(),
        private var memories: Set<String> = setOf("4096", "8192", "16384"),
        private var embedJava: Boolean = true,
        private var destinationFolder: Path = Paths.get("C:\\temp\\juin-2023-serverGeneratorTest")
    ) {
        fun fabricInstallerVersion(fabricInstallerVersion: String) = apply { this.fabricInstallerVersion = fabricInstallerVersion }
        fun fabricLoaderVersion(fabricLoaderVersion: String) = apply { this.fabricLoaderVersion = fabricLoaderVersion }
        fun minecraftVersions(minecraftVersions: List<String>) = apply { this.minecraftVersions = minecraftVersions }
        fun javaVersions(javaVersions: List<MCServerLauncher.Java>) = apply { this.javaVersions = javaVersions }
        fun flags(flags: List<MCServerLauncher.Flag>) = apply { this.flags = flags }
        fun memories(memories: Set<String>) = apply { this.memories = memories }
        fun embedJava(embedJava: Boolean) = apply { this.embedJava = embedJava }
        fun destinationFolder(destinationFolder: Path) = apply { this.destinationFolder = destinationFolder }

        fun build() = ServerGenerator(fabricInstallerVersion, fabricLoaderVersion, minecraftVersions, javaVersions, flags, memories, embedJava, destinationFolder)
    }

    fun generate() {
        minecraftVersions.forEach { mcVersion ->

            val serverRootFolder = destinationFolder.resolve("fabricmc__${mcVersion}__${fabricLoaderVersion}_${fabricInstallerVersion}")

            if (serverRootFolder.exists()) {
                println("CANNOT CREATE A MINECRAFT SERVER WITH NAME ${serverRootFolder.absolutePathString()} BECAUSE ONE IS ALREADY PRESENT [SKIP]")
                return@forEach
            }

            serverRootFolder.createDirectory()

            val launcherFolder = serverRootFolder.resolve("launcher")
            val serverFolder = serverRootFolder.resolve("server")
            val javaFolder = if (embedJava) serverRootFolder.resolve("java") else BASE_JAVA_FOLDER

            launcherFolder.createDirectory()
            serverFolder.createDirectory()
            if (embedJava && javaFolder.notExists()) javaFolder.createDirectory()

            // Create the eula.txt (set to true) file inside the serverFolder (path example: C:\\temp\\fabricmc__1.19.4_0.14.19_0.11.2\server\eula.txt)
            createEula(serverFolder)

            // Download the minecraftServer.jar (path example: C:\\temp\\fabricmc__1.19.4_0.14.19_0.11.2\server\fabric-server-mc.1.19.4-loader.0.14.19-launcher.0.11.2)
            val fileName = downloadMinecraftServerJar(mcVersion, serverFolder)
            if (fileName == null) {
                println("An error occurred with the downloading of the minecraft server jar")
                return@forEach
            }

            // We will create a start.bat foreach java versions passed in parameters
            javaVersions.forEach first@{ java ->

                // Check if the version of minecraft can be used with this version of Java
                if (!java.canUse.invoke(mcVersion, MCServerLauncher.MINECRAFT_VERSIONS_FOR_FABRICMC)) {
                    println("JAVA ${java.filename} CANNOT BE USED WITH MINECRAFT VERSION $mcVersion")
                    return@first
                }

                downloadAndExtractAllJava(javaFolder, java)


                // We also will create a start.bat foreach flags passed in parameters (like one for aikar's Flags, shenandoah Flags, etc.)
                flags.forEach second@{ flag ->
                    if (flag == MCServerLauncher.Flag.BASICS) return@second

                    if (!flag.compatibleJava.contains(java)) {
                        println("Flags $flag is not compatible with java $java")
                        return@second
                    }

                    val javaExec = findPathToJavaExe(java.filename.substringBeforeLast("."), javaFolder)

                    // And finally we also create a specific start.bat foreach different memory profile (2GB, 4GB, etc.)
                    memories.forEach third@{ mem ->

                        // For some flags like aikar's flags, there is one that cannot be used with more than 12GB of memory
                        // So we check if we can create or not a working start.bat with this memory and this flags
                        if (flag.memory.info == MCServerLauncher.MemoryInfo.BELOW && mem.toInt() > flag.memory.memory) return@third
                        if (flag.memory.info == MCServerLauncher.MemoryInfo.ABOVE_EQUAL && mem.toInt() < flag.memory.memory) return@third

                        val content = """
                                    cd /d %~dp0../server
                                    
                                    chcp 65001
                                    
                                    :loop
                                    "$javaExec" ${flag.updateMemoryAndGetAsSingleLine(mem)} ${MCServerLauncher.Flag.BASICS.getAsSingleLine()} -jar "${fileName.name}" --nogui
                                    
                                    echo Restarting the server in 30 seconds. Press Ctrl+C to stop
                                    timeout /t 30 /nobreak
                                    goto loop
                                """.trimIndent()

                        val file = launcherFolder.resolve("${java.vendor}-${java.packageType}-${java.version}_${flag.fName}_${mem}MB.bat")
                        FileUtils.writeStringToFile(file.toFile(), content, Charset.defaultCharset())
                    }

                }

            }
        }

    }

    private fun createEula(folder: Path) {
        val content = "eula=true"
        FileUtils.writeStringToFile(folder.resolve("eula.txt").toFile(), content, Charset.defaultCharset())
    }

    /**
     * return true if the file is successfully downloaded
     */
    private fun downloadMinecraftServerJar(mcVersion: String, serverFolder: Path): Path? {
        val url = "https://meta.fabricmc.net/v2/versions/loader/%s/%s/%s/server/jar".format(mcVersion, fabricLoaderVersion, fabricInstallerVersion)

        return FilesUtils.downloadFile(URL(url), serverFolder)
    }

    private fun downloadAndExtractAllJava(javaFolder: Path, java: MCServerLauncher.Java) {
        Thread.currentThread().contextClassLoader.getResource("java/${java.filename}")?.let { url ->
            val javaAsZipFile = javaFolder.resolve(url.toExternalForm().substringAfterLast("/")).toFile()
            val javaWithoutExt = javaFolder.resolve(url.toExternalForm().substringAfterLast("/").substringBeforeLast("."))
            if (javaWithoutExt.exists()) return
            FileUtils.copyURLToFile(url, javaAsZipFile)
            ZipFile(javaAsZipFile).extractAll(javaWithoutExt.absolutePathString())
            javaAsZipFile.delete()
        }
    }

    fun findPathToJavaExe(javaOriginalName: String, javaFolder: Path): Path {
        val p = javaFolder.resolve(javaOriginalName)
        if (p.exists()) {
            p.toFile().walk(FileWalkDirection.TOP_DOWN).onEnter {
                return@onEnter true
            }.forEach {
                if (it.name == "java.exe") return it.toPath()
            }
        }
        return p.resolve("FAILED")
    }

}