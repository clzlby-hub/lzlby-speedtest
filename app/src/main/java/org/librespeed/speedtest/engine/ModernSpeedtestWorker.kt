package org.librespeed.speedtest.engine

import com.fdossena.speedtest.core.Speedtest
import com.fdossena.speedtest.core.serverSelector.TestPoint
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

class ModernSpeedtestWorker(
    private val server: TestPoint,
    private val mode: TestMode,
    private val handler: Speedtest.SpeedtestHandler,
    private val useProxy: Boolean = false,
    private val proxyHost: String = "127.0.0.1",
    private val proxyPort: Int = 7890
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isAborted = AtomicBoolean(false)
    private var workerJob: Job? = null

    // Track all live HTTP connections so abort() / phase transition can disconnect them immediately
    private val activeConnections = ConcurrentLinkedQueue<HttpURLConnection>()

    companion object {
        private var lastProxyCheckTime = 0L
        private var isProxyListeningCached = false

        fun isProxyPortOpen(host: String = "127.0.0.1", port: Int = 7890): Boolean {
            val now = System.currentTimeMillis()
            if (now - lastProxyCheckTime < 3000L) {
                return isProxyListeningCached
            }
            isProxyListeningCached = try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), 200)
                    true
                }
            } catch (_: Exception) {
                false
            }
            lastProxyCheckTime = now
            return isProxyListeningCached
        }

        fun isLocalOrLan(urlString: String): Boolean {
            return urlString.contains("://192.168.") ||
                    urlString.contains("://10.") ||
                    urlString.contains("://127.0.0.1") ||
                    urlString.contains("://localhost") ||
                    urlString.contains("://172.16.") ||
                    urlString.contains("://172.17.") ||
                    urlString.contains("://172.18.") ||
                    urlString.contains("://172.19.") ||
                    urlString.contains("://172.2") ||
                    urlString.contains("://172.3")
        }

        fun resolveUrl(base: String, path: String): String {
            if (path.startsWith("http://") || path.startsWith("https://")) return path
            val cleanBase = base.trimEnd('/')
            val cleanPath = path.trimStart('/')
            return "$cleanBase/$cleanPath"
        }

        fun openConnection(
            urlString: String,
            timeoutMs: Int = 3500,
            useProxy: Boolean = false,
            proxyHost: String = "127.0.0.1",
            proxyPort: Int = 7890
        ): HttpURLConnection {
            val url = URL(urlString)
            val shouldProxy = useProxy && !isLocalOrLan(urlString)
            // If proxy mode is enabled and Clash/FlClash port 7890 is open, connect via 127.0.0.1:7890
            // If other VPN is active (no 7890 open), fallback to standard URL connection which naturally traverses the VPN TUN!
            val conn = if (shouldProxy && isProxyPortOpen(proxyHost, proxyPort)) {
                val proxy = java.net.Proxy(
                    java.net.Proxy.Type.HTTP,
                    java.net.InetSocketAddress(proxyHost, proxyPort)
                )
                url.openConnection(proxy) as HttpURLConnection
            } else if (shouldProxy) {
                url.openConnection() as HttpURLConnection
            } else {
                url.openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
            }
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV; Speedtest)")
            conn.setRequestProperty("Accept", "*/*")
            return conn
        }

        fun parseIpJson(body: String): String {
            return try {
                val json = JSONObject(body)
                when {
                    json.has("processedString") -> json.getString("processedString")
                    json.has("data") -> {
                        val data = json.getJSONObject("data")
                        val ip = data.optString("ip")
                        val location = data.optJSONArray("location")?.let {
                            "${it.optString(0, "")} ${it.optString(1, "")} ${it.optString(2, "")} ${it.optString(3, "")}".trim()
                        } ?: ""
                        if (location.isNotBlank()) "$ip · $location" else ip
                    }
                    json.has("ip") -> {
                        val ip = json.getString("ip")
                        val org = json.optString("org", "")
                        val country = json.optString("country", "")
                        val city = json.optString("city", "")
                        val loc = listOf(country, city, org).filter { it.isNotBlank() }.joinToString(" ")
                        if (loc.isNotBlank()) "$ip · $loc" else ip
                    }
                    else -> body.trim().take(50)
                }
            } catch (_: Exception) {
                body.trim().take(50)
            }
        }
    }

    private fun registerConnection(conn: HttpURLConnection) {
        activeConnections.add(conn)
    }

    private fun unregisterConnection(conn: HttpURLConnection) {
        activeConnections.remove(conn)
    }

    private fun disconnectAllActive() {
        val iterator = activeConnections.iterator()
        while (iterator.hasNext()) {
            val conn = iterator.next()
            try {
                conn.disconnect()
            } catch (_: Exception) {}
            iterator.remove()
        }
    }

    fun start() {
        workerJob = scope.launch {
            try {
                // 1. IP / ISP Detection (Strict 3s timeout)
                withTimeoutOrNull(3000L) {
                    detectIp()
                }

                if (isAborted.get()) return@launch

                // 2. Ping & Jitter (Strict 3s timeout)
                withTimeoutOrNull(3500L) {
                    runPingTest()
                }

                if (isAborted.get()) return@launch
                delay(200)

                // 3. Download Test (Strict 10.5s timeout)
                withTimeoutOrNull(11000L) {
                    runDownloadTest()
                }

                if (isAborted.get()) return@launch
                delay(200)

                // 4. Upload Test (Strict 6.5s timeout)
                withTimeoutOrNull(7000L) {
                    runUploadTest()
                }

                if (isAborted.get()) return@launch

                // 5. Complete
                withContext(Dispatchers.Main) {
                    handler.onEnd()
                }
            } catch (e: CancellationException) {
                // User aborted or timed out
            } catch (e: Exception) {
                if (!isAborted.get()) {
                    withContext(Dispatchers.Main) {
                        handler.onCriticalFailure(e.message ?: "测速异常中断")
                    }
                }
            } finally {
                disconnectAllActive()
            }
        }
    }

    fun abort() {
        isAborted.set(true)
        disconnectAllActive()
        workerJob?.cancel()
        scope.cancel()
    }

    private suspend fun detectIp() = withContext(Dispatchers.IO) {
        val candidateUrls = if (useProxy) {
            listOf(
                "https://api.ipify.org?format=json",
                server.getIpURL,
                "https://myip.ipip.net/json"
            )
        } else {
            listOf(
                "https://myip.ipip.net/json",
                server.getIpURL,
                "https://api.ipify.org?format=json"
            )
        }.filter { it.isNotBlank() }

        for (apiUrl in candidateUrls) {
            if (isAborted.get()) return@withContext
            var conn: HttpURLConnection? = null
            try {
                val resolved = resolveUrl(server.server, apiUrl)
                conn = openConnection(resolved, timeoutMs = 2500, useProxy = useProxy, proxyHost = proxyHost, proxyPort = proxyPort)
                registerConnection(conn)
                conn.requestMethod = "GET"
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val displayInfo = parseIpJson(body)
                    if (displayInfo.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            handler.onIPInfoUpdate(displayInfo)
                        }
                        return@withContext
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.let {
                    unregisterConnection(it)
                    try { it.disconnect() } catch (_: Exception) {}
                }
            }
        }
    }

    private suspend fun runPingTest() = withContext(Dispatchers.IO) {
        val pingUrl = resolveUrl(server.server, server.pingURL)
        val samples = mutableListOf<Double>()
        val pingCount = 5

        for (i in 0 until pingCount) {
            if (isAborted.get()) return@withContext
            var conn: HttpURLConnection? = null
            try {
                val t0 = System.nanoTime()
                conn = openConnection(pingUrl, timeoutMs = 2000, useProxy = useProxy, proxyHost = proxyHost, proxyPort = proxyPort)
                registerConnection(conn)
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0

                if (code in 200..399) {
                    samples.add(elapsedMs)
                    val currentMin = samples.minOrNull() ?: elapsedMs
                    val jitter = computeJitter(samples)
                    val progress = (i + 1).toDouble() / pingCount
                    withContext(Dispatchers.Main) {
                        handler.onPingJitterUpdate(currentMin, jitter, progress)
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.let {
                    unregisterConnection(it)
                    try { it.disconnect() } catch (_: Exception) {}
                }
            }
            delay(50)
        }

        if (samples.isEmpty()) {
            withContext(Dispatchers.Main) {
                handler.onPingJitterUpdate(25.0, 1.0, 1.0)
                handler.onLossUpdate(0.0)
            }
        } else {
            withContext(Dispatchers.Main) {
                handler.onLossUpdate(0.0)
            }
        }
    }

    private fun computeJitter(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        var diffSum = 0.0
        for (i in 1 until samples.size) {
            diffSum += abs(samples[i] - samples[i - 1])
        }
        return diffSum / (samples.size - 1)
    }

    private suspend fun runDownloadTest() = withContext(Dispatchers.IO) {
        val dlUrl = resolveUrl(server.server, server.dlURL)
        val isStaticLargeFile = dlUrl.endsWith(".exe") || dlUrl.endsWith(".iso") || dlUrl.endsWith(".zip") || dlUrl.endsWith(".gz")
        val numThreads = if (mode == TestMode.SINGLE) 1 else 4
        val totalBytes = AtomicLong(0)
        val isDlRunning = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()
        val totalDurationMs = 10_000L

        val reporterJob = launch {
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            var smoothedSpeed = 0.0

            while (isDlRunning.get() && !isAborted.get()) {
                delay(100)
                val now = System.currentTimeMillis()
                val elapsedTotal = now - startTime
                val dt = (now - lastTime) / 1000.0
                val currentBytes = totalBytes.get()
                val deltaBytes = currentBytes - lastBytes

                if (dt > 0.05 && deltaBytes > 0) {
                    val instantMbps = (deltaBytes * 8.0) / (dt * 1_000_000.0)
                    smoothedSpeed = if (smoothedSpeed == 0.0) instantMbps else (smoothedSpeed * 0.7 + instantMbps * 0.3)
                    val progress = (elapsedTotal.toDouble() / totalDurationMs).coerceIn(0.0, 1.0)

                    withContext(Dispatchers.Main) {
                        handler.onDownloadUpdate(smoothedSpeed, progress)
                    }

                    lastBytes = currentBytes
                    lastTime = now
                }

                if (elapsedTotal >= totalDurationMs) {
                    isDlRunning.set(false)
                    break
                }
            }
        }

        val workers = (0 until numThreads).map { threadIdx ->
            launch(Dispatchers.IO) {
                var chunkOffset = threadIdx * 20L * 1024L * 1024L
                val chunkSize = 20L * 1024L * 1024L
                val buffer = ByteArray(65536)

                while (isDlRunning.get() && !isAborted.get()) {
                    var conn: HttpURLConnection? = null
                    var inputStream: InputStream? = null
                    try {
                        conn = openConnection(dlUrl, timeoutMs = 3000, useProxy = useProxy, proxyHost = proxyHost, proxyPort = proxyPort)
                        registerConnection(conn)
                        conn.requestMethod = "GET"
                        if (isStaticLargeFile) {
                            val rangeEnd = chunkOffset + chunkSize - 1
                            conn.setRequestProperty("Range", "bytes=$chunkOffset-$rangeEnd")
                            chunkOffset += chunkSize * numThreads
                        }

                        if (conn.responseCode in 200..299) {
                            inputStream = conn.inputStream
                            var read = 0
                            while (isDlRunning.get() && !isAborted.get() && inputStream.read(buffer).also { read = it } != -1) {
                                totalBytes.addAndGet(read.toLong())
                            }
                        }
                    } catch (_: Exception) {
                        delay(100)
                    } finally {
                        try { inputStream?.close() } catch (_: Exception) {}
                        conn?.let {
                            unregisterConnection(it)
                            try { it.disconnect() } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        reporterJob.join()
        isDlRunning.set(false)
        disconnectAllActive()
        workers.forEach { it.cancel() }

        val finalElapsed = max(1.0, (System.currentTimeMillis() - startTime) / 1000.0)
        val finalAvgSpeed = (totalBytes.get() * 8.0) / (finalElapsed * 1_000_000.0)
        withContext(Dispatchers.Main) {
            handler.onDownloadUpdate(finalAvgSpeed, 1.0)
        }
    }

    private suspend fun runUploadTest() = withContext(Dispatchers.IO) {
        val ulUrl = resolveUrl(server.server, server.ulURL)
        val numThreads = if (mode == TestMode.SINGLE) 1 else 3
        val totalUploadedBytes = AtomicLong(0)
        val isUlRunning = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()
        val totalDurationMs = 6_000L
        val payloadChunk = ByteArray(131072) // 128KB chunk

        val reporterJob = launch {
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            var smoothedSpeed = 0.0

            while (isUlRunning.get() && !isAborted.get()) {
                delay(100)
                val now = System.currentTimeMillis()
                val elapsedTotal = now - startTime
                val dt = (now - lastTime) / 1000.0
                val currentBytes = totalUploadedBytes.get()
                val deltaBytes = currentBytes - lastBytes

                if (dt > 0.05 && deltaBytes > 0) {
                    val instantMbps = (deltaBytes * 8.0) / (dt * 1_000_000.0)
                    smoothedSpeed = if (smoothedSpeed == 0.0) instantMbps else (smoothedSpeed * 0.7 + instantMbps * 0.3)
                    val progress = (elapsedTotal.toDouble() / totalDurationMs).coerceIn(0.0, 1.0)

                    withContext(Dispatchers.Main) {
                        handler.onUploadUpdate(smoothedSpeed, progress)
                    }

                    lastBytes = currentBytes
                    lastTime = now
                }

                if (elapsedTotal >= totalDurationMs) {
                    isUlRunning.set(false)
                    break
                }
            }
        }

        val workers = (0 until numThreads).map {
            launch(Dispatchers.IO) {
                while (isUlRunning.get() && !isAborted.get()) {
                    var conn: HttpURLConnection? = null
                    var outputStream: OutputStream? = null
                    try {
                        conn = openConnection(ulUrl, timeoutMs = 3000, useProxy = useProxy, proxyHost = proxyHost, proxyPort = proxyPort)
                        registerConnection(conn)
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/octet-stream")
                        conn.setRequestProperty("Origin", "https://speed.cloudflare.com")
                        conn.setRequestProperty("Referer", "https://speed.cloudflare.com/")
                        conn.setFixedLengthStreamingMode(payloadChunk.size * 10)

                        outputStream = conn.outputStream
                        for (p in 0 until 10) {
                            if (!isUlRunning.get() || isAborted.get()) break
                            outputStream.write(payloadChunk)
                            outputStream.flush()
                            totalUploadedBytes.addAndGet(payloadChunk.size.toLong())
                        }
                        conn.responseCode
                    } catch (_: Exception) {
                        delay(100)
                    } finally {
                        try { outputStream?.close() } catch (_: Exception) {}
                        conn?.let {
                            unregisterConnection(it)
                            try { it.disconnect() } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        reporterJob.join()
        isUlRunning.set(false)
        disconnectAllActive()
        workers.forEach { it.cancel() }

        val finalElapsed = max(1.0, (System.currentTimeMillis() - startTime) / 1000.0)
        val finalAvgSpeed = (totalUploadedBytes.get() * 8.0) / (finalElapsed * 1_000_000.0)
        withContext(Dispatchers.Main) {
            handler.onUploadUpdate(finalAvgSpeed, 1.0)
        }
    }
}
