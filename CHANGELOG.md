# Changelog

本文件记录用户可见的版本变化。内部逐提交重构与实验过程不在此展开。

## 1.1.3 — 保存与导出恢复修复

- 修复保存图片尚未结束时重新导出，旧保存结果覆盖新导出进度或错误提示的问题；同一图片连续保存时，仅最新一次保存更新提示，已开始的文件写入仍正常完成。
- 修复取消导出时，主线程取消消息返回或 IO 文件交接中断导致的临时文件清理遗漏，保证清理完成后再允许重试。
- 在 API 30 / WebView 83.0.4103.106 隔离模拟器上完成正式签名 1.1.3 候选的首次 serif 1×→2× 与取消清理/重试定向复核，均通过；该结果不代表完整跨 API 矩阵通过。
- 保留项目存储格式、Renderer 协议、8 秒 spec 超时和现行生产签名、发布流程。

## 1.1.2 — 维护与依赖安全候选

- 修复构建、测试和 Renderer 工具链中的 Bouncy Castle、Netty、JDOM、jose4j 与 Vitest 已知告警，并继续保留实时 npm 审计、依赖审查和默认分支 Gradle 依赖提交。
- 将拉取请求检查按影响范围路由；依赖、构建配置、发布输入和未知路径仍执行完整质量门，普通文档和已知源码改动可使用受限门。
- 将 React、React DOM 与类型声明对齐到已经验证的 19.2 解析树，避免为清理历史更新队列引入 19.3 功能升级。
- 本候选不改变编辑、渲染、导出协议或生产签名策略。`focused-manual-v1` 六项真机确认尚未执行，API 30 历史超时及 #35 剩余风险不视为通过。

## 1.1.1 — 安全与正式产物验收修复

- 修复正式签名来源检查执行候选自带校验器的漏洞，在任何候选代码执行前从可信 workflow 校验精确主干提交。
- APK、AAB 与正式测试 APK 在受保护的同一签名任务中生成来源证明；设备 Runner 只消费已证明的产物，无需持有生产密钥。
- 对每个构建变体的实际合并 Manifest 和编译资源验证备份、云恢复及设备迁移排除规则。
- 修复 fast-uri、Browserslist 依赖告警，并为构建与测试工具链告警建立逐项负责人和期限。
- 加强真实安装 APK 哈希、设备身份、TalkBack、保存、分享与耐久日志的发布验收。
- 正式版使用 `versionCode 10101`；发布结论以同一源码和产物的受控设备门为准。

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
