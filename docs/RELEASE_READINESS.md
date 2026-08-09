# Release Readiness

## 当前结论

**PROVISIONAL / NOT FINAL READY**

源码版本已经收敛到 `1.0.0` 的 production 配置，但完整发布门尚未通过。本文不把本地 build、JVM tests 或 CI infrastructure 等同于设备矩阵、真机或最终 Reviewer PASS。

## 已实现的产品与工程能力

- Compose 原生 Home、Editor、Export、Settings 与 typed Navigation；
- 六步编辑流程、UDF state ownership、autosave、undo/redo 与 project-ID 恢复；
- Room v2 `cover_assets` reference ledger、v1→v2 migration、orphan cleanup、thumbnail/export lifecycle；
- 固定 Origin、no-network、no-file/content-access Renderer WebView；
- session/generation/latest-wins、串行 export、cancel、timeout 与 renderer-process recovery；
- 30-case Renderer Golden regression 数据集和合同/安全/JVM/instrumentation test source；
- production R8/resource shrinking、APK/AAB 构建路径；
- 正式 CI、手动 signed production candidate workflow、统一 local/CI signing configuration；
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

当前小米设备不属于本工作流 H 的授权范围，不得安装、重试或调整设备安全策略。

## External release gate

仓库只包含 signing infrastructure，不包含生产 keystore 或密码。没有 production signing secrets 不阻止 unsigned/minified local/CI verification，但会阻止生成可公开分发的 signed APK/AAB，并因此阻止 public release。

即使生产 signing secrets 后续可用，仍必须先完成上述 G/真机/Reviewer Gate；手动 workflow 的 signed artifact upload 也不是 GitHub Release 或商店发布授权。

## 证据来源

- H 的最终本地命令、artifact path/hash 与 source cleanliness：`docs/internal/release-engineering-gate-2026-08-09.md`；
- H 起点前的 G 终局失败：`docs/internal/quality-gate-2026-08-08.md`；
- 完整发布步骤与 STOP 条件：根目录 `RELEASE_CHECKLIST.md`。
