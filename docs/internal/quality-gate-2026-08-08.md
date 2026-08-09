# Lyrics Card Generator Android — 工作流 G2 质量门报告（2026-08-09）

> 结论：**FIX REQUIRED / AUTOMATION FAIL**
> 实体小米设备：**EXTERNAL / PHYSICAL BLOCKED**（按任务边界未再次尝试）

- 时区：Asia/Shanghai
- 锁定基线：`b1fa76d78731188f1607657d71e44010d07b5740`
- 分支：`codex/quality-device-hardening-recovery`
- 最终 renderer binary commit：`553556b538347298c4a6558fdb7d17a45c28e85d`
- 最终 ProductionDebug APK SHA-256：`af5f1d8d8c4b32c9a4fab49e4d80db1a317c21dc0d81b1f26ef17dd82f996783`
- 最终 ProductionDebug test APK SHA-256：`b2b06a540fce7fb556aeb2d9981a9f412903d9c059c102bd55af023cd71c60f3`
- 最终阻断：API30 fresh-install serif 1×→2× probe 在执行导出前的 `measure/spec` 请求触发既有 8 秒超时，1/1 失败。
- 纠正额度：2/2 已用尽；按总指挥边界，不重跑、不再修改产品或 harness。
- 独立 Reviewer：由总指挥另开任务；本报告不冒充独立 review。

## A. Scope

本工作流只负责从干净 `b1fa76d…` 精确重建 API30 renderer “最佳 A”最小修复，并执行静态、Golden、API26/API30 及后续设备质量门。开始时已证明：

```powershell
git rev-parse HEAD
git rev-parse refs/heads/codex/quality-device-hardening
git status --short
```

前两条均精确返回 `b1fa76d78731188f1607657d71e44010d07b5740`，新 worktree clean。旧 G dirty worktree与旧产物目录只读取证，未复制整批 dirty changes，未 cherry-pick dirty changes，未修改主工作区。

未执行 push、PR、merge、tag、Release、商店发布或实体小米设备重试。

## B. Changes

### 提交

| Commit | 变更 |
|---|---|
| `f9d39b0` | 重建字体去重、family 单槽缓存、DOM/SVG 单槽缓存、可复用 canvas、Image/Canvas 清理、根 Blob 单次 ArrayBuffer 读取与可取消分块。 |
| `ae45074` | 将 API30 retained-memory verdict 锁定为同阶段 app+renderer aggregate；增加 serif 1×→2× probe。app-only 仅保留诊断。 |
| `bf905a0` | 第一轮纠正：Image source 在 `drawImage` 后、`canvas.toBlob` 前释放。 |
| `553556b` | 第二轮且最后一轮纠正：新增 yield 使用 WebView69 可用的 `setTimeout`，不再使用 `globalThis`；增加针对性回归测试。 |

### Renderer 最小实现

- 删除未使用的 `Source Han Sans/Serif Heavy Local` 字体别名；保留主 family、OTF 文件和像素语义。
- 字体 CSS 使用按实际 family 键控的单槽缓存：同 family 复用，切换替换，失败不缓存，生命周期清空。
- SVG/source 使用 DOM revision、spec、cover、auto-height、lifecycle 键控的单槽缓存。静态检查确认 DOM 构建不读取 `pixelRatio`，因此未将其加入 DOM 缓存键。
- 保留 app-lifecycle reusable canvas；每次导出清像素，lifecycle 结束时归零。
- 保留 `canvas.toBlob`；根 Blob 只读一次完整 ArrayBuffer，再以 `Uint8Array.subarray` 分块；不使用 `blob.slice()` 子 Blob。
- 保留 WebView69 `FileReader.readAsArrayBuffer` fallback；分块间显式 `setTimeout(..., 0)` 让出事件循环，使 cancellation 可生效。
- Image source 在 draw 后、PNG encode 前释放；finally 中保留幂等清理。

### Golden

只更新了证据允许的两张 serif PNG；另外 28 张 SHA-256 不变：

