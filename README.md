# 谷歌IP测速（Android）

移植自 GoodCoder666/GoogleTranslate_IPFinder 的「测速」功能（GPL-3.0-or-later，
本项目同样以 GPL-3.0 许可开源）。

## 构建
1. 用 Android Studio（Koala 或更新）打开本目录，等待 Gradle 同步（会自动生成 wrapper）。
2. 连接手机或模拟器，点 Run；或 Build > Build APK(s)。

## 使用
1. 在「同步 IP 库」里点「官方 IPv4 / 扩展 IPv4 / 扩展 IPv6」下载（与原项目 README 的三个库一致，可叠加导入；下不了就填自定义 URL，或把 IP 列表复制后点「剪贴板导入」）。
2. 点「开始测速」。每个 IP 需连续成功「次数」次才算可用（默认 3 次，与原项目一致）。
3. 复制最快 IP，自行用于 hosts / 代理 / DNS 设置。「复制 hosts」会生成两行（与 Windows 版一致）：`<IP> translate.googleapis.com` 和 `<IP> translate-pa.googleapis.com`

## 原理
对每个 IP 发起 `https://translate.googleapis.com/translate_a/element.js`（与 Windows 版默认请求模板一致），
通过自定义 OkHttp Dns 把域名强制解析到该 IP（SNI/证书校验仍用域名），超时 1.5s。
