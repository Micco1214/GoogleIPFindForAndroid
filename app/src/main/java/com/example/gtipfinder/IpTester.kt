package com.example.gtipfinder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

const val TARGET_HOST = "translate.googleapis.com"
const val TEST_URL = "https://$TARGET_HOST/translate_a/single?client=gtx&sl=en&tl=fr&q=a"

data class TestConfig(
    val concurrency: Int = 64,
    val timeoutMs: Long = 1500,
    val rounds: Int = 3,
)

data class IpResult(val ip: String, val avgMs: Long, val minMs: Long)

object IpTester {

    // 共享的基础 client（共享线程池），不保留空闲连接，保证每轮都是全新握手
    private val base: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()

    /**
     * 把 translate.googleapis.com 强制解析到指定 IP：
     * SNI / Host / 证书校验仍然使用域名，所以是真实可用性测试。
     */
    private fun clientFor(ip: String, timeoutMs: Long): OkHttpClient {
        val literal = InetAddress.getByName(ip) // IP 字面量，不会触发 DNS
        val bound = InetAddress.getByAddress(TARGET_HOST, literal.address)
        return base.newBuilder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = listOf(bound)
            })
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    /** 测试单个 IP：连续成功 rounds 次才算可用，返回平均/最低延迟。 */
    suspend fun testIp(ip: String, cfg: TestConfig): IpResult? = withContext(Dispatchers.IO) {
        val client = try {
            clientFor(ip, cfg.timeoutMs)
        } catch (e: Exception) {
            return@withContext null
        }
        val times = ArrayList<Long>()
        for (i in 0 until cfg.rounds) {
            ensureActive()
            val t0 = System.nanoTime()
            val ok = try {
                val req = Request.Builder().url(TEST_URL).header("Connection", "close").build()
                client.newCall(req).execute().use { resp ->
                    resp.isSuccessful && resp.body?.string() != null
                }
            } catch (e: IOException) {
                false
            } catch (e: RuntimeException) {
                false
            }
            if (!ok) return@withContext null
            times += (System.nanoTime() - t0) / 1_000_000
        }
        IpResult(ip, times.average().toLong(), times.min())
    }

    suspend fun run(
        ips: List<String>,
        cfg: TestConfig,
        onProgress: (done: Int, found: List<IpResult>) -> Unit,
    ): List<IpResult> = coroutineScope {
        val sem = Semaphore(cfg.concurrency)
        val done = AtomicInteger(0)
        val found = ArrayList<IpResult>()

        fun snapshot(): List<IpResult> = synchronized(found) { found.sortedBy { it.avgMs } }

        ips.map { ip ->
            async {
                sem.withPermit {
                    val r = testIp(ip, cfg)
                    if (r != null) synchronized(found) { found += r }
                    val d = done.incrementAndGet()
                    // 节流：每 10 个或最后一个才刷新 UI
                    if (d % 10 == 0 || d == ips.size) onProgress(d, snapshot())
                }
            }
        }.awaitAll()
        snapshot()
    }
}
