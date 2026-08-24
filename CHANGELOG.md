# Changelog

本文件记录用户可见的版本变化。内部逐提交重构与实验过程不在此展开。

## 1.1.0 — 发布与设备验证加固

- 品牌名称统一为 `Lyric Card Generator`，并将长期图标源收录到仓库；启动器、圆形、自适应与启动页均复用同一张图。
- 修复 Windows 中文路径下 JVM tests 全量 `ClassNotFoundException`，提供 ASCII staging wrapper。
- 明确 Android 12+ 备份与设备到设备迁移策略，并补充合同测试。
- 增加依赖安全检查、Dependabot 与依赖图覆盖。
- 为正式签名候选增加生产证书连续性、来源证明与可验证 provenance。
- 增加最终真机证据门，阻止缺少真实设备证据的候选被误判为可发布。
- 正式版使用 `versionCode 10100`。

## 1.0.1 — 图标对齐修复

### 外观

- Android 启动器图标与启动页改用 Windows 桌面版打包所用的同一图标资源；Android 8+ adaptive icon 不再回退到旧的矢量字形。

### 发布

- 正式版使用 `versionCode 10003`。

## 1.0.0 — 正式版

### 外观

- 应用主题现在提供“跟随系统”“浅色”“深色”三个选项；新安装默认跟随系统，Beta 旧设置会迁移为对应的浅色或深色选项。

### 发布

- 正式版使用 `versionCode 10002` 与生产签名；安装测试签名 Beta 的设备需要先卸载 Beta。

## 1.0.0-beta.1 — Public beta

### 新增

- Android 原生 Home、六步 Editor、Export 与 Settings 体验；
- 本地项目管理、示例项目、自动保存、撤销/重做与恢复；
- 竖版、横版、自动高度、浅色/深色、字体与视觉设置；
- 用户主动发起的网易云歌曲搜索、链接解析、歌词与封面导入；
- 1×/2× PNG 导出、系统文件保存与 Android 分享；
- TalkBack 语义、large-font/adaptive layout 支持及相应自动化 test source。

### 可靠性与安全

- 卡片编辑与导出使用 APK 内置的离线 Renderer，不加载远程网页；
- Renderer 强制固定 Origin、no-network、no external navigation 与有界消息协议；
- Preview 采用 latest-wins，导出采用串行 session/request 校验，并支持 cancel、timeout 与 renderer-process recovery；
- PNG 使用有界分块回传、Native 结构/尺寸校验和原子文件发布；
- Room 项目存储加入 cover reference ledger、migration、orphan/partial cleanup 与原子 export metadata；
- 网易云客户端加入 HTTPS host/redirect 校验、timeout、响应大小与错误分类。

### 工程与发布

- 增加 Renderer/JVM/lint/R8/Production APK/AAB 正式 CI 门；
- 增加只手动执行、只面向 signed `productionRelease` candidate 的 release workflow；
- 增加 SHA-256、隐私、第三方 notices 与发布检查清单。

> 本版本使用测试签名，仅用于公开 Beta 与真机核验。`1.0.0` 已作为生产签名正式版发布；完整设备矩阵与独立 Reviewer 状态以 `docs/RELEASE_READINESS.md` 为准。
