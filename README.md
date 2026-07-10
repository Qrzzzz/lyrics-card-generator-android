# Lyrics Card Generator Android

歌词卡片生成器 Android Alpha 工程。原生界面使用 Jetpack Compose，最终预览与 PNG 继续由 APK 内的 React/CSS 渲染器完成；卡片编辑与导出离线可用，网易云选歌与链接解析按需联网。

当前版本：`0.2.0-alpha02`

Windows 版基准：[`Qrzzzz/lyrics-card-generator`](https://github.com/Qrzzzz/lyrics-card-generator) `b894db9e121848122a16ddcdaaab1283ffab1e27`（package `4.3.8`）。本仓库不修改相邻的 Windows 工作树；基准提交、字体散列和 Schema 都记录在 `renderer/renderer-manifest.json`。

## 已完成

- Alpha / Production 构建变体，`minSdk 26`、`targetSdk 36`、`compileSdk 36.1`；
- Compose 首页、最近项目、空白/示例项目、复制、重命名和删除；
- Room 项目存储、版本化 RenderSpec v1、500 ms 防抖自动保存和 50 步撤销/重做；
- 对齐 Web 版的六步制作流程：选择歌曲、歌词、布局、字体方案、视觉、导出；
- 网易云歌曲名搜索、结果解析、歌词/封面导入，以及分享文本或链接贴入解析；
- 本地封面私有存储，RenderSpec 仅保存逻辑 Asset ID；
- `WebViewAssetLoader` + 固定 Origin 的 WebMessage 通道，前两步不创建预览、第三步按需启动并跨后续步骤/导出复用；
- 本地 React/CSS latest-wins 串行实时预览、独立自动测高、封面取色、1×/2× `html-to-image` PNG；
- PNG 按每块 384 KiB 原始数据编号回传，Native 流式写入临时文件并校验顺序、大小和最终图片；
- SAF 保存、FileProvider 分享、导出结果预览和诊断页；
- 固定思源黑体/宋体、Renderer manifest、JSON Schema、Fixtures 和单元测试；
- `INTERNET` 仅用于 Native 侧的网易云 HTTPS 请求；渲染 WebView 仍阻断网络、外部导航、文件访问、混合内容和下载；
- 版本化字体 URL、本地静态资源长缓存、`font-display: swap` 与 OTF 免压缩，减少预览首次可见和重复打开等待。
- RenderSpec JSON Schema 在构建期生成独立验证器，运行时无需 `eval`，严格 CSP 下也能立即启动渲染器。

## 构建

需要 JDK 17、Node.js 20+、Android SDK Platform 36.1 和 Build Tools 36.1。

```powershell
cd renderer
npm ci
npm run check

cd ..
.\gradlew.bat :app:testAlphaDebugUnitTest
.\gradlew.bat :app:assembleAlphaDebug
```

Gradle 的 `preBuild` 会自动执行 `renderer/npm run build` 并把产物写入 `app/src/main/assets/renderer`，无需手工复制。

Windows 下仓库路径包含中文时，AGP 编译已通过 `android.overridePathCheck` 允许；如果本机 Gradle/JUnit 工作进程仍不能正确处理路径，可从一个只含 ASCII 的目录联接运行测试。

## 目录

```text
app/                         Android Compose 应用
  src/main/java/.../model    RenderSpec 与领域模型
  src/main/java/.../data     Room、DataStore、Repository
  src/main/java/.../renderer WebView 安全策略、桥接与导出
renderer/                    可独立测试和构建的 React/CSS 渲染器
  schema/                    RenderSpec v1 JSON Schema
  fixtures/                  首批渲染样例
docs/                        架构、基准和 Alpha 状态
```

## 当前边界

这是可运行的 Alpha 技术闭环，不等同于最终发布验收。30 组跨端 Golden、固定 WebView 参考镜像、API 26/30/33/36 真机矩阵、长时间/连续导出测试和资源引用计数仍需在发布前完成；WebMessage ArrayBuffer 快速通道保留为后续内存与吞吐优化。详见 [Alpha 状态](docs/ALPHA_STATUS.md)。

项目代码沿用根目录的 Source Available License；内置思源字体受各自的 SIL Open Font License 1.1 约束。
