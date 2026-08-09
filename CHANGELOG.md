# Changelog

本文件记录用户可见的版本变化。内部逐提交重构与实验过程不在此展开。

## 1.0.0 — Pending public release

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

> `1.0.0` 尚未公开发布。最终设备矩阵、获授权真机、production signing secret 与独立 Reviewer 状态以 `docs/RELEASE_READINESS.md` 为准。