| Golden | 旧 SHA-256 | 新 SHA-256 |
|---|---|---|
| `landscape-sixteen-nine-long-serif.png` | `d816ac616aa825f0bb3ed623dcbeccb3f13c2b50d91cfee59f2e068b34dbd9d7` | `542a07f8e9b2b7dd5208120b8a4390fad8dd5c2a087ab2eef7dcb3d2822d2fbc` |
| `serif-center-cream.png` | `ac0e38f3b18f5a2c946d8f3f7391aae910c38c11b0159d3ce1fce31ec0e480b8` | `65c174a2b94d52c40216459ab8c0c17cf073489ce58a69ab08ac83c39af0e53a` |

- 最终 source fingerprint：`2572dcf40b96dc55914e31b4c0db903871ca50b21c5be2b9a9928151e0e0fb74`
- fixture fingerprint：`0476356f485da8a91773a37888b35c3ff7d50c63d1169b2e612bae9e0c8a2a8c`
- fresh serif 与同页 sans→serif 每轮均 byte-exact，SHA-256 均为 `542a07f…d2fbc`。

## C. Invariants preserved

以下既有契约未改变：

- trusted local origin、no-network、wrong-origin rejection；
- session/generation/latest-wins；
- export mutex；
- PNG MIME、尺寸、签名与正式落盘验证；
- bounded chunk size 与既有错误契约；
- cancellation、partial cleanup 与 renderer recovery 语义；
- `canvas.toBlob` 输出路径；
- API26 WebMessagePort/FileReader 兼容路径。

未采用 Object/Blob URL SVG、toDataURL 输出、decoded-image cache、1×1 retirement、CDP GC、固定延迟、主动 reload/kill WebView、WOFF2 考古或阈值修改。

## D. Tests actually run

### 最终 clean renderer commit

```powershell
cd C:\CodexTmp\lcg-g2-recovery-build-f9d39b0\renderer
npm run check
npm run golden:test
```

- renderer：8/8 suites，78/78 tests，typecheck/validator/build PASS。
- production bundle：未出现 `globalThis`。
- Golden：最终实现连续三轮 30/30 exact；提交后 ASCII clean worktree 再跑一轮 30/30 exact，全部 PASS。
- 30 张 PNG 与第二轮纠正前已审核基线相比 `changed_count=0`。

### Android/JVM/release/lint/R8

```powershell
.\gradlew.bat `
  :app:testAlphaDebugUnitTest `
  :app:testProductionDebugUnitTest `
  :app:assembleAlphaDebugAndroidTest `
  :app:assembleProductionDebugAndroidTest `
  :app:assembleProductionDebug `
  :app:assembleProductionRelease `
  :app:bundleProductionRelease `
  :app:lintProductionRelease `
  --rerun-tasks --console=plain
```

- Gradle：216/216 tasks executed，`BUILD SUCCESSFUL in 1m 18s`。
- AlphaDebug JVM：31 suites / 143 tests / 0 failures / 0 errors / 0 skipped。
- ProductionDebug JVM：31 suites / 143 tests / 0 failures / 0 errors / 0 skipped。
- 两套 test APK、ProductionDebug APK、ProductionRelease unsigned APK、AAB：PASS。
- R8、resource shrinking：PASS。
- lint：34 个非阻断 issue，0 Fatal，0 Error。

最终构建产物：

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app-production-debug.apk` | 62,747,363 | `af5f1d8d8c4b32c9a4fab49e4d80db1a317c21dc0d81b1f26ef17dd82f996783` |
| `app-production-debug-androidTest.apk` | 4,754,504 | `b2b06a540fce7fb556aeb2d9981a9f412903d9c059c102bd55af023cd71c60f3` |
| `app-alpha-debug-androidTest.apk` | 4,754,488 | `fa566e267f6adbc46198f3ff6c66e5e15cbd33dede6bbdb463a17095b883d5f6` |
| `app-production-release-unsigned.apk` | 45,157,865 | `e35e7235b07b7aca55e9592c44ec41e09a9faa4f566d2c9e53b9be883bd58c96` |
| `app-production-release.aab` | 39,916,322 | `828d4113c315a58fa12fd5558ad3b1338b0eba39bfd0c8eb042c4c61751b94a7` |

