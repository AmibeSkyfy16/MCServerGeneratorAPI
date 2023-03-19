package ch.skyfy.mcserverlauncher

import ch.skyfy.mcserverlauncher.FilesUtils.downloadFile
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.lingala.zip4j.ZipFile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.io.FileUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.*


@Suppress("SameParameterValue")
class MCServerLauncher {

    data class Memory(val info: MemoryInfo, val memory: Int)

    enum class MemoryInfo() {
        BELOW(),
        ABOVE_EQUAL(),
        ANY();

        fun memory(memory: Int) = Memory(this, memory)
        fun any() = Memory(this, -1)
    }

    enum class Flag(
        val fName: String,
        val memory: Memory,
        val flags: String
    ) {

        // To add maybe -> -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
        BASICS(
            "Basics",
            MemoryInfo.ANY.any(),
            """
            -server
            -Dfile.encoding=UTF-8
            """.trimIndent()
        ),

        /**
         * Aikar's Flags - BELOW 12GB
         * from this: https://flags.sh/
         */
        AIKARS_1_4GB(
            "Aikars#1-Below-12GB",
            MemoryInfo.BELOW.memory(12288),
            """
            -Xms4096M
            -Xmx4096M
            --add-modules=jdk.incubator.vector
            -XX:+UseG1GC
            -XX:+DisableExplicitGC
            -XX:+PerfDisableSharedMem
            -XX:+ParallelRefProcEnabled
            -XX:MaxGCPauseMillis=200
            -XX:+UnlockExperimentalVMOptions
            -XX:+AlwaysPreTouch
            -XX:G1HeapWastePercent=5
            -XX:G1MixedGCCountTarget=4
            -XX:InitiatingHeapOccupancyPercent=15
            -XX:G1MixedGCLiveThresholdPercent=90
            -XX:G1RSetUpdatingPauseTimePercent=5
            -XX:SurvivorRatio=32
            -XX:MaxTenuringThreshold=1
            -XX:G1NewSizePercent=30
            -XX:G1MaxNewSizePercent=40
            -XX:G1ReservePercent=20
            -XX:G1HeapRegionSize=8M
            -Dusing.aikars.flags=https://mcflags.emc.gs
            -Daikars.new.flags=true
        """.trimIndent()
        ),

        /**
         * Aikar's Flags - EQUAL OR ABOVE 12GB
         * from this: https://flags.sh/
         */
        AIKARS_1_16GB(
            "Aikars#1-AboveEqual-12GB",
            MemoryInfo.ABOVE_EQUAL.memory(12288),
            """
            -Xms16384M
            -Xmx16384M
            --add-modules=jdk.incubator.vector
            -XX:+UseG1GC
            -XX:+DisableExplicitGC
            -XX:+PerfDisableSharedMem
            -XX:+ParallelRefProcEnabled
            -XX:MaxGCPauseMillis=200
            -XX:+UnlockExperimentalVMOptions
            -XX:+AlwaysPreTouch
            -XX:G1HeapWastePercent=5
            -XX:G1MixedGCCountTarget=4
            -XX:InitiatingHeapOccupancyPercent=15
            -XX:G1MixedGCLiveThresholdPercent=90
            -XX:G1RSetUpdatingPauseTimePercent=5
            -XX:SurvivorRatio=32
            -XX:MaxTenuringThreshold=1
            -XX:G1NewSizePercent=40
            -XX:G1MaxNewSizePercent=50
            -XX:G1ReservePercent=15
            -XX:G1HeapRegionSize=16M
            -Dusing.aikars.flags=https://mcflags.emc.gs
            -Daikars.new.flags=true
        """.trimIndent()
        ),

        DEFAULT(
            "DEFAULT",
            MemoryInfo.ANY.any(),
            """
            -Xms16384M
            -Xmx16384M
            """.trimIndent()
        );

        fun getAsSingleLine() = flags.replace("\n", " ")

        fun updateMemoryAndGetAsSingleLine(memory: String): String {
            val splits = flags.split("\n")
            val newXms = "-Xms${memory}M"
            val newXmx = "-Xmx${memory}M"

            val newStr = StringBuilder("")
            splits.forEachIndexed { index, s ->
                when (index) {
                    0 -> newStr.append("$newXms ")
                    1 -> newStr.append("$newXmx ")
                    else -> newStr.append("$s ")
                }
            }
            return newStr.toString()
        }
    }

