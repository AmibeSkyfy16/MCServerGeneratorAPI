package ch.skyfy.mcserverlauncher

import java.io.FileOutputStream
import java.net.URL
import java.net.URLDecoder
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

object FilesUtils {

    data class FileResource(
        val name: String,
        val url: URL
    )

    fun getFileResourcesForDirnameFromCP(directoryName: String): List<FileResource> {
        val filenames = mutableListOf<FileResource>()
        val url = Thread.currentThread().contextClassLoader.getResource(directoryName) ?: return filenames.toList()

        if (url.protocol == "file") {
            val file = Paths.get(url.toURI()).toFile() ?: return filenames.toList()
            file.listFiles()?.let { files ->
                files.forEach { file ->
                    filenames.add(FileResource(file.name, file.toURI().toURL()))
                }
            }
        } else if (url.protocol == "jar") {
            val dirname = "$directoryName/"
            val path = url.path
            val jarPath = path.substring(5, path.indexOf("!"))
            JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name())).use { jar ->
                val entries = jar.entries()
                while(entries.hasMoreElements()){
                    val entry = entries.nextElement()
                    val name = entry.name
                    if(name.startsWith(dirname) && dirname != name){
                        Thread.currentThread().contextClassLoader.getResource(name)?.let { resource ->
                            filenames.add(FileResource(name.substringAfter(dirname), resource))
                        }
                    }
                }
            }
        }
        return filenames
    }

    /**
     * https://stackoverflow.com/questions/3923129/get-a-list-of-resources-from-classpath-directory
     */
    fun downloadFile(url: URL, outputFolder: Path, replaceIfExist: Boolean = true): Path? {
        try {
            val field = url.openConnection().getHeaderField("Content-Disposition")

            val fileName = if (field != null && field.contains("filename="))
                field.substring(field.indexOf("filename=\"") + 10, field.count() - 1)
            else
                url.toString().substringAfterLast("/")

            val outputFileName = outputFolder.resolve(fileName)
            if(outputFileName.exists() && !replaceIfExist){
                return outputFileName
            }

            url.openStream().use {
                Channels.newChannel(it).use { rbc ->
                    FileOutputStream(outputFileName.absolutePathString()).use { fos ->
                        fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE)
                    }
                }
            }
            return outputFileName
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

}