### 最终设备命令骨架

```powershell
adb -s emulator-5554 install -r -t app-production-debug.apk
adb -s emulator-5554 install -r -t app-production-debug-androidTest.apk
adb -s emulator-5554 shell sha256sum <device-base.apk>
adb -s emulator-5554 shell am instrument -w -r `
  -e class <test-selector> `
  com.qrzzzz.lyricscard.debug.test/androidx.test.runner.AndroidJUnitRunner
```

最终 API26 选择器：

- `com.qrzzzz.lyricscard.ui.AvdMatrixSmokeTest`：PASS 1/1，29.897 s。
- `com.qrzzzz.lyricscard.ui.ArchitectureRestorationTest`：PASS 1/1，10.788 s。
- `com.qrzzzz.lyricscard.ui.AccessibilityFrameworkTest`：PASS 1/1，12.953 s。

最终 API30 选择器：

- `QualityStressTest#a_serifOneXThenTwoXProbeUsesTheSameRendererLifecycle`：**FAIL 1/1**，11.414 s；`TimeoutCancellationException: Timed out waiting for 8000 ms`。
- 按锁定规则，最终 binary 的 20×、core smoke、recovery、ATF 均未继续执行。

## E. Device validation

| API | AVD / image | Runtime RAM | WebView | Device APK hash | 结果 |
|---|---|---:|---|---|---|
| 26 | `lcg_quality_api26`; Google APIs x86_64 rev16; Android 8.0.0 | 4,042,036 KB | Chrome/WebView `69.0.3497.100` | host/device 均 `af5f1d8…96783` | smoke、恢复、ATF 各 1/1 PASS；无 `globalThis`、OOM、code 5、`.part/.tmp`。 |
| 30 | `lcg_quality_api30`; Play Store x86_64 rev10; Android 11 | 4,023,600 KB | `83.0.4103.106` | host/device 均 `af5f1d8…96783` | fresh-install serif probe 1/1 FAIL；在 20× 之前 STOP。 |
| 33 | `lcg_quality_api33`; Play Store x86_64 rev9 | 未进入最终 binary gate | 未进入最终 binary gate | 未安装最终 binary | NOT RUN：由 API30 STOP 触发。 |
| 36 | `lcg_quality_api36`; Play Store x86_64 rev7 | 未进入最终 binary gate | 未进入最终 binary gate | 未安装最终 binary | NOT RUN：full connected、20×、真实 30 分钟均未执行。 |

API33 TalkBack 核心流未在最终 binary 上执行，因为 API30 强制 STOP 先发生；不得沿用旧 binary 的 TalkBack 结果。

实体小米设备缺失单列为 `EXTERNAL / PHYSICAL BLOCKED`，没有把它包装成自动化失败，也未再次尝试。

## F. Remaining issues

1. **阻断**：最终 binary 在 fresh API30 首次 serif probe 的 pre-export `measure/spec` 请求超过既有 8 秒 timeout。失败发生在任何 1×/2× PNG 成功证据之前。
2. 纠正额度已经用完，且总指挥明确要求此后任何产品或 harness 失败立即停止；因此本工作流不得继续改代码或重跑。
3. 最终 binary 尚缺 API30 20×/core/recovery/ATF、API33 core/recovery/ATF/TalkBack、API36 full connected/20×/30-minute。
4. 独立 Reviewer 仍由总指挥单独执行。

严格 verdict：**FIX REQUIRED / AUTOMATION FAIL**。不得标记 `AUTOMATION PASS`、`READY` 或仅因实体设备缺失而 `BLOCKED`。

## G. Cross-workstream impacts

- 产品变更仅位于 renderer；Android 产品接口、安全边界、数据库 schema 和 release 配置未改变。
- `QualityStressTest` 增加 serif probe，并把 app-only 内存值降为诊断；最终 verdict 使用 host 同阶段 app+renderer aggregate。
- Golden 只有两张 serif PNG 更新；任何消费 Golden 的 Reviewer 应使用本报告中的新 SHA。
- 其他负责人不得复用 `bf905a0` 或旧 G dirty binary 的设备结果为 `553556b` 背书。
- 当前候选不是公开发布级最终候选；后续工作必须从 API30 首次 spec/measure timeout 这一事实开始。

