package org.librespeed.speedtest.engine

import android.content.Context
import com.fdossena.speedtest.core.Speedtest
import com.fdossena.speedtest.core.serverSelector.TestPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class TestMode(val key: String) {
    STANDARD("standard"), SINGLE("single"), STABILITY("stability"), COMPARE("compare");

    companion object {
        fun fromKey(key: String?): TestMode = entries.find { it.key == key } ?: STANDARD
    }
}

class TestEngine(private val context: Context) {

    private var activeWorker: ModernSpeedtestWorker? = null
    private var selectedServer: TestPoint? = null
    private var testMode: TestMode = TestMode.STANDARD
    private var useProxy: Boolean = false
    private var proxyPort: Int = 7890

    data class Discovery(val servers: List<TestPoint>, val selected: TestPoint?)

    /**
     * Loads the server list (curated high-speed endpoints + custom servers),
     * performs parallel non-blocking HTTP latency checks, and selects the lowest-latency server.
     */
    suspend fun discover(
        customServers: List<TestPoint>,
        onListLoaded: (List<TestPoint>) -> Unit = {}
    ): Discovery = withContext(Dispatchers.IO) {
        val serverList = mutableListOf<TestPoint>()

        // 1. Read bundled curated server list
        val data = readAsset("ServerList.json")?.trim() ?: readAsset("ServerListFallback.json")?.trim()
        if (data != null) {
            runCatching {
                val array = JSONArray(data)
                for (i in 0 until array.length()) {
                    serverList.add(TestPoint(array.getJSONObject(i)))
                }
            }
        }

        // 2. Add user custom servers
        serverList.addAll(customServers)

        if (serverList.isEmpty()) throw IOException("Failed to load server list")
        onListLoaded(serverList)

        // 3. Fast parallel latency probe (strictly bounds total discovery to <= 1.5 seconds)
        val probeJobs = serverList.map { s ->
            async {
                val pingUrl = ModernSpeedtestWorker.resolveUrl(s.server, s.pingURL)
                val res = ModernSpeedtestWorker.probeOnce(
                    urlString = pingUrl,
                    timeoutMs = 1200,
                    useProxy = false,
                    keepAlive = false
                )
                s.setPing(if (res.success) res.rttMs.toFloat() else -1f)
            }
        }

        withTimeoutOrNull(1500L) {
            probeJobs.awaitAll()
        }

        var bestServer: TestPoint? = null
        var bestPing = Float.MAX_VALUE
        for (s in serverList) {
            val p = s.ping
            if (p > 0 && p < bestPing) {
                bestPing = p
                bestServer = s
            }
        }

        val finalSelected = bestServer ?: serverList.firstOrNull()
        Discovery(serverList, finalSelected)
    }

    fun prepare(
        servers: List<TestPoint>,
        selected: TestPoint,
        telemetryEnabled: Boolean,
        mode: TestMode = TestMode.STANDARD,
        configOverrides: JSONObject? = null,
        useProxy: Boolean = false,
        proxyPort: Int = 7890
    ) {
        selectedServer = selected
        testMode = mode
        this.useProxy = useProxy
        this.proxyPort = proxyPort
    }

    fun start(handler: Speedtest.SpeedtestHandler) {
        val server = selectedServer ?: return
        activeWorker?.abort()
        activeWorker = ModernSpeedtestWorker(
            server = server,
            mode = testMode,
            handler = handler,
            useProxy = useProxy,
            proxyPort = proxyPort
        ).also {
            it.start()
        }
    }

    fun abort() {
        activeWorker?.abort()
        activeWorker = null
    }

    private fun readAsset(name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }
}
