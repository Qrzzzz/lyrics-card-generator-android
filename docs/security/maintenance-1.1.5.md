# 1.1.5 安全维护与 API 30 证据

核对日期：2026-09-15。发布前默认分支基线 `2e9db87`：开放告警 9 条（3 high / 5 medium / 1 low），Issue #9/#35/#45，PR #51/#56/#57。原始历史记录及期限不改写。

## 根因与最终依赖

| 范围 | 最终处理 | 验证 |
| --- | --- | --- |
| buildscript / bundletoolCli / 十二个 UTP 配置 | Protobuf Java/Kotlin 3.25.5，只在相应宿主配置加约束 | 实际依赖解析 PASS；UTP core 与 Kotlin 路径必需存在；混合版本、旧版本或未解析路径拒绝 |
| AndroidTest compile/runtime | Guava 33.4.8-android，替代只升级 compileOnly 的 #51 | 两侧版本和实际 Android JAR 校验 PASS，duplicate classes 与 R8 PASS |
| ATF 3.1.2 runtime | jsoup 1.23.1 | 解析与真实 ATF instrumentation PASS；覆盖主干扫描新识别的 #64，ATF 未公开的 jsoup 不强行加入 compile API |
| app compile/runtime | 不增加以上宿主/测试依赖 | 隔离校验 PASS |

#51 在 run 34817769504 首先失败于 `verifyNettyResolution`，实际原因是 Guava 32.0.0-android 编译依赖与运行时 strictly 28.2-android 冲突。对齐 32.0.1 后，本地 test APK R8 进一步暴露缺失 `java.lang.reflect.AnnotatedType`；改为 33.4.8-android，未关闭 R8 或添加忽略规则。[Guava 33.0.0 上游说明](https://github.com/google/guava/releases/tag/v33.0.0)确认移除了这些 Android 上不可用的方法。

## 自动执行证据

产品/依赖快照 `7735352`，本地 ASCII worktree 提交 `1c96e3a`；后续 `e3f6782` 只补强解析检查，应用和测试源码/依赖未变。诊断包使用 Android debug 证书，不能作为正式发布签名证据。

- **PASS**：`:app:verifyProtobufAndAccessibilityResolution :app:bundleProductionRelease :app:dumpProductionReleaseBundleManifest :app:connectedProductionReleaseAndroidTest`，后接下面三个 class selector；Gradle 8.13 / JBR 21，真实 UTP connected 执行。
- **PASS**：`:app:verifyProtobufAndAccessibilityResolution --offline`，补强后的实际 Android JAR 校验。
- **PASS**：28 项 Node CI/publish 合同；dependency-security、production-release、frozen-source、device-gate-evidence 四组 PowerShell 合同。设备证据合同是夹具测试，不计入模拟器测试数。
- AVD `lcg_113_api30_probe` / API 30 / x86_64 / WebView 83.0.4103.106；已卸载旧应用和 test APK，当前包全新安装。
- 应用 APK SHA-256：`20bf0700c78595762b38a8b4c9627bb879ccd9f1b00f940b0a93567755bd951f`。
- Test APK SHA-256：`859e516a5b075ebdccee78ed0f572ccc7ce545f69bdf6c11100a2ad83d98614a`。

| 实际 instrumentation selector | 结果 |
| --- | --- |
| `QualityStressTest#a_serifOneXThenTwoXProbeUsesTheSameRendererLifecycle` | PASS，15.594 秒；1040×1613 / 2080×3226，rendererErrors=0、partialFiles=0 |
| `QualityStressTest#a_cancelledExportRemovesPartialAndRetryProducesValidPng` | PASS，15.803 秒；真实 partial 创建与取消，清理后 0，generation 推进、重试 PNG 有效 |
| `AccessibilityFrameworkTest` | PASS，6.596 秒；home/editor/export/settings 四阶段，ATF preset 3.1，每阶段 12 checks |

XML 汇总：3 tests / 0 failures / 0 errors / 0 skipped。原始 connected 日志、logcat、XML、解析日志、合同日志及 SHA256SUMS 保存在维护者机器 `C:\Users\qrzzz\Downloads\LCG_Android_1.1.5_evidence`。正式候选的 source/run/哈希另由发布记录绑定，不以诊断包替代。

## 默认分支复扫后的最终 jsoup 补丁

PR #63 合并为 `09fe2bd4e171cd4acae10b8d83e06553abb88aba`；Dependency Security 34968323516 成功，原告警 #1/#2/#5/#6/#10/#11 全部 fixed。扫描随后新建 medium #64（[GHSA-pmhh-3w7g-xqp8](https://github.com/jhy/jsoup/security/advisories/GHSA-pmhh-3w7g-xqp8)，>=1.14.3 且 <1.23.1），因此最终测试依赖及校验下限升级到 1.23.1。

补丁 `0bcf162`，本地等价提交 `5ade299`：实际解析、test APK R8 和 API 30 全新安装的三个 connected UTP 测试再次 PASS，0 failures / 0 errors / 0 skipped。取消清理重试 15.323 秒、serif 1×/2× 15.239 秒、ATF 四阶段 5.167 秒。应用 APK 字节未变；新的诊断 test APK SHA-256 为 `1a8593214b9d296fa2c1a4c34818c47750bbdf85424a95f884ed3bf84eb76d61`。同一证据目录新增 `jsoup-1.23.1-api30-tests.xml`、`jsoup-1.23.1-api30-logcat.txt`、`lcg-115-jsoup-connected.log`，上节旧测试记录保留其原始范围。

## #9 与后续事项

1.1.3 的正式 API 30 结果见 [v1.1.3](../releases/v1.1.3.md)。此后仅将 ExportAssembly 从 RendererController 抽出，未修改 8 秒超时、渲染协议或保存竞态修复。本轮同一旧 WebView 的 fresh-install serif 和取消重试再次 PASS，当前版本未复现原始风险；已有确定性旧回调、取消交接及保存竞态回归继续受完整 CI 保护。按此范围关闭 #9，不将历史 FAIL 改写为 PASS。

Kotlin #53 / Commons Compress #7/#8 仍待 1.1.6，责任人 Qrzzzz、原 medium 期限 2026-10-02。#35/#45 保持 OPEN；本次六条告警需待默认分支 dependency submission 后核实 fixed，不能人工 dismiss 冒充修复。#56/#57 留在计划后续版本。

**NOT RUN**：实体手机六项、跨 API 矩阵、耐久、低内存、TalkBack、大字体。自 1.1.5 起实体手机人工验收为可选；以上自动回归不表示这些专项通过。
