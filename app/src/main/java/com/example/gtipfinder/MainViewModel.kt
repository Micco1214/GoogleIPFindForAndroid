package com.example.gtipfinder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val ipCount: Int = 0,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val results: List<IpResult> = emptyList(),
    val message: String = "",
    val concurrency: String = "64",
    val timeoutMs: String = "1500",
    val rounds: String = "3",
    val customUrl: String = "",
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = IpRepository(app)
    private var ips: Set<String> = repo.load().toSet()
    private var job: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            ipCount = ips.size,
            message = if (ips.isEmpty()) "IP 库为空，请先同步或导入" else "",
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun edit(f: (UiState) -> UiState) = _state.update(f)

    fun importUrl(url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(message = "正在下载…") }
            try {
                addIps(repo.download(u))
            } catch (e: Exception) {
                _state.update { it.copy(message = "下载失败：${e.message}") }
            }
        }
    }

    fun importText(text: String) = addIps(IpRepository.parse(text))

    private fun addIps(list: List<String>) {
        val before = ips.size
        ips = ips + list
        val snapshot = ips
        viewModelScope.launch(Dispatchers.IO) { repo.save(snapshot) }
        _state.update {
            it.copy(ipCount = ips.size, message = "导入 ${list.size} 个，新增 ${ips.size - before} 个")
        }
    }

    fun clearIps() {
        ips = emptySet()
        viewModelScope.launch(Dispatchers.IO) { repo.save(emptySet()) }
        _state.update { it.copy(ipCount = 0, results = emptyList(), done = 0, total = 0, message = "已清空") }
    }

    fun start() {
        if (_state.value.running || ips.isEmpty()) return
        val s = _state.value
        val cfg = TestConfig(
            concurrency = (s.concurrency.toIntOrNull() ?: 64).coerceIn(1, 64),
            timeoutMs = (s.timeoutMs.toLongOrNull() ?: 1500L).coerceIn(300L, 10000L),
            rounds = (s.rounds.toIntOrNull() ?: 3).coerceIn(1, 10),
        )
        val list = ips.toList().shuffled()
        _state.update {
            it.copy(running = true, done = 0, total = list.size, results = emptyList(), message = "测速中…")
        }
        job = viewModelScope.launch {
            try {
                val res = IpTester.run(list, cfg) { done, found ->
                    _state.update { it.copy(done = done, results = found) }
                }
                _state.update { it.copy(results = res, message = "完成：${res.size}/${list.size} 个 IP 可用") }
            } catch (e: CancellationException) {
                _state.update { it.copy(message = "已停止") }
                throw e
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
