package com.example.gtipfinder

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class IpRepository(private val ctx: Context) {

    private val file get() = File(ctx.filesDir, "ip.txt")

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun load(): List<String> = if (file.exists()) parse(file.readText()) else emptyList()

    fun save(ips: Collection<String>) {
        file.writeText(ips.sorted().joinToString("\n"))
    }

    suspend fun download(url: String): List<String> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            parse(resp.body?.string().orEmpty())
        }
    }

    companion object {
        // 来自作者的 IP 库仓库 GoodCoder666/gtdb（src/ip.txt 与 full/v4.txt）。
        // 如果下载不了，可以在界面里填自定义 URL（如 GitHub 代理镜像），或从剪贴板导入。
        const val OFFICIAL =
            "https://raw.githubusercontent.com/GoodCoder666/gtdb/main/src/ip.txt"
        const val SUPER_V4 =
            "https://raw.githubusercontent.com/GoodCoder666/gtdb/main/full/v4.txt"

        private val IP_CHARS = Regex("^[0-9a-fA-F:.]+$")

        /** 每行一个 IP；忽略空行和 # 注释；只保留 IP 字面量。 */
        fun parse(text: String): List<String> = text.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() && IP_CHARS.matches(it) && (it.contains('.') || it.contains(':')) }
            .distinct()
            .toList()
    }
}
