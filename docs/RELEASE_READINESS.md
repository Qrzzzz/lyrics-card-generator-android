# Release Readiness

本文定义 `1.1.1`（`versionCode 10101`）及后续修订版的发布条件。`FINAL READY` 是绑定具体 source SHA、产物字节和 Actions run 的结论；静态文档、旧版结果、单台设备启动成功或合同测试均不能签发该结论。

## 必须闭合的发布链

1. 同一主干提交的 Android Quality Gate 和 Dependency Security 成功。主干保留 Renderer、四变体 JVM、lint、R8、APK/AAB 与 Windows 中文路径门；PR 的中文路径门按构建相关改动执行并保留稳定检查结果。已有同 SHA 主干证据可以引用，无需在本地重复全套。
2. `Production Release Candidate` 固定可信 main dispatch 的精确 SHA，检查主干祖先关系、同 SHA Quality Gate 和版本唯一性。main 前进不自动废弃已批准候选。签名 job 在审批前后拒绝来源错配；候选自带脚本不能授权自身。
3. 同一签名 job 生成生产 APK/AAB、metadata、mapping、checksums 以及单独的正式测试 APK，验证生产证书连续性并生成 GitHub build provenance。公开候选 metadata 固定为 `PROVISIONAL / device NOT RUN / finalReady=false`。
4. `Capture Device Gate Evidence` 从同一候选 run 下载并验证上述产物，在获授权的 API 26/30/33/36 AVD 与实体设备执行真实测试。设备 Runner 不构建或重签 APK，也不持有生产签名凭据。
5. `Final Device Gate` 重新核对 workflow identity、冻结 source/run/artifact、source → producer → validator 的祖先链、公开候选及测试 APK 的 provenance、实际字节哈希、完整设备矩阵和成功日志，产生 `FINAL READY` verdict。验证器可位于更新的受保护主干提交。
6. 发布者核对三个 run 的来源和产物，上传已验证的候选原字节。Release 必须列出 source、candidate/evidence/final run、证书指纹及 checksums；公开下载后的哈希与 provenance 仍须通过。

任何缺失、失败、跳过、旧候选或未完成项必须保留 `NOT RUN/BLOCKED/FAIL`，不能通过修改文档、手工填写 PASS 或重复运行后抹掉失败记录来关闭门。诊断实验与正式一次性矩阵分开保存；修改产品或 APK 内测试后重新冻结候选。只修改 CI/采集/验证器时保留 APK 原字节，绑定新的可信 workflow SHA，并按实际变化重新采集受影响证据；已有失败记录不得删除。

## 设备验收范围

- API 26：fresh install、核心编辑和导出、Renderer/WebView 生命周期、恢复与 ATF。
- API 30：fresh serif 1×→2× measure/spec、20 次连续 2× 导出、实际取消与重试后的临时文件清理、4 GB 配置与实际内存记录、核心编辑/导出与恢复/ATF。
- API 33：核心编辑、恢复/ATF、运行中的真实 TalkBack 手势导航、200% 字体。
- API 36：完整正式 instrumentation、20 次导出、至少 30 分钟编辑耐久及状态恢复。
- 获授权实体设备：实际安装字节、核心编辑、真实保存和分享选择器。测试不向联系人发送文件，只清理自身创建的项目和验证文件。

每个环境记录实际 API、设备/模拟器类型、AVD 身份、build fingerprint、WebView、配置及实际 RAM。安装后从设备的真实 base APK 重新计算生产与测试 APK SHA-256，并检查实际 package/version；不能复制主机哈希冒充设备读数。ATF 不能代替 TalkBack，打开导出页不能代替保存/分享，失败 instrumentation 或不足 30 分钟的日志不能支持 PASS。

## 外部保护与依赖基线

2026-09-04 复核：main 已设置 required checks 并对管理员强制执行；`production-signing` 和 `final-device-gate` 仅接受受保护分支、保留 Qrzzzz reviewer，管理员 bypass 已关闭。仓库目前只有该维护者，因此允许其本人审核自己发起的运行；这项明确的单维护者设置不等同于取消 required reviewer。具体运行仍需通过 GitHub 环境批准点。

Dependency Graph、Dependabot alerts/security updates 和三生态更新已启用。npm 已修复本轮发现的依赖漏洞；构建/测试工具链的原始告警、范围、逐项负责人、截止日期和公开处置链接见 [依赖基线](security/dependency-baseline-2026-09-04.md)。不得把正式运行时图没有这些依赖扩大为所有生态零风险。

## 历史结论的边界

- 2026-08-08 的 API 30 超时绑定旧 Debug 候选。后续 `c3ff32f` 候选的 API 30 serif probe、20× 和 4 GB 项已有成功诊断日志，但 API 36 测试失败；这些记录不能代替本版同一最终候选矩阵。
- v1.1.0 发布说明明确记录设备矩阵和耐久门由 owner 豁免；其公开 APK/AAB 使用连续生产证书，但五个公开资产没有 GitHub attestation，metadata 也未满足现行来源合同。本版不得追溯伪造其 provenance，或将该豁免沿用为新版验收。
- 2026-09-04 的 `d7f3b2e` 候选 [33869390382 / attempt 2](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/33869390382/attempts/2) 已通过签名和六个文件的来源证明；[设备运行 33872306376](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/33872306376) 在 API 26 系统保存测试中报 `missing DocumentsUI filename field`，该组 2 tests / 1 failure、0 个完成门。API 30/33/36 和真机均未执行；失败 artifact 保留原始日志，后续修复必须重新冻结候选。
- #13 的中文路径 wrapper 已由 PR #14 实现并复核关闭；其他原 issue 也须分别绑定实际 PR 与其适用的验收证据。依赖机制或隐私规则的单项验收不能替代完整设备门与公开产物核验。

详细执行清单见 [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md)，来源合同见 [RELEASE_PROVENANCE.md](RELEASE_PROVENANCE.md)。