    enum class Java(
        val vendor: String,
        val version: String,
        val packageType: String,
        val filename: String,
        val canUse: (String, List<String>) -> Boolean
    ) {
        BELLSOFT_STANDARD_JRE_8U362_PLUS_9("BELLSOFT", "8u362+9", "STANDARD-JRE", "bellsoft-jre8u362+9-windows-amd64.zip", canUse = { mcVersionToCheck, mcVersionList -> canUse(8, mcVersionToCheck, mcVersionList) }),
        BELLSOFT_STANDARD_JRE_16_0_2_PLUS_7("BELLSOFT", "16.0.2+7", "STANDARD-JRE", "bellsoft-jre16.0.2+7-windows-amd64.zip", canUse = { mcVersionToCheck, mcVersionList -> canUse(16, mcVersionToCheck, mcVersionList) }),
        BELLSOFT_STANDARD_JRE_17_0_6_PLUS_10("BELLSOFT", "17.0.6+10", "STANDARD-JRE", "bellsoft-jre17.0.6+10-windows-amd64.zip", canUse = { mcVersionToCheck, mcVersionList -> canUse(17, mcVersionToCheck, mcVersionList) }),
        BELLSOFT_STANDARD_JRE_18_0_2_PLUS_1("BELLSOFT", "18.0.2+1", "STANDARD-JRE", "bellsoft-jre18.0.2.1+1-windows-amd64.zip", canUse = { mcVersionToCheck, mcVersionList -> canUse(18, mcVersionToCheck, mcVersionList) }),
        BELLSOFT_STANDARD_JRE_19_0_2_PLUS_9("BELLSOFT", "19.0.2+9", "STANDARD-JRE", "bellsoft-jre19.0.2+9-windows-amd64.zip", canUse = { mcVersionToCheck, mcVersionList -> canUse(19, mcVersionToCheck, mcVersionList) });

        fun getFormattedName(): String {
            return name.replace("_", "-")
        }
    }

    enum class Loader(val loaderName: String) {
        FABRICMC("FabricMC"),
        QUILTMC("QuiltMC"),
        SPIGOTMC("SpigotMC");
    }

    class Folder(val name: String, private var childs: MutableList<Folder> = mutableListOf()) {

        fun then(child: Folder): Folder {
            this.childs.add(child)
            return this
        }

        fun createDirs(outputFolder: Path, name: String = this.name, childs: MutableList<Folder> = this.childs): Folder {
            val f = outputFolder.resolve(name)
            if (f.notExists()) f.createDirectory()
            if (childs.isNotEmpty()) {
                childs.forEach {
                    it.createDirs(f, it.name, it.childs)
                }
            }
            return this
        }

        fun getFolder(folderName: String, outputFolder: Path, name: String = this.name, childs: MutableList<Folder> = this.childs): Path? {
            val outputFolderNext = outputFolder.resolve(name)
            if (name == folderName) {
                return outputFolderNext
            } else {
                if (childs.isNotEmpty()) {
                    childs.forEach {
                        val r = getFolder(folderName, outputFolderNext, it.name, it.childs)
                        if (r != null) return r
                    }
                }
            }
            return null
        }

        fun getRoot(outputFolder: Path): Path {
            return outputFolder.resolve(name)
        }

        fun copy(rootFolderName: String): Folder {
            return Folder(rootFolderName, childs)
        }

    }

