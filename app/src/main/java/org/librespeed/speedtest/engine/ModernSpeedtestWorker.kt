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
        @Volatile
        private var lastProxyCheckTime = 0L
        @Volatile
        private var isProxyListeningCached = false

        data class ProbeResult(
            val success: Boolean,
            val rttMs: Double,
            val usedGetRange: Boolean
        )

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
            conn.setRequestProperty("Accept-Encoding", "identity")
            return conn
        }

        /**
         * Measures a single HTTP RTT using HEAD (or GET Range: bytes=0-0 if HEAD is unsupported).
         * Resets the timer on 405/501 fallback so the failed HEAD round-trip is not counted.
         */
        fun probeOnce(
            urlString: String,
            timeoutMs: Int = 2000,
            useProxy: Boolean = false,
            proxyHost: String = "127.0.0.1",
            proxyPort: Int = 7890,
            preferGetRange: Boolean = false,
            keepAlive: Boolean = true
        ): ProbeResult {
            var conn: HttpURLConnection? = null
            var usedRange = preferGetRange
            var succeeded = false
            return try {
                var t0 = System.nanoTime()
                conn = openConnection(urlString, timeoutMs, useProxy, proxyHost, proxyPort)
                conn.instanceFollowRedirects = true
                if (preferGetRange) {
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Range", "bytes=0-0")
                } else {
                    conn.requestMethod = "HEAD"
                }
                var code = conn.responseCode
                if (!preferGetRange && (code == 405 || code == 501)) {
                    try { conn.disconnect() } catch (_: Exception) {}
                    usedRange = true
                    t0 = System.nanoTime()
                    conn = openConnection(urlString, timeoutMs, useProxy, proxyHost, proxyPort)
                    conn.instanceFollowRedirects = true
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Range", "bytes=0-0")
                    code = conn.responseCode
                }
                val rttMs = (System.nanoTime() - t0) / 1_000_000.0
                try {
                    conn.inputStream?.use { stream ->
                        val buf = ByteArray(256)
                        while (stream.read(buf) != -1) { /* drain for Keep-Alive */ }
                    }
                } catch (_: Exception) {}
                succeeded = code in 200..399
                ProbeResult(succeeded, rttMs, usedRange)
            } catch (_: Exception) {
                ProbeResult(false, 0.0, usedRange)
            } finally {
                if (!keepAlive || !succeeded) {
                    try { conn?.disconnect() } catch (_: Exception) {}
                }
            }
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
        val dlDurationMs = if (mode == TestMode.STABILITY) 20_000L else 10_000L
        val ulDurationMs = if (mode == TestMode.STABILITY) 15_000L else 10_000L
        workerJob = scope.launch {
            try {
                // 1. IP / ISP Detection (Strict 3s timeout)
                withTimeoutOrNull(3000L) {
                    detectIp()
                }

                if (isAborted.get()) return@launch

                // 2. Ping & Jitter (Strict 4.5s timeout)
                withTimeoutOrNull(4500L) {
                    runPingTest()
                }

                if (isAborted.get()) return@launch
                delay(200)

                // 3. Download Test (headroom prevents cancelling finalAvgSpeed reporting)
                withTimeoutOrNull(dlDurationMs + 5000L) {
                    runDownloadTest(dlDurationMs)
                }

                if (isAborted.get()) return@launch
                delay(200)

                // 4. Upload Test (headroom prevents cancelling finalAvgSpeed reporting)
                withTimeoutOrNull(ulDurationMs + 5000L) {
                    runUploadTest(ulDurationMs)
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
                withContext(NonCancellable) {
                    disconnectAllActive()
                }
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
        val pingCount = 6
        var failedCount = 0

        // 1 warm-up probe to establish DNS + TCP/TLS Keep-Alive and detect if HEAD is unsupported
        val warmup = probeOnce(
            urlString = pingUrl,
            timeoutMs = 2500,
            useProxy = useProxy,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            preferGetRange = false,
            keepAlive = true
        )
        var preferGetRange = warmup.usedGetRange

        for (i in 0 until pingCount) {
            if (isAborted.get()) return@withContext
            val res = probeOnce(
                urlString = pingUrl,
                timeoutMs = 2000,
                useProxy = useProxy,
                proxyHost = proxyHost,
                proxyPort = proxyPort,
                preferGetRange = preferGetRange,
                keepAlive = !isAborted.get()
            )
            preferGetRange = res.usedGetRange

            if (res.success) {
                samples.add(res.rttMs)
                val currentMin = samples.minOrNull() ?: res.rttMs
                val jitter = computeJitter(samples)
                val progress = (i + 1).toDouble() / pingCount
                withContext(Dispatchers.Main) {
                    handler.onPingJitterUpdate(currentMin, jitter, progress)
                }
            } else {
                failedCount++
            }
            delay(50)
        }

        val lossPercent = (failedCount * 100.0) / pingCount
        if (samples.isEmpty()) {
            withContext(Dispatchers.Main) {
                handler.onPingJitterUpdate(0.0, 0.0, 1.0)
                handler.onLossUpdate(100.0)
            }
        } else {
            withContext(Dispatchers.Main) {
                handler.onLossUpdate(lossPercent)
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

    private fun trimmedMean(samples: List<Double>, dropLowRatio: Double = 0.2, dropHighRatio: Double = 0.1): Double? {
        if (samples.size < 8) return null
        val sorted = samples.sorted()
        val low = (sorted.size * dropLowRatio).toInt()
        val high = (sorted.size * dropHighRatio).toInt()
        val slice = sorted.subList(low, (sorted.size - high).coerceAtLeast(low + 1))
        return slice.average()
    }

    private suspend fun runDownloadTest(totalDurationMs: Long = 10_000L) = withContext(Dispatchers.IO) {
        var dlUrl = resolveUrl(server.server, server.dlURL)
        if (dlUrl.contains("__down?bytes=50000000")) {
            dlUrl = dlUrl.replace("__down?bytes=50000000", "__down?bytes=100000000")
        }
        val isStaticLargeFile = dlUrl.endsWith(".exe") || dlUrl.endsWith(".iso") || dlUrl.endsWith(".zip") || dlUrl.endsWith(".gz")
        val numThreads = if (mode == TestMode.SINGLE) 1 else 6
        val totalBytes = AtomicLong(0)
        val firstByteTime = AtomicLong(0)
        val steadyStartTime = AtomicLong(0)
        val steadyStartBytes = AtomicLong(0)
        val steadySamples = java.util.Collections.synchronizedList(mutableListOf<Double>())
        val isDlRunning = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()
        val warmupAfterFirstByteMs = 1500L
        val maxWarmupMs = 3000L

        val reporterJob = launch {
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            var lastSteadySampleBytes = 0L
            var lastSteadySampleTime = 0L
            var smoothedSpeed = 0.0

            while (isDlRunning.get() && !isAborted.get()) {
                delay(100)
                val now = System.currentTimeMillis()
                val elapsedTotal = now - startTime
                val dt = (now - lastTime) / 1000.0
                val currentBytes = totalBytes.get()
                val deltaBytes = currentBytes - lastBytes
                val fb = firstByteTime.get()

                // Capture steady-state baseline after TCP/TLS handshake & slow-start warm-up
                val warmedUp = (fb > 0L && now - fb >= warmupAfterFirstByteMs) || elapsedTotal >= maxWarmupMs
                if (warmedUp && steadyStartTime.get() == 0L && currentBytes > 0) {
                    steadyStartTime.set(now)
                    steadyStartBytes.set(currentBytes)
                    lastSteadySampleTime = now
                    lastSteadySampleBytes = currentBytes
                }

                if (steadyStartTime.get() > 0L && now - lastSteadySampleTime >= 200L) {
                    val sampleDt = (now - lastSteadySampleTime) / 1000.0
                    val sampleDelta = currentBytes - lastSteadySampleBytes
                    if (sampleDt > 0.1 && sampleDelta >= 0) {
                        steadySamples.add((sampleDelta * 8.0) / (sampleDt * 1_000_000.0))
                        lastSteadySampleTime = now
                        lastSteadySampleBytes = currentBytes
                    }
                }

                if (dt > 0.05 && deltaBytes > 0) {
                    val instantMbps = (deltaBytes * 8.0) / (dt * 1_000_000.0)
                    smoothedSpeed = if (smoothedSpeed == 0.0) instantMbps else (smoothedSpeed * 0.65 + instantMbps * 0.35)
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

        val workers = (0 until numThreads).map {
            launch(Dispatchers.IO) {
                val buffer = ByteArray(131072) // 128KB read buffer

                while (isDlRunning.get() && !isAborted.get()) {
                    var conn: HttpURLConnection? = null
                    var inputStream: InputStream? = null
                    var completedEof = false
                    try {
                        conn = openConnection(dlUrl, timeoutMs = 4000, useProxy = useProxy, proxyHost = proxyHost, proxyPort = proxyPort)
                        registerConnection(conn)
                        conn.requestMethod = "GET"
                        if (isStaticLargeFile) {
                            conn.setRequestProperty("Range", "bytes=0-")
                        }

                        if (conn.responseCode in 200..299) {
                            inputStream = conn.inputStream
                            var read = 0
                            while (isDlRunning.get() && !isAborted.get() && inputStream.read(buffer).also { read = it } != -1) {
                                firstByteTime.compareAndSet(0L, System.currentTimeMillis())
                                totalBytes.addAndGet(read.toLong())
                            }
                            if (read == -1) completedEof = true
                        }
                    } catch (_: Exception) {
                        delay(100)
                    } finally {
                        try { inputStream?.close() } catch (_: Exception) {}
                        conn?.let {
                            unregisterConnection(it)
                            // Keep-Alive reuse on normal EOF; only force disconnect on error/stop/abort
                            if (!completedEof || !isDlRunning.get() || isAborted.get()) {
                                try { it.disconnect() } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        reporterJob.join()
        isDlRunning.set(false)
        val endTime = System.currentTimeMillis()
        val finalTotalBytes = totalBytes.get()

        val steadyT0 = steadyStartTime.get()
        val trimmed = trimmedMean(steadySamples.toList())
        val finalAvgSpeed = when {
            trimmed != null && trimmed > 0.0 -> trimmed
            steadyT0 > 0L && endTime - steadyT0 >= 1000L && finalTotalBytes > steadyStartBytes.get() -> {
                val steadyBytes = finalTotalBytes - steadyStartBytes.get()
                val steadySec = (endTime - steadyT0) / 1000.0
                (steadyBytes * 8.0) / (steadySec * 1_000_000.0)
            }
            else -> {
                val t0 = firstByteTime.get().let { if (it > 0L) it else startTime }
                val finalElapsed = max(0.5, (endTime - t0) / 1000.0)
                (finalTotalBytes * 8.0) / (finalElapsed * 1_000_000.0)
            }
        }
        withContext(Dispatchers.Main) {
            handler.onDownloadUpdate(finalAvgSpeed, 1.0)
        }
        withContext(NonCancellable) {
            disconnectAllActive()
            workers.forEach { it.cancel() }
        }
    }

    private suspend fun runUploadTest(totalDurationMs: Long = 10_000L) = withContext(Dispatchers.IO) {
        val ulUrl = resolveUrl(server.server, server.ulURL)
        val numThreads = if (mode == TestMode.SINGLE) 1 else 8
        val totalUploadedBytes = AtomicLong(0)
        val ackedBytes = AtomicLong(0)
        val lastAckTime = AtomicLong(0)
        val firstSteadyAckTime = AtomicLong(0)
        val steadyAckBaselineBytes = AtomicLong(0)
        val steadyStartTime = AtomicLong(0)
        val isUlRunning = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()
        val warmupMs = 2000L
        val payloadChunk = ByteArray(65536) // 64KB chunk

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

                if (elapsedTotal >= warmupMs && steadyStartTime.get() == 0L) {
                    steadyStartTime.set(now)
                }

                if (dt > 0.05 && deltaBytes > 0) {
                    val instantMbps = (deltaBytes * 8.0) / (dt * 1_000_000.0)
                    smoothedSpeed = if (smoothedSpeed == 0.0) instantMbps else (smoothedSpeed * 0.65 + instantMbps * 0.35)
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
                var chunksPerRequest = 8 // Start at 512KB; adapts 256KB..3MB to saturate high-RTT links

                while (isUlRunning.get() && !isAborted.get()) {
                    var conn: HttpURLConnection? = null
                    var outputStream: OutputStream? = null
                    var completedNormally = false
                    val requestBytes = payloadChunk.size.toLong() * chunksPerRequest
                    try {
                        val reqStart = System.currentTimeMillis()
                        conn = openConnection(ulUrl, timeoutMs = 4500, useProxy = useProxy, proxyHost = proxyHost, proxyPort = proxyPort)
                        conn.readTimeout = 8000
                        registerConnection(conn)
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/octet-stream")
                        if (ulUrl.contains("cloudflare.com")) {
                            conn.setRequestProperty("Origin", "https://speed.cloudflare.com")
                            conn.setRequestProperty("Referer", "https://speed.cloudflare.com/")
                        }
                        conn.setFixedLengthStreamingMode(requestBytes)

                        outputStream = conn.outputStream
                        var abortedMidWrite = false
                        for (p in 0 until chunksPerRequest) {
                            if (!isUlRunning.get() || isAborted.get()) {
                                abortedMidWrite = true
                                break
                            }
                            outputStream.write(payloadChunk)
                            totalUploadedBytes.addAndGet(payloadChunk.size.toLong())
                        }
                        if (abortedMidWrite) continue

                        outputStream.flush()
                        val code = conn.responseCode
                        try {
                            conn.inputStream?.use { stream ->
                                val buf = ByteArray(256)
                                while (stream.read(buf) != -1) { /* drain for Keep-Alive */ }
                            }
                        } catch (_: Exception) {}

                        if (code in 200..299) {
                            completedNormally = true
                            val ackNow = System.currentTimeMillis()
                            val newAcked = ackedBytes.addAndGet(requestBytes)
                            lastAckTime.set(ackNow)

                            if (steadyStartTime.get() > 0L && firstSteadyAckTime.compareAndSet(0L, ackNow)) {
                                steadyAckBaselineBytes.set(newAcked)
                            }

                            val reqDurationMs = ackNow - reqStart
                            if (reqDurationMs < 2000L && chunksPerRequest < 48) {
                                chunksPerRequest = (chunksPerRequest * 2).coerceAtMost(48)
                            } else if (reqDurationMs > 3500L && chunksPerRequest > 4) {
                                chunksPerRequest /= 2
                            }
                        }
                    } catch (_: Exception) {
                        delay(100)
                    } finally {
                        try { outputStream?.close() } catch (_: Exception) {}
                        conn?.let {
                            unregisterConnection(it)
                            if (!completedNormally || !isUlRunning.get() || isAborted.get()) {
                                try { it.disconnect() } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        reporterJob.join()
        isUlRunning.set(false)
        val endTime = System.currentTimeMillis()

        val steadyAckT0 = firstSteadyAckTime.get()
        val steadyAckT1 = lastAckTime.get()
        val totalAck = ackedBytes.get()
        val steadyAckDelta = totalAck - steadyAckBaselineBytes.get()
        val finalAvgSpeed = when {
            steadyAckT0 > 0L && steadyAckT1 - steadyAckT0 >= 1000L && steadyAckDelta > 0L -> {
                val steadySec = (steadyAckT1 - steadyAckT0) / 1000.0
                (steadyAckDelta * 8.0) / (steadySec * 1_000_000.0)
            }
            totalAck > 0L && steadyAckT1 > startTime -> {
                val ackElapsed = max(0.5, (steadyAckT1 - startTime) / 1000.0)
                (totalAck * 8.0) / (ackElapsed * 1_000_000.0)
            }
            else -> {
                val finalElapsed = max(0.5, (endTime - startTime) / 1000.0)
                (totalUploadedBytes.get() * 8.0) / (finalElapsed * 1_000_000.0)
            }
        }
        withContext(Dispatchers.Main) {
            handler.onUploadUpdate(finalAvgSpeed, 1.0)
        }
        withContext(NonCancellable) {
            disconnectAllActive()
            workers.forEach { it.cancel() }
        }
    }
}
