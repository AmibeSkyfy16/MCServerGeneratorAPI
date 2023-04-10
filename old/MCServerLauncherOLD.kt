package ch.skyfy.mcserverlauncher

import org.apache.commons.io.FileUtils
import org.buildobjects.process.ProcBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Paths
import javax.swing.plaf.FileChooserUI

class MCServerLauncherOLD {


    enum class Flags(val flags: String) {
        BASICS(
            """
            -server
            -Dfile.encoding=UTF-8
            -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
            """.trimIndent()
        ),

        /**
         * Aikar's Flags - BELOW 12GB
         * from this: https://flags.sh/
         */
        FLAGS_1(
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
        FLAGS_2(
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
        );

        fun getAsSingleLine() : String{
            return flags.replace("\n", " ")
        }
    }

    enum class Java(val location: String) {
        BELLSOFT_STANDARD_JRE_17_0_6("E:\\tmp\\githubProject\\MCServerLauncher\\src\\main\\resources\\bellsoft-jre17.0.6+10-windows-amd64\\jre-17.0.6"),
        BELLSOFT_STANDARD_JRE_19_0_2("E:\\tmp\\githubProject\\MCServerLauncher\\src\\main\\resources\\bellsoft-jre19.0.2+9-windows-amd64\\jre-19.0.2")
    }

    companion object {

        val l = arrayOf(
            "",
            ""
        )

        // Working command
        val b1 = ProcBuilder(
            "C:\\Program Files\\PowerShell\\7\\pwsh.exe",
            "-Command", "Start-Process", "wt.exe", "-ArgumentList \"powershell.exe\", \"-NoExit\", \"-Command\", \"dir\""
        )
        val b2 = ProcBuilder(
            "wt.exe",
            "java", "-jar", "\"E:\\test\\Latest Fabric__FabricMC[0.14.17-0.11.1]__[1.19.3]\\server\\server.jar\""
        )

        fun gen(){

            Java.values().forEach {



            }

            val java = Java.BELLSOFT_STANDARD_JRE_17_0_6
            val flags = Flags.FLAGS_1
            val javaExec = Paths.get(java.location).resolve("bin").resolve("java.exe")
            val content = """
                cd /d %~dp0../server
                "$javaExec" ${flags.getAsSingleLine()} ${Flags.BASICS.getAsSingleLine()} -jar "E:\test\Latest Fabric__FabricMC[0.14.17-0.11.1]__[1.19.3]\server\server.jar" --nogui
            """.trimIndent()

            val folder = Paths.get("E:\\test\\benchmark\\launcher")
            val file = folder.resolve("${java.name}_${flags.name}.bat")
            FileUtils.writeStringToFile(file.toFile(), content, Charset.defaultCharset())
        }

        @JvmStatic
        fun main(args: Array<String>) {
            println("main()")
            println(Flags.FLAGS_1.flags.replace("\n", " "))

            gen()

//            val out = ProcBuilder.run("start", "cmd.exe", "/C", "Hello World!");
//            val output = ProcBuilder.run("echo", "Hello World!")
//            val b = ProcBuilder("cmd.exe", "/c", "start", "C:\\Program Files\\PowerShell\\7\\pwsh.exe").withArgs()
//            val b = ProcBuilder("cmd.exe", "/c", "start", "cmd.exe").withArgs()
//            val b = ProcBuilder("C:\\Program Files\\PowerShell\\7\\pwsh.exe", "Start-Process", "wt.exe", "-ArgumentList").withArgs()
//            val b = ProcBuilder("C:\\Program Files\\PowerShell\\7\\pwsh.exe", "-Command", "Start-Process", "wt.exe", "-ArgumentList", "\"C:\\Program Files\\PowerShell\\7\\pwsh.exe\"").withArgs()
//            val b = ProcBuilder("C:\\Program Files\\PowerShell\\7\\pwsh.exe", "-Command", "Start-Process", "wt.exe", "-ArgumentList \"Powershell.exe\", \"-NoExit\", \"-Command\", \"dir\"").withArgs()
//            val b = ProcBuilder("C:\\Program Files\\PowerShell\\7\\pwsh.exe", "-Command", "Start-Process", "wt.exe", "-ArgumentList \"java\", \"\"-jar\", \"E:\\test\\Latest Fabric__FabricMC[0.14.17-0.11.1]__[1.19.3]\\server\\server.jar\"\" ")
//            val b = ProcBuilder("cmd.exe", "/c", "start", "C:\\Program Files\\PowerShell\\7\\pwsh.exe",).withArgs()
//            val b = ProcBuilder("cmd.exe", "/c", "start", "wt.exe").withArgs()
//            val b = ProcBuilder("cmd.exe", "/c", "powershell.exe -Command dir")
//            val b = ProcBuilder("wt.exe", "-p", "Latest-Powershell", "\"C:\\Program Files\\PowerShell\\7\\pwsh.exe\"", "-NoExit", "-Command", "dir")
            val b = ProcBuilder("wt.exe", "\"C:\\Program Files\\PowerShell\\7\\pwsh.exe\"", "-NoExit", "-Command", "java", "-jar", "'E:\\test\\Latest Fabric__FabricMC[0.14.17-0.11.1]__[1.19.3]\\server\\server.jar'")
//            val b = ProcBuilder("wt.exe", "java", "-jar", "\"E:\\test\\Latest Fabric__FabricMC[0.14.17-0.11.1]__[1.19.3]\\server\\server.jar\"")
//            val b = ProcBuilder("wt.exe", "Start-Process").withArgs()
//                b1
                .withWorkingDirectory(Paths.get("C:\\temp").toFile())
//                .run()
//            test()

            val homeDirectory = System.getProperty("user.home")

//            Runtime.getRuntime().exec(String.format("cmd.exe /c dir %s", homeDirectory))
//            Runtime.getRuntime().exec(String.format("cmd.exe /c pause"))

//            val pb = ProcBuilder("C:\\Program Files\\PowerShell\\7\\pwsh.exe", "-NoExit","-command", "echo s")
//            pb.redirectErrorStream(true)
//            val process: Process = pb.start()
//            val inStreamReader = BufferedReader(
//                InputStreamReader(process.inputStream)
//            )
//
//            while (inStreamReader.readLine() != null) {
//                //do something with commandline output.
//            }
        }

        fun test() {
//            ProcBuilder("java", "-jar", "server.jar")
//            ProcBuilder("cmd.exe", "/k", "powershell.exe -Command dir")
//            ProcBuilder("wt", "ping", "learn.microsoft.com")
//            ProcBuilder("wt")
            ProcBuilder("wt", "-p", "Latest-Powershell", "Get-ChildItem")
//            ProcBuilder("cmd.exe", "/k", "powershell.exe", "-Command", "dir")
//                b1
                .withWorkingDirectory(Paths.get("E:\\test\\Latest Fabric__FabricMC[0.14.17-0.11.1]__[1.19.3]\\server").toFile())
                .withNoTimeout()
//                .withArgs("line1\\nline2")
                .withOutputConsumer { stream ->
                    val reader = BufferedReader(InputStreamReader(stream))
                    println("stdout: " + reader.readLine())

                }
                .withErrorConsumer { stream ->
                    val reader = BufferedReader(InputStreamReader(stream))
                    println("error: " + reader.readLine())
                }
//                .withTimeoutMillis(2000)
                .run()
        }

    }


}