    companion object {
        private val USER_HOME: String = System.getProperty("user.home")
        private val APPLICATION_DIR: Path = Paths.get(USER_HOME, "MCServerLauncher")

        private val JAVA_MINECRAFT_COMPATIBILITY = mapOf(
            Pair("17w13a", "21w18a") to "8", // java 8 or greater
            Pair("21w19a", "1.18-pre1") to "16", // java 16 or greater
            Pair("1.18-pre2", "1.19.4") to "17", // java 17 or greater
        )

        val MINECRAFT_VERSIONS = getMinecraftVersion()
        private val MINECRAFT_VERSIONS_FOR_FABRICMC = getMinecraftVersionForFabricMC()
        private val FABRICMC_LOADER_VERSION = getFabricLoaderVersion()
        private val FABRICMC_INSTALLER_VERSION = getFabricInstallerVersion()

        //        val MEMORY = listOf<String>("256", "512", "1024", "2048", "4096", "8192", "16384", "24576", "32768")
        private val MEMORIES = setOf("512", "1024", "2048", "4096", "8192", "16384")

        private const val OUTPUT_FOLDER = "E:\\test\\benchmark"
        private val OUTPUT_FOLDER_PATH = Paths.get(OUTPUT_FOLDER)

        private val folderStructure: Folder = Folder("myServer")
            .then(Folder("launcher"))
            .then(Folder("server"))


        /**
         * return true or false for example if minecraft 1.18.1 can be use with java 16 or not
         */
        fun canUse(javaVersion: Int, mcVersion: String, minecraftVersionList: List<String>): Boolean {
            var returned = false
            minecraftVersionList.forEachIndexed { index, s ->
                if (s == mcVersion) {
                    JAVA_MINECRAFT_COMPATIBILITY.forEach { (t, u) ->
                        val index1 = if (minecraftVersionList.indexOf(t.first) == -1) Int.MAX_VALUE else minecraftVersionList.indexOf(t.first)
                        val index2 = if (minecraftVersionList.indexOf(t.second) == -1) Int.MIN_VALUE else minecraftVersionList.indexOf(t.second)

                        if (index in index2..index1)
                            if (javaVersion >= u.toInt())
                                returned = true
                    }
                }
            }
            return returned
        }

        private fun getJavaVersionToUse(mcVersion: String, minecraftVersionList: List<String>): Int? {
            minecraftVersionList.forEachIndexed { index, s ->
                if (s == mcVersion) {
                    JAVA_MINECRAFT_COMPATIBILITY.forEach { (t, u) ->
                        val index1 = if (minecraftVersionList.indexOf(t.first) == -1) Int.MAX_VALUE else minecraftVersionList.indexOf(t.first)
                        val index2 = if (minecraftVersionList.indexOf(t.second) == -1) Int.MIN_VALUE else minecraftVersionList.indexOf(t.second)

                        if (index in index2..index1)
                            return u.toInt()
                    }
                }
            }
            return null
        }

        /**
         * https://github.com/yushijinhun/minecraft-version-json-history/tree/master/history/release
         * https://reqbin.com/
         */
        private fun getMinecraftVersion(): List<String> {
            val client = OkHttpClient()

            val request: Request = Request.Builder()
                .url("https://api.github.com/repos/yushijinhun/minecraft-version-json-history/contents/history/release")
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.body.string()
                val jsonArray = Json.decodeFromString<JsonArray>(json)
                return jsonArray.map { it.jsonObject["version"]?.jsonPrimitive.toString().replace("\"", "") }
            }
        }

