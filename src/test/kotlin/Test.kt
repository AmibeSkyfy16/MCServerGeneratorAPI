import ch.skyfy.mcserverlauncher.MCServerLauncher.Companion.findPathToJavaExe
import com.gargoylesoftware.htmlunit.*
import com.gargoylesoftware.htmlunit.html.HtmlDivision
import com.gargoylesoftware.htmlunit.html.HtmlElement
import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.gargoylesoftware.htmlunit.util.WebConnectionWrapper
import java.io.IOException
import kotlin.test.Test


class Test {

    @Throws(IOException::class)
    fun clickTwice(webClient: WebClient, clickable: HtmlElement): Page? {
        val originalConnection = webClient.webConnection
        val wcw: WebConnectionWrapper = object : WebConnectionWrapper(originalConnection) {
            private var m_requested = false

            @Throws(IOException::class)
            override fun getResponse(request: WebRequest): WebResponse {
                if (m_requested) {
                    throw RuntimeException("Double request!")
                }
                m_requested = true
                clickable.click<Page>()
                webClient.webConnection = originalConnection
                return super.getResponse(request)
            }
        }
        webClient.webConnection = wcw
        return clickable.click<Page>()
    }

    @Test
    fun test() {

        if(0 == 0)return
        val webClient = WebClient(BrowserVersion.CHROME)
        webClient.options.isCssEnabled = false;
        webClient.options.isThrowExceptionOnFailingStatusCode = false;
        webClient.options.isThrowExceptionOnScriptError = false;
        webClient.options.isPrintContentOnFailingStatusCode = false;
        println("js: " + webClient.options.isJavaScriptEnabled)
        webClient.options.isJavaScriptEnabled = true
        try {
//            val page = webClient.getPage<HtmlPage>("https://fabricmc.net/use/server/")
            val page = webClient.getPage<HtmlPage>("https://github.com/yushijinhun/minecraft-version-json-history/tree/master/history/release")

            println("Text TItle ${page.titleText}")

            try {
//                val links = page.anchors
//                for (link in links) {
//                    val href = link.hrefAttribute
//                    println("Link: $href")
//                }


//                val anchors: List<Any?> = page.getByXPath("//option")
                val anchors: List<Any?> = page.getByXPath("//div[@class='fabric-component']")
                anchors.forEach {
                    println("\t$it")
                    if(it is HtmlDivision){
                        val newPage = clickTwice(webClient, it)
                        it.page.getByXPath<Any?>("//option")
                        println()
                    }
                }
            } catch (e: Exception) {
            }

            webClient.currentWindow.jobManager.removeAllJobs()
            webClient.close()
        } catch (e: IOException) {
            println("An error occurred: $e")
        }
    }

    @Test
    fun test2(){
val flags = """
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

        val p = flags.replaceRange(flags.indexOf("-Xms") + 4, flags.indexOf("M"), "1111")

//        findPathToJavaExe("bellsoft-jre17.0.6+10-windows-amd64")

        if(0 == 0)return
        val mc = listOf<String>("1.11.3", "1.13.3", "1.14.1", "1.15.4", "1.16.4")
        val v = mc.filter {
            it.split(".")[1].toInt() >= 14
        }

        println()
    }

}