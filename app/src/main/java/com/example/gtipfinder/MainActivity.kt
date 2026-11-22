package com.example.gtipfinder

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.systemBarsPadding().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text("谷歌IP测速", style = MaterialTheme.typography.headlineSmall)
                }
                item { Text("IP 库：${s.ipCount} 个    ${s.message}") }

                // ---- IP 库导入（原项目 README 里的三个库）----
                item { Text("同步 IP 库", style = MaterialTheme.typography.titleSmall) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.importUrl(IpRepository.OFFICIAL_V4) },
                            enabled = !s.running,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text("官方 IPv4") }
                        OutlinedButton(
                            onClick = { vm.importUrl(IpRepository.EXT_V4) },
                            enabled = !s.running,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text("扩展 IPv4") }
                        OutlinedButton(
                            onClick = { vm.importUrl(IpRepository.EXT_V6) },
                            enabled = !s.running,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text("扩展 IPv6") }
                    }
                }
                item {
                    Text(
                        "IPv6 普遍不太稳定，非必要不要用；如要用，建议把下面的「次数」调到 10。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    OutlinedTextField(
                        value = s.customUrl,
                        onValueChange = { v -> vm.edit { it.copy(customUrl = v) } },
                        label = { Text("自定义 IP 库 URL（每行一个 IP）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.importUrl(s.customUrl) },
                            enabled = !s.running,
                            modifier = Modifier.weight(1f),
                        ) { Text("下载自定义") }
                        OutlinedButton(
                            onClick = { vm.importText(clipboard.getText()?.text.orEmpty()) },
                            enabled = !s.running,
                            modifier = Modifier.weight(1f),
                        ) { Text("剪贴板导入") }
                        TextButton(onClick = { vm.clearIps() }, enabled = !s.running) { Text("清空") }
                    }
                }

                // ---- 测速参数 ----
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("并发(≤64)", s.concurrency, !s.running, Modifier.weight(1f)) { v ->
                            vm.edit { it.copy(concurrency = v) }
                        }
                        NumField("超时 ms", s.timeoutMs, !s.running, Modifier.weight(1f)) { v ->
                            vm.edit { it.copy(timeoutMs = v) }
                        }
                        NumField("次数", s.rounds, !s.running, Modifier.weight(1f)) { v ->
                            vm.edit { it.copy(rounds = v) }
                        }
                    }
                }
                item {
                    if (s.running) {
                        Button(onClick = { vm.stop() }, modifier = Modifier.fillMaxWidth()) { Text("停止") }
                    } else {
                        Button(
                            onClick = { vm.start() },
                            enabled = s.ipCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("开始测速") }
                    }
                }
                if (s.total > 0) {
                    item {
                        LinearProgressIndicator(
                            progress = { s.done.toFloat() / s.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${s.done}/${s.total}，可用 ${s.results.size}")
                    }
                }

                // ---- 结果 ----
                if (s.results.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { copy(s.results.first().ip) },
                                modifier = Modifier.weight(1f),
                            ) { Text("复制最快 IP") }
                            OutlinedButton(
                                onClick = { copy("${s.results.first().ip} $TARGET_HOST") },
                                modifier = Modifier.weight(1f),
                            ) { Text("复制 hosts 行") }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { copy(s.results.take(10).joinToString("\n") { it.ip }) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("复制前 10 个 IP") }
                    }
                    items(s.results.take(200), key = { it.ip }) { r ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                Text(r.ip, fontFamily = FontFamily.Monospace)
                                Text(
                                    "平均 ${r.avgMs} ms · 最低 ${r.minMs} ms",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { copy(r.ip) }) { Text("复制") }
                        }
                    }
                }
                item { androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun NumField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
