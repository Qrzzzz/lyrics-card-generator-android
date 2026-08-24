# Release Readiness

## 当前结论

**PROVISIONAL / NOT FINAL READY**

源码版本当前为 `1.1.0`（`versionCode 10100`）正式发布候选。相较 1.0.1，本次集中修复中文路径构建、备份与 D2D 策略、依赖安全、正式签名 provenance 与最终真机证据门；本文仍不把本地 build、JVM tests、单台真机冒烟或 CI infrastructure 等同于完整设备矩阵与最终 Reviewer PASS。

## 已实现的产品与工程能力

- Compose 原生 Home、Editor、Export、Settings 与 typed Navigation；
- 六步编辑流程、UDF state ownership、autosave、undo/redo 与 project-ID 恢复；
- Room v2 `cover_assets` reference ledger、v1→v2 migration、orphan cleanup、thumbnail/export lifecycle；
- 固定 Origin、no-network、no-file/content-access Renderer WebView；
- session/generation/latest-wins、串行 export、cancel、timeout 与 renderer-process recovery；
- 30-case Renderer Golden regression 数据集和合同/安全/JVM/instrumentation test source；
- production R8/resource shrinking、APK/AAB 构建路径；
- 正式 CI、受控 main-tip/same-SHA Quality Gate 的 signed production candidate workflow、生产证书连续性与同 job build provenance；
- 正式 README、architecture、changelog、privacy、release checklist 与 third-party notices。

以上项目不再作为“未来 Alpha TODO”。

## H 后必须重验的 Gate

工作流 H 修改了 signing/build variant 使用方式、CI/release workflow 和正式文档。因此最终工作流 G 与独立 Final Reviewer 必须从 H 的最终 commit 重新构建同一 production candidate，并按该候选 APK/AAB 的 SHA-256 绑定所有设备证据。H 之前 binary 的结果不能替代 H 后产物。

当前工作流 G 的终局 Reviewer 已判定设备门 **NOT PASS**：最终 renderer binary 在 fresh API 30 的 serif 1×→2× probe 中，于导出前的 measure/spec 请求触发既有 8 秒 timeout。该事实不能被 H 的非设备构建结果覆盖或改写。

仍未决的最终候选门：

- API 26、30、33、36 的候选安装、核心流程、恢复与导出验证；
- API 30 的上述 timeout 修复后重新执行 20 次连续导出；
- 4 GB 环境的 2× 导出与内存证据；
- 30 分钟连续编辑、后台/进程恢复与 temp cleanup；
- API 33 TalkBack 核心流程；
- 200% font scale；
- 一台获明确授权的实体设备。

历史工作流 H 不包含物理设备授权。本 Beta 工作流已单独授权固定真机 `b2601eb1` 执行最小冒烟并安装最终 Beta；该授权不包含修改 MIUI/USB 安全策略，也不等同于四 API 矩阵、20 次导出或 30 分钟耐久 PASS。

## External release gate

仓库只包含 signing infrastructure，不包含生产 keystore 或密码。公开 Beta 可以使用明确披露的测试证书签署，但该 APK 不是生产签名产物，不能升级为正式版；没有 production signing secrets 时，仍会阻止为当前候选生成新的正式 signed APK/AAB。

即使生产 signing secrets 后续可用，仍必须先完成上述 G/真机/Reviewer Gate；手动 workflow 的 signed artifact upload 也不是 GitHub Release 或商店发布授权。

`Production Release Candidate` 与最终设备结论采用诚实的两阶段语义：第一阶段的 signed/attested artifact metadata 永远是 `PROVISIONAL / device NOT RUN / finalReady=false`；获授权设备运行另行产生受控 evidence artifact，随后由不接触 signing secrets 的 `Final Device Gate` workflow 下载同一 candidate bytes、test APK 与日志，运行 `scripts/validate-device-gate-evidence.ps1`。只有后置 job success 才能产生 `FINAL READY` verdict。当前没有 v1.1.0 的完整受控设备 evidence，因此该后置门按设计 fail closed；`tests/fixtures` 中的正例不能用于发布。

当前还没有 `.github/workflows/capture-device-gate-evidence.yml` 的真实 producer/run；它必须由主任务在获设备授权后另行实现或提供，并满足 same-repository、same-source-SHA、main、workflow_dispatch、completed/success 与受保护人工批准要求。consumer 已硬编码拒绝其他 workflow identity，故在 producer 和 `final-device-gate` environment reviewer 均未配置前，流程不会产生可发布的 FINAL READY。

当前 Gradle task graph 只提供 `productionDebugAndroidTest`，没有可直接运行的 `productionReleaseAndroidTest`；因此历史/现有 Debug test APK 不能冒充正式签名 APK 的 instrumentation 证据。主任务的 capture producer 还必须先提供一个实际以 `com.qrzzzz.lyricscard` 为 target、可由 host `aapt2`/`apksigner` 验证 package/version/certificate 的受控 test APK，或明确补齐等价的 release-target 测试构建链。本提交不为绕过该缺口而接受 Debug target。

仓库内已定义 source/provenance 合同，但 `main` ruleset/branch protection、`production-signing` required reviewers/deployment policy 与管理员 bypass 只能在 GitHub Settings 配置。在管理员完成并独立核验这些外部前置条件之前，P1 发布供应链边界仍为 **PROVISIONAL**；本地合同测试也不能替代真实 GitHub-hosted attestation。详见 `docs/RELEASE_PROVENANCE.md`。

## 证据来源

- H 的最终本地命令、artifact path/hash 与 source cleanliness：`docs/internal/release-engineering-gate-2026-08-09.md`；
- H 起点前的 G 终局失败：`docs/internal/quality-gate-2026-08-08.md`；
- 完整发布步骤与 STOP 条件：根目录 `RELEASE_CHECKLIST.md`。