## H. Commit / workspace state

- renderer binary commit：`553556b538347298c4a6558fdb7d17a45c28e85d`。
- 修改范围：12 个受控文件，包括 renderer source/tests/Golden、Golden 脚本和 `QualityStressTest`；无未预期产品文件。
- ASCII 构建 worktree 与主执行 worktree在最终 binary commit 上均已验证 clean；本报告为后续 docs-only 变更。
- 未 push、未 PR、未 merge、未 tag、未 Release。

## Superseded evidence — 不得冒充最终证据

### 旧 G dirty 试验

只读目录：`C:\CodexTmp\lcg-quality-artifacts-20260808-final`

- `matrix-api30-single-read-blob-release-ascii-fixed-20260809`：旧“最佳 A”；按锁定 aggregate 口径重算 warmed app+renderer `532,700 KB` → idle+GC `614,805 KB`，`+15.41%`。它不是 clean G2 最终 binary。
- `matrix-api30-direct-base64-release-ascii-fixed-20260809`、`matrix-api30-decoded-image-cache-release-ascii-fixed-20260809`、`matrix-api30-image-retirement-flush-release-ascii-fixed-20260809`：RED/被否决试验，未带入本实现。
- Object/Blob URL、toDataURL 输出、decoded-image cache、1×1 retirement 等均不得恢复。

### G2 superseded clean candidates

| Candidate | 状态 | 说明 |
|---|---|---|
| `ae45074` | RED / superseded | Image source 释放晚于旧最佳 A；API30 20× aggregate warmed `546,992 KB` → idle+GC `804,540 KB`，`+47.08%`。 |
| `bf905a0` | PASS but superseded | 修正 Image 释放时序后，API30 20/20 成功；但随后 API26 证明 `globalThis` 不兼容，因此该 binary 不再有效。 |
| `553556b` | FINAL FAIL | API26 兼容恢复；最终 API30 fresh probe 1/1 spec/measure timeout，按规则停止。 |

`bf905a0` 的 20× 诊断时间序列仅用于说明收敛历史，不用于最终 verdict：

| Stage | App PSS KB | Renderer PSS KB | Aggregate PSS KB |
|---|---:|---:|---:|
| warmed | 238,603 | 345,892 | 584,495 |
| after 5（采样距标记 -5,797 ms） | 242,894 | 710,468 | 953,362 |
| after 10 | 242,284 | 544,353 | 786,637 |
| after 20 | 240,052 | 469,894 | 709,946 |
| idle + GC | 229,097 | 469,894 | 698,991 |

该 superseded run 的 warmed→idle+GC 为 `+114,496 KB / +19.59%`；中途 aggregate peak `1,185,365 KB` 后回落，无 OOM/code 5/partial。由于最终 renderer binary 已变更，这组数据不能替代 `553556b` 的 20×；最终 binary 没有 20× 数据。

## Evidence paths

- G2 证据根目录：`C:\CodexTmp\lcg-quality-g2-recovery-018a-20260809`
- 最终 API26：`api26-final2-smoke-553556b-*`、`api26-final2-restoration-553556b-*`、`api26-final2-atf-553556b-*`
- 最终 API30 阻断：`api30-final2-probe-553556b-instrumentation.log`、`api30-final2-probe-553556b-logcat.txt`、`api30-final2-probe-553556b-memory.csv`
- superseded 20×：`api30-correction1-20x-bf905a0-stage-memory.csv` 及同前缀 logcat/instrumentation/memory 文件
- 最终构建：`C:\CodexTmp\lcg-g2-recovery-build-f9d39b0\app\build\outputs`
- JVM：`C:\CodexTmp\lcg-g2-recovery-build-f9d39b0\app\build\test-results`
- lint：`C:\CodexTmp\lcg-g2-recovery-build-f9d39b0\app\build\reports\lint-results-productionRelease.html`
