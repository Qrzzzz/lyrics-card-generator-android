# Lyrics Card Generator Android

Lyrics Card Generator 是一款 Android 原生歌词卡片制作应用。项目管理、编辑、设置、保存与分享由 Jetpack Compose 实现；卡片预览和 PNG 导出由 APK 内置的 React/CSS 渲染器完成，以保持既有卡片设计和跨端输出合同。

当前源码版本为 `1.0.1`（`versionCode 10003`）。应用主题支持跟随系统、浅色与深色；正式产物通过受保护的 production signing workflow 构建。设备矩阵与验证边界见 [Release Readiness](docs/RELEASE_READINESS.md)。

## 功能概览

- 创建空白或示例项目，并在首页管理、复制、重命名和删除最近项目；
- 按六步流程完成选歌、歌词、布局、字体、视觉和导出设置；
- 支持手动输入，以及用户主动发起的网易云歌曲搜索、链接解析、歌词与封面导入；
- 使用 Room 在本地保存项目，支持自动保存、撤销/重做、配置变化和进程重建后的项目恢复；
- 提供紧凑、中等和展开布局、跟随系统/浅色/深色主题及无障碍语义；
- 生成 1× 或 2× PNG，通过系统文件选择器保存，或通过 Android 分享面板分享；
- 维护封面引用账本、缩略图、导出缓存和中断后的孤儿文件清理。

## Offline Renderer

编辑和导出不依赖远程网页。Gradle 将 `renderer/` 构建到 `app/build/generated/renderer/assets/renderer`，应用只从固定的本地 Origin 加载这些资源。Renderer WebView 禁止网络、外部导航、文件访问和内容访问；`INTERNET` 权限仅供 Native 网易云客户端在用户执行搜索、解析或封面导入时发起受限的 HTTPS 请求。

应用不包含 analytics、tracking、广告或遥测 SDK。项目和导出缓存保存在本机；详细数据与网络行为见 [PRIVACY.md](PRIVACY.md)。

## 系统与构建要求

运行要求：

- Android 8.0（API 26）或更高版本；
- 可用且支持安全消息通道的 Android System WebView。

构建要求：

- JDK 17；
- Node.js 20 或更高版本（CI 使用 Node.js 24）；
- Android SDK Platform 36.1 与 Build Tools 36.1.0。

Windows：

```powershell
cd renderer
npm.cmd ci
npm.cmd run check

cd ..
.\gradlew.bat :app:test `
  :app:lintProductionRelease `
  :app:assembleAlphaDebug `
  :app:assembleProductionDebug `
  :app:assembleProductionRelease `
  :app:bundleProductionRelease `
  --no-parallel
```

macOS / Linux 使用 `npm` 与 `./gradlew` 执行等价任务。`preBuild` 会自动执行 Renderer 生产构建；正常构建只写入 Gradle build directory，不应修改受版本控制的 Renderer source 或 generated validator。

GitHub Actions 的 `Android Quality Gate` 覆盖 Renderer 检查、全部 JVM tests、`lintProductionRelease`、Alpha Debug 保留门、Production Debug、R8/minified Production Release APK 与 Production Release AAB。Instrumentation 不在 hosted CI 中伪造为通过，仍属于获授权设备上的 Final Gate。

## Production release candidate

没有生产签名配置时，`assembleProductionRelease` 与 `bundleProductionRelease` 仍可完成 unsigned/minified 本地验证。签名值统一从以下环境变量读取，也可从未跟踪的根目录 `release-signing.properties` 读取：

```text
LYRICS_CARD_STORE_FILE
LYRICS_CARD_STORE_PASSWORD
LYRICS_CARD_KEY_ALIAS
LYRICS_CARD_KEY_PASSWORD
```

本地配置格式见 `release-signing.properties.example`。不得提交 keystore、密码、alias secret 或 base64 keystore。

`Production Release Candidate` workflow 只能手动运行，要求完整 40 位 commit SHA、匹配的 production `versionName` 以及受保护的 `production-signing` environment。它只构建并上传已验证签名的 Production Release APK/AAB、`SHA256SUMS`、metadata 和可选 R8 mapping；它没有写仓库权限，也不会创建 tag、GitHub Release 或商店发布。

公开 Beta 使用 `v1.0.0-beta.N` prerelease，并以测试 keystore 签署正式包名 APK。Beta 与未来生产签名不兼容；安装正式版前必须卸载 Beta。Beta 不使用上述 production release workflow，也不得被描述为正式发布候选。

发布前必须完成 [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md)，并由独立 Final Integration & Release Readiness Reviewer 对同一 commit 和同一组 artifact hash 复核。

## 项目结构

```text
app/                         Android Compose 应用与 Android tests
renderer/                    本地 React/CSS Renderer、合同、tests 与 Golden
docs/ARCHITECTURE.md         运行时与构建架构
docs/RELEASE_READINESS.md    当前候选的真实门状态
docs/internal/               集成与质量门证据
.github/workflows/           CI 与手动 release-candidate workflow
```

## License 与 notices

项目原创代码、设计、文档与项目资产受根目录 [Source Available License](LICENSE) 约束，版权归属保持为 `Copyright (c) 2026 Qrzzzz. All rights reserved.`。第三方组件继续适用各自许可证；详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。版本变更见 [CHANGELOG.md](CHANGELOG.md)。
