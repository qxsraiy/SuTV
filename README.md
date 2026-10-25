# SuTV

整合咪咕视频后端 + 天光云影前端 + WebView 兜底的 IPTV 安卓应用。

> ⚠️ **免责声明**：本软件为开源播放器工具，**不提供、不存储、不分发任何音视频内容**。所有直播源/点播源均来自第三方公开网络或由用户自行配置。请在使用前阅读完整 [DISCLAIMER.md](DISCLAIMER.md)，仅限个人学习研究使用，禁止任何商业用途，使用者自行承担全部使用后果。

## 功能

- **咪咕直播**：内置 174 个频道，11 个分组（CCTV、卫视等）
- **ExoPlayer 主播放**：Media3/HLS，支持 H.264 硬解
- **WebView 兜底播放**：ExoPlayer 播放失败时自动切换 hls.js WebView 播放
- **零配置**：打开即看，无需设置

## 架构

```
用户打开 App
  → 本地 HttpServer (端口 10481) 启动
  → 生成咪咕 M3U 播放列表 (174 频道)
  → ExoPlayer 播放 HLS 流
  → (失败时) 自动启动 WebView 兜底
  → hls.js + 本地代理播放
```

### 核心组件

| 文件 | 说明 |
|------|------|
| `MiguApi.kt` | 咪咕 API：频道列表、流地址、ddCalcu 加密 |
| `MiguM3uGenerator.kt` | M3U/TXT 播放列表生成 |
| `HttpServer.kt` | 本地 HTTP 服务器：M3U 端点、302 重定向、M3U8 代理 |
| `MiguFallbackActivity.kt` | 全屏 WebView 兜底播放器 |
| `hls_player.html` | hls.js HTML 播放器 |
| `MainContentState.kt` | 兜底触发逻辑 |

## 构建

```bash
# 环境要求
# JDK 21, Android SDK (minSdk 21, targetSdk 36)

./gradlew :tv:assembleOriginalDebug
```

输出 APK：`tv/build/outputs/apk/original/debug/`