        private fun getMinecraftVersionForFabricMC(): List<String> {
            val client = OkHttpClient()

            val request: Request = Request.Builder()
                .url("https://meta.fabricmc.net/v2/versions/game")
//                .url("https://api.github.com/repos/yushijinhun/minecraft-version-json-history/contents/history/release")
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.body.string()
                val jsonArray = Json.decodeFromString<JsonArray>(json)
                return jsonArray.map { it.jsonObject["version"]?.jsonPrimitive.toString().replace("\"", "") }
            }
        }

        /**
         * https://maven.fabricmc.net/net/fabricmc/
         * https://meta.fabricmc.net/v2/versions/loader/
         * Event Listeners tab -> Server.be103d36.js
         */
        private fun getFabricLoaderVersion(): List<String> {
            val client = OkHttpClient()

            val request: Request = Request.Builder()
                .url("https://meta.fabricmc.net/v2/versions/loader")
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.body.string()
                val jsonArray = Json.decodeFromString<JsonArray>(json)
                return jsonArray.map { it.jsonObject["version"]?.jsonPrimitive.toString().replace("\"", "") }
            }
        }

        private fun getFabricInstallerVersion(): List<String> {
            val client = OkHttpClient()

            val request: Request = Request.Builder()
                .url("https://meta.fabricmc.net/v2/versions/installer")
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.body.string()
                val jsonArray = Json.decodeFromString<JsonArray>(json)
                return jsonArray.map { it.jsonObject["version"]?.jsonPrimitive.toString().replace("\"", "") }
            }
        }

        private fun createDirsStructure(path: Path) {
            if (path.notExists()) path.createDirectories()
            folderStructure.createDirs(path)
        }

        private fun createEula(folder: Path?) {
            val content = "eula=true"
            folder?.let {
                FileUtils.writeStringToFile(it.resolve("eula.txt").toFile(), content, Charset.defaultCharset())
            }
        }

        enum class VersionType {
            SNAPSHOT,
            RELEASE,
            PRE_RELEASE,
            RELEASE_CANDIDATE,
            EXPERIMENTAL,
            COMBAT,
            OTHER
        }

        private fun getVersionType(mcVersion: String): VersionType {

            // Check for experimental version
            if (mcVersion.contains("experimental")) {
                return VersionType.EXPERIMENTAL
            }

            // Check for experimental version
            if (mcVersion.contains("combat")) {
                return VersionType.COMBAT
            }

            // Check for a snapshot (include april snapshot like 22w13oneblockatatime)
            if (
                mcVersion[0].isDigit() && mcVersion[1].isDigit()
                && mcVersion[2] == 'w'
                && mcVersion[3].isDigit() && mcVersion[4].isDigit()
                && mcVersion[5].isLetter()
            ) {
                return VersionType.SNAPSHOT
            }

            // Check for release, pre-release or release candidate version
            val splits = if(mcVersion.contains(" Pre-Release")) mcVersion.substringBefore(" ").split(".") else  mcVersion.substringBefore("-").split(".")
            if (
                (splits.size == 2 && splits[0].toCharArray().all { it.isDigit() } && splits[1].toCharArray().all { it.isDigit() })
                ||
                (splits.size == 3 && splits[0].toCharArray().all { it.isDigit() } && splits[1].toCharArray().all { it.isDigit() } && splits[2].toCharArray().all { it.isDigit() })
            ) {
                if (mcVersion.contains("-pre") || mcVersion.contains("Pre-Release")) {
                    return VersionType.PRE_RELEASE
                } else if (mcVersion.contains("-rc")) {
                    return VersionType.RELEASE_CANDIDATE
                }
                return VersionType.RELEASE
            }

            // If none found, like for the "3D Shareware v1.34" version
            return VersionType.OTHER
        }

        private fun filterByVersionType(mcVersion: String, vararg versionType: VersionType) : Boolean {
            if(versionType.any { it == getVersionType(mcVersion) }) return true
            return false
        }

        private fun generateSingleFabricMCServer(
            rootFolder: Path,
            mcVersion: String,
            javaVersions: Array<Java> = Java.values(),
            flags: Array<Flag> = Flag.values(),
            memories: Set<String> = MEMORIES,
            fabricLoaderVersion: String = FABRICMC_LOADER_VERSION.first(),
            fabricInstallerVersion: String = FABRICMC_INSTALLER_VERSION.first(),
        ) {
            // Create a folder called with the name of the minecraft version in the rootFolder
            // Example: rootFolder = C:\temp\all-servers, so a folder called C:\temp\all-servers\1.19.3 will appear
            val versionFolder = rootFolder.resolve(mcVersion)
            if (versionFolder.notExists()) versionFolder.createDirectory()

            // Represent how the folders are structured for one fabricmc server
            // something like:
            // C:\temp\all-servers\1.19.3\fabricmc
            // C:\temp\all-servers\1.19.3\fabricmc\launcher -> contain all different start.bat files
            // C:\temp\all-servers\1.19.3\fabricmc\server -> contain files for the server
            val fs = folderStructure.copy(Loader.FABRICMC.loaderName.lowercase()).createDirs(versionFolder)

            // Create the eula.txt file inside 1.19.3\fabricmc\server folder
            createEula(fs.getFolder("server", versionFolder))

            val url = "https://meta.fabricmc.net/v2/versions/loader/%s/%s/%s/server/jar".format(mcVersion, fabricLoaderVersion, fabricInstallerVersion)
            val fileName = downloadFile(URL(url), fs.getFolder("server", versionFolder)!!)

            if (fileName == null) {
                println("No server file for $mcVersion $fabricLoaderVersion, $fabricInstallerVersion")
                return
            }

            // We will create a start.bat foreach java versions passed in parameters
            javaVersions.forEach first@{ java ->

                // Check if the version of minecraft can be used with this version of Java
                if (!java.canUse.invoke(mcVersion, MINECRAFT_VERSIONS_FOR_FABRICMC)) {
                    return@first
                }

//                val launcherFolder = fs.getFolder("launcher", versionFolder)!!
//                javaLauncherFolder = launcherFolder.resolve(java.name)

                // We also will create a start.bat foreach flags passed in parameters (like one for aikar's Flags, shenandoah Flags, etc.)
                flags.forEach second@{ flag ->
                    if (flag == Flag.BASICS) return@second

                    val javaExec = findPathToJavaExe(java.filename.substringBeforeLast("."))

                    // And finally we also create a specific start.bat foreach different memory profile (2GB, 4GB, etc.)
                    memories.forEach third@{ mem ->

                        // For some flags like aikar's flags, there is one that cannot be used with more than 12GB of memory
                        // So we check if we can create or not a working start.bat with this memory and this flags
                        if (flag.memory.info == MemoryInfo.BELOW && mem.toInt() > flag.memory.memory) return@third
                        if (flag.memory.info == MemoryInfo.ABOVE_EQUAL && mem.toInt() < flag.memory.memory) return@third

                        val content = """
                                    cd /d %~dp0../server
                                    
                                    :loop
                                    "$javaExec" ${flag.updateMemoryAndGetAsSingleLine(mem)} ${Flag.BASICS.getAsSingleLine()} -jar "${fileName.name}" --nogui
                                    
                                    echo Restarting the server in 30 seconds. Press Ctrl+C to stop
                                    timeout /t 30 /nobreak
                                    goto loop
                                """.trimIndent()

                        fs.getFolder("launcher", versionFolder)?.let { launcherFolder ->
                            val file = launcherFolder.resolve("${java.vendor}-${java.packageType}-${java.version}_${flag.fName}_${mem}MB.bat")
                            FileUtils.writeStringToFile(file.toFile(), content, Charset.defaultCharset())
                        }
                    }

                }

            }
        }

        private fun generateFabricMCServer() {
            val rootFolder = Paths.get("C:\\temp\\all-servers_test")
            if (rootFolder.notExists()) rootFolder.createDirectories()

//            startAllFabricMCServer(rootFolder)

//            if (0 == 0) return
            MINECRAFT_VERSIONS_FOR_FABRICMC
                .filter { mcVersion ->
                    mcVersion == "1.19.4"
//                    filterByVersionType(mcVersion, VersionType.RELEASE)
//                    filterByVersionType(mcVersion, VersionType.PRE_RELEASE)
//                    filterByVersionType(mcVersion, VersionType.RELEASE_CANDIDATE)
//                    filterByVersionType(mcVersion, VersionType.SNAPSHOT)
//                    filterByVersionType(mcVersion, VersionType.COMBAT)
//                    filterByVersionType(mcVersion, VersionType.EXPERIMENTAL)
//                    filterByVersionType(mcVersion, VersionType.OTHER)
                }
                .forEach { mcVersion -> generateSingleFabricMCServer(rootFolder, mcVersion) }

//            startAllFabricMCServer(rootFolder)
//            copyDefaultFabricMCServerToResources(rootFolder)
        }

        fun findPathToJavaExe(javaOriginalName: String): Path {
            val p = APPLICATION_DIR.resolve("java").resolve(javaOriginalName)
            if (p.exists()) {
                p.toFile().walk(FileWalkDirection.TOP_DOWN).onEnter {
                    return@onEnter true
                }.forEach {
                    if (it.name == "java.exe") return it.toPath()
                }
            }
            return p.resolve("FAILED")
        }

        private fun generateAllServers() {
            Loader.values().forEach { loader ->
                if (loader == Loader.FABRICMC) {
                    generateFabricMCServer()
                }
            }
        }

        private fun downloadAndExtractAllJava() {
            val javaFolder = APPLICATION_DIR.resolve("java")
            if (javaFolder.notExists()) javaFolder.createDirectory()
            Java.values().forEach { j ->
                Thread.currentThread().contextClassLoader.getResource("java/${j.filename}")?.let { url ->
                    val javaAsZipFile = javaFolder.resolve(url.toExternalForm().substringAfterLast("/")).toFile()
                    val javaWithoutExt = javaFolder.resolve(url.toExternalForm().substringAfterLast("/").substringBeforeLast("."))
                    if (javaWithoutExt.exists()) return@forEach
                    FileUtils.copyURLToFile(url, javaAsZipFile)
                    ZipFile(javaAsZipFile).extractAll(javaWithoutExt.absolutePathString())
                    javaAsZipFile.delete()
                }
            }
        }

        private fun genSingleServer(serverFileName: String) {

            createDirsStructure(OUTPUT_FOLDER_PATH)


            Java.values().forEach { java ->
                Flag.values().forEach second@{ flags ->
                    if (flags == Flag.BASICS) return@second

                    val javaExec = findPathToJavaExe(java.filename.substringBeforeLast("."))
                    val content = """
                                    cd /d %~dp0../server
                                    
                                    :loop
                                    "$javaExec" ${flags.getAsSingleLine()} ${Flag.BASICS.getAsSingleLine()} -jar "$serverFileName" --nogui
                                    
                                    echo Restarting the server in 30 seconds. Press Ctrl+C to stop
                                    timeout /t 30 /nobreak
                                    goto loop
                                """.trimIndent()

                    val folder = Paths.get("E:\\test\\benchmark\\launcher")
                    val file = folder.resolve("${java.getFormattedName()}_${flags.fName}.bat")
                    FileUtils.writeStringToFile(file.toFile(), content, Charset.defaultCharset())
                }

            }
        }

        /**
         * Start all FabricMC server in order to create the default server.properties file and other things
         */
        private fun startAllFabricMCServer(rootFolder: Path) {
            rootFolder.listDirectoryEntries().forEach { mcVersionDir ->

                val serverFiles = mcVersionDir.resolve(Loader.FABRICMC.loaderName).resolve("server").toFile().listFiles()
                if (serverFiles != null) {
                    if (serverFiles.size == 2) {
                        println("NOT DONE")
                    } else {
                        println("skip $mcVersionDir")
                        println(serverFiles)
                        return@forEach
                    }
                }

                val javaToUse = getJavaVersionToUse(mcVersionDir.name, MINECRAFT_VERSIONS_FOR_FABRICMC) ?: 17

                val startFile = mcVersionDir.resolve(Loader.FABRICMC.loaderName).resolve("launcher").toFile()
                    .listFiles()?.firstOrNull {
                        // Take only default file (file that are not using specific flags)
                        it.nameWithoutExtension.contains("DEFAULT") && it.nameWithoutExtension.contains("2048MB")
                    } ?: return@forEach

                val pb = ProcessBuilder(*arrayOf("cmd.exe", "/c", startFile.absolutePath))
                val process = pb.start()
                val pid = process.pid()
                val job = CoroutineScope(Dispatchers.IO).launch r@{

                    BufferedReader(InputStreamReader(process.inputStream)).use { input ->
                        var stoppingServerLine = ""
                        var line: String?
                        while (withContext(Dispatchers.IO) { input.readLine() }.also { line = it } != null) {
                            var breakLoop = false
                            line?.let {
                                if (it.contains("Done") && it.contains("For help, type \"help\"")) {
                                    process.outputWriter().write("stop\n")
                                    process.outputWriter().flush()
                                } else if (it.contains("Press Ctrl+C to stop")) {
                                    breakLoop = true
                                } else if (it.contains("Stopping the server")) {
                                    stoppingServerLine = it
                                    val job2 = launch {
                                        delay(5000)
                                    }
                                    job2.invokeOnCompletion { t ->
                                        if (stoppingServerLine == it) {
                                            println("same line after 5000 ms")
                                            this.cancel("as")
                                        }
                                    }

                                }
                            }
                            println(line)
                            if (breakLoop) break
                        }
                        println("end use block")
                    }

                }
                while (job.isActive) {
                }
                println("FINISHED")

                println("killing process $pid")
                Runtime.getRuntime().exec(arrayOf("taskkill", "/PID", pid.toString(), "/F"))
                process.destroyForcibly()
                Thread.sleep(5000)
//                println("isAlive ${process.isAlive}")
//                process.waitFor()
//                println("isAlive ${process.isAlive}")
            }
        }

        /**
         * Utility fun
         */
        private fun copyDefaultFabricMCServerToResources(rootFolder: Path) {
            val fabricMCResourceFolder = Paths.get("E:\\tmp\\githubProject\\MCServerLauncher\\src\\main\\resources\\server\\fabricmc")
            rootFolder.toFile().listFiles()?.forEach {
                if (it.isDirectory) {
                    FileUtils.copyDirectoryToDirectory(it, fabricMCResourceFolder.toFile())
                }
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            if (APPLICATION_DIR.notExists()) APPLICATION_DIR.createDirectories()

            downloadAndExtractAllJava()

//            genSingleServer()
            generateAllServers()
        }


    }


}