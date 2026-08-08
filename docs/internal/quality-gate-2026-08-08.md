# Lyrics Card Generator Android — Release Quality Gate (2026-08-08)

- 执行时区：Asia/Shanghai
- Gate 2 基线：`b41a2e00606f5b970b553301c7911cae646c3c66`
- 候选分支：`codex/quality-device-hardening`
- 本报告候选 HEAD：`b65d2a150fe075872fdb7156fc4224f961857421`
- 自动化结论：**PASS**
- 整体结论：**BLOCKED**（只因 Gate 2 唯一一次 Xiaomi 安装被设备安全策略阻断；本任务按约束未重试）
- 独立 Reviewer：**PENDING**

## A. Scope / candidate

输入硬门槛按 detached-worktree 规则通过：

```powershell
git rev-parse HEAD
git rev-parse refs/heads/codex/integration-gate2
git branch --contains HEAD
git status --porcelain=v1
```

`HEAD` 与 `refs/heads/codex/integration-gate2` 均精确为 `b41a2e00606f5b970b553301c7911cae646c3c66`，输入工作树 clean；随后从该提交创建 `codex/quality-device-hardening`。未执行 push、PR、tag、signing、GitHub Release、商店发布或公开文档发布。

本 Gate 不增加产品功能。以下约束保持：六步编辑、persistent BottomSheet、仅 1x/2x、二态 dark、500 ms autosave、50 条历史；Compose → RenderSpec → trusted local WebView → React/CSS → PNG；Room v2、v1→v2、rendererVersion 兼容 ID、UDF/AppContainer、FINALIZING 原子提交与 single Room update。

## B. Changes / fixes

| Commit | 范围 | 可执行证据 |
|---|---|---|
| `55ad8bc` | 新增 30-case deterministic pixel Golden、锁定 Playwright/pixelmatch/SSIM 依赖；auto-height 量测收敛 | Windows Chromium 151；30/30 PNG exact |
| `ade2585` | 新增 Accessibility Test Framework、AVD matrix smoke、20 次导出与 30 分钟压力测试 | API 36 connected 21/21 |
| `bf041e2` | Home/Editor/Export/Settings pane title | semantics/ATF/connected PASS |
| `2547366` | Base64 chunk 落盘改为有界 IO queue，移出主线程 | StrictMode 0；20/20；JVM recovery/assembly PASS |
| `80acb27` | API 26 WebView 使用 origin-bound `WebMessagePort` fallback；Blob 读取兼容 FileReader | API 26 WebView 69 smoke PASS；transport/security tests PASS |
| `775dc2d` | matrix smoke 同步、阶段日志与清理稳定化 | API 26/30/33/36 PASS |
| `7509055` | 异步导航提交回到 Main dispatcher | connected navigation PASS |
| `b83ed6f` | ATF 导航流程稳定化 | API 33 TalkBack+ATF PASS |
| `f865ead` | rename focus、搜索结果/设置/导出 action semantics 最小修复 | 13 项 UI 定向套件及 21 项全套 PASS |
| `9ddcbe1` | 真实 Activity 横竖屏、设备 density、font 1.3/2.0、IME、Compose 主时钟轮询 | 20 秒 UI 门限下 PASS |
| `d69f31c` | 移除 preview/card 强制高密度合成层提示，修复 WebView renderer memory crash | 20/20 2x PASS；30/30 Golden exact |
| `f167202` | 压力测试按 `measure()` 实际 auto-height 验证 2x PNG 尺寸 | 2080×5544 压力输出通过 PNG 校验 |
| `b65d2a1` | Golden source/fixture 文本 fingerprint 统一 CRLF→LF，字体二进制仍按原字节 | Unicode/ASCII 两 worktree 指纹一致，双方 30/30 exact |

补充验收约束已落实：Compose 轮询通过 `compose.mainClock.advanceTimeBy(...)` 推进；`AvdMatrixSmokeTest.UI_TIMEOUT_MS` 与 `AccessibilityFrameworkTest.UI_TIMEOUT_MS` 均为原始 `20_000L`，没有保留无证据的 60 秒 UI 门限。renderer 专用导出等待仍按独立 renderer timeout 管理。

## C. Invariants / Golden methodology

### Protected trees and renderer invariants

```powershell
git diff --name-only b41a2e00606f5b970b553301c7911cae646c3c66..HEAD -- app/src/main/java/com/qrzzzz/lyricscard/data app/schemas
git diff --name-only 55ad8bc..HEAD -- renderer/golden/reference
```

两条命令均为空：Room/data/schema protected tree 未改；初始 Golden baseline 建立后 30 张 reference PNG 未被更新。产品 renderer 的变化仅为：auto-height 固定点量测、API 26 安全消息通道兼容、移除造成高密度 compositor crash 的两个强制 layer hint。未改用 Compose Canvas；trusted origin/no-network、generation/latest-wins、export mutex、Base64 chunks、PNG validation、timeout/cancel/recovery/shared WebView 语义均由 renderer/JVM/connected tests 覆盖。

### Golden reference environment

| 项 | 固定值 |
|---|---|
| Browser | Playwright Chromium `151.0.7922.34` |
| Platform | `win32` |
| Viewport | `1280×960` |
| Device scale | `1` |
| Locale / timezone | `en-US` / `UTC` |
| Color / motion | light / reduced motion |
| Chromium args | `--disable-lcd-text`, `--font-render-hinting=none`, `--force-color-profile=srgb` |
| Fonts | repo 内 Source Han Sans/Serif Heavy 与 license |
| Pixel threshold | pixelmatch threshold `0.02`；mismatch ratio ≤ `0.0005` |
| Similarity threshold | SSIM ≥ `0.9995` |
| Source fingerprint | `4f76f1c39f6018b035e267ad6b583c28e9c5120f3e264dd10075c7bdd36c66ca` |
| Fixture fingerprint | `0476356f485da8a91773a37888b35c3ff7d50c63d1169b2e612bae9e0c8a2a8c` |

Golden 比较读取真实 expected PNG，渲染 actual PNG 后同时执行尺寸、pixelmatch mismatch ratio、SSIM 与 byte-exact 检查；不是 HTML/JSON/hash 替代品。当前 30 张 PNG 共 62.43 MiB。最终实际结果全部为 `exact=true`、mismatch ratio `0.00000000`、SSIM `1.00000000`。

30 个可审计 fixture：

1. `portrait-square-cjk-left-auto`
2. `portrait-four-five-bilingual-center-preset`
3. `portrait-nine-sixteen-latin-right-custom-no-cover`
4. `landscape-sixteen-nine-long-serif`
5. `landscape-twenty-one-nine-punctuation-emoji`
6. `landscape-three-two-minimal-branding`
7. `portrait-custom-fixed`
8. `portrait-custom-auto-height`
9. `instrumental-with-cover`
10. `instrumental-without-cover`
11. `translation-off-traditional-cjk`
12. `translation-on-spanish`
13. `lyrics-without-song-info`
14. `platform-qq-badge`
15. `platform-netease-badge`
16. `platform-apple-badge`
17. `platform-spotify-badge`
18. `shared-by-without-platform`
19. `watermark-without-footer-badges`
20. `grid-sparse-palette`
21. `grid-medium-gradient`
22. `grid-dense-gradient`
23. `serif-center-cream`
24. `sans-right-soft-blue`
25. `custom-text-color`
26. `long-lyrics-twenty-lines`
27. `empty-lyrics-localized-fallback`
28. `explicit-two-line-title`
29. `square-two-x-export`
30. `landscape-sixteen-nine-two-x-export`

覆盖 1:1、4:5、9:16、16:9、21:9、3:2、custom、auto-height、lyrics/instrumental、translation、cover/no-cover、长 metadata、CJK/Latin/punctuation/emoji、left/center/right、sans/serif、auto/preset/custom text color、palette/gradient/grid、platform/shared-by/watermark 与 1x/2x。

## D. Commands and exact automated counts

### Renderer final HEAD (`C:\CodexTmp\lcg-quality-staging-20260808\renderer`)

```powershell
npm ci --no-fund
npm audit
npm run check
npm run golden:test
```

- `npm ci`: 117 packages installed；118 audited；0 vulnerabilities。
- `npm audit`: 0 vulnerabilities。
- renderer: 8/8 suites、60/60 tests，typecheck/validator/build PASS。
- Golden: 30/30 cases exact PASS；没有运行 `golden:update`。

### Android JVM / APK / release / lint final HEAD

```powershell
.\gradlew.bat `
  :app:testAlphaDebugUnitTest `
  :app:testProductionDebugUnitTest `
  :app:assembleAlphaDebugAndroidTest `
  :app:assembleProductionDebugAndroidTest `
  :app:assembleProductionRelease `
  :app:lintProductionRelease `
  --rerun-tasks --console=plain
```

- Gradle：`BUILD SUCCESSFUL in 1m 18s`；194/194 tasks executed。
- AlphaDebug JVM：31 suites / 141 tests / 0 failures / 0 errors / 0 skipped。
- ProductionDebug JVM：31 suites / 141 tests / 0 failures / 0 errors / 0 skipped。
- 两套 androidTest APK：PASS。
- `assembleProductionRelease`：PASS；候选为 unsigned APK（本任务禁止 signing）。
- R8/resource shrinking：`minifyProductionReleaseWithR8`、`convertShrunkResourcesToBinaryProductionRelease`、`optimizeProductionReleaseResources` 均执行。
- lint：0 errors；33 warnings。warning 分类为 GradleDependency 11、NewerVersionAvailable 6、UnusedResources 5、UseKtx 3、RequiresFeature 2、ExifInterface 2、AndroidGradlePluginVersion 1、ObsoleteSdkInt 1、DiscouragedApi 1、DataExtractionRules 1；未为非阻断升级提示扩大范围。

### API 36 full connected gate

```powershell
$env:ANDROID_SERIAL='emulator-5556'
.\gradlew.bat :app:connectedProductionDebugAndroidTest '-Pandroid.injected.device.serial=emulator-5556'
```

- 21/21 tests；0 failures/errors/skips。
- XML suite time：2076.178 s；Gradle wall time：34m49s。
- 20 次 2x：149.557 s。
- 30 分钟编辑：1807.380 s testcase；内部 wall-clock assertion 为 1,800,021 ms。
- 关键 UI 定向回归另有 13/13 PASS（90.955 s）。
- API 36 外部进程恢复：后台后 `am kill` 使旧 PID 消失；cold relaunch 恢复到第 4 步，返回第 1 步后 synthetic search draft 精确保留。

21 项覆盖：renderer lifecycle/shared WebView、Home 无 WebView、ATF、font 1/2、landscape+IME、Activity recreation、matrix 1x/2x、Home actions/dialog、Editor 六步/search/invalid inputs/slider/retry、Export 1x/2x/busy/failure/success/cancel/save/share actions、Settings 两态/quality/safe-area/cache、20-export、30-minute endurance。

## E. AVD / API / WebView / Accessibility matrix

SDK 只读盘点结果：Emulator `36.6.11.0` stable；WHPX `10.0.26200` installed and usable；C 盘执行前 23.32 GiB 可用。安装/保留的稳定 system image：

- API 26：`system-images;android-26;google_apis;x86_64` rev 16
- API 30：`system-images;android-30;google_apis_playstore;x86_64` rev 10
- API 33：`system-images;android-33;google_apis_playstore;x86_64` rev 9
- API 36：`system-images;android-36;google_apis_playstore;x86_64` rev 7

所有测试 AVD 位于 `C:\CodexTmp\lcg-quality-avds-20260808`，config 默认 2G，但每次启动显式 `-memory 4096`；设备运行时 `dumpsys meminfo` 均确认约 4 GiB。实际启动模板：

```powershell
Start-Process -WindowStyle Hidden `
  -FilePath "$env:ANDROID_SDK_ROOT\emulator\emulator.exe" `
  -ArgumentList @('-avd', $avd, '-port', '5556', '-memory', '4096',
    '-no-window', '-no-audio', '-no-snapshot-load', '-no-snapshot-save',
    '-wipe-data', '-no-boot-anim', '-gpu', 'host', '-netfast')
```

始终一台启动、测试、关机后才进入下一台。

| API | Image / ABI | Runtime RAM | Android | WebView | Boot / install | Final smoke | Accessibility |
|---|---|---:|---|---|---|---|---|
| 26 | Google APIs rev16 / x86_64 | 4,042,036K | 8.0.0 / 26 | 69.0.3497.100 | PASS / 两 APK PASS | 1/1，35.269 s | TalkBack/Scanner absent；Compose semantics+ATF alternative |
| 30 | Play Store rev10 / x86_64 | 4,023,600K | 11 / 30 | 83.0.4103.106 | PASS / 两 APK PASS | 1/1，22.191 s | TalkBack/Scanner absent；Compose semantics+ATF alternative |
| 33 | Play Store rev9 / x86_64 | 4,007,916K | 13 / 33 | 109.0.5414.123 | PASS / 两 APK PASS | TalkBack on：1/1，25.005 s | `com.google.android.marvin.talkback` 14.2.0.618048417；ATF 1/1，3.818 s；Scanner absent |
| 36 | Play Store rev7 / x86_64 | 4,013,816K | 16 / 36 | 133.0.6943.137 | PASS / 两 APK PASS | matrix 1/1，21.228 s；full 21/21 | TalkBack 16.0.0.738667889 present；Scanner absent；ATF included in full suite |

API 33 TalkBack 执行命令与核心步骤：

```powershell
adb -s emulator-5556 shell settings put secure enabled_accessibility_services `
  com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb -s emulator-5556 shell settings put secure accessibility_enabled 1
adb -s emulator-5556 shell am instrument -w -r `
  -e class com.qrzzzz.lyricscard.ui.AvdMatrixSmokeTest `
  com.qrzzzz.lyricscard.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am instrument -w -r `
  -e class com.qrzzzz.lyricscard.ui.AccessibilityFrameworkTest `
  com.qrzzzz.lyricscard.debug.test/androidx.test.runner.AndroidJUnitRunner
```

服务启用状态下走 Home ready → 新建 → 六步 direct/back/next → Step 3 renderer ready → 1x → 2x → Export route → Home；随后 ATF 检查 Home → Editor → Export → Settings。Accessibility Scanner 的标准包 `com.google.android.apps.accessibility.auditor` 不存在，未伪报 PASS；使用 Espresso Accessibility Test Framework 替代。

Compose/instrumentation 还真实覆盖 compact/medium/expanded、wide landscape、Activity portrait/landscape、fontScale 1.0/1.3/2.0、实际 IME inset 与 48dp touch target；检查 heading、paneTitle、selected/disabled、Role.Switch/Button、合并 semantics、decorative null、dialog action/focus、live-region error/polite 状态。

进程恢复没有只用 Activity recreation 代替：API 36 上先进入 Editor 第 4 步并写入 synthetic draft，HOME 后执行 `adb -s emulator-5556 shell am kill com.qrzzzz.lyricscard.debug`；PID 2631 消失，重新启动报告 `LaunchState: COLD` 且恢复原 task。新 PID 4014 恢复第 4 步，切回第 1 步后 draft `process-restore-42` 精确存在。

## F. 20-export and 30-minute performance

### 20 consecutive real 2x exports

fixture 为 16 行双语、auto-height；先 `measure()` 后验证真实 2080×5544 PNG。warmup 不计入 20 次。

| Stage | PSS KB | RSS KB | Java PSS KB | Native PSS KB | Graphics PSS KB | Export files | `.part`/`.tmp` |
|---|---:|---:|---:|---:|---:|---:|---:|
| before | 247,361 | 396,092 | 13,752 | 20,552 | 0 | 0 | 0 |
| 5 | 249,749 | 392,384 | 21,540 | 19,340 | 0 | 5 | 0 |
| 10 | 249,457 | 392,000 | 21,548 | 19,368 | 0 | 10 | 0 |
| 20 | 233,500 | 366,776 | 17,148 | 19,520 | 0 | 20 | 0 |
| idle + GC | 228,564 | 362,052 | 13,752 | 19,520 | 0 | 20 | 0 |

- Success 20/20；renderer crash/error 0；OOM 0；partial/temp 0。
- 峰值 PSS 相对 warmed baseline +0.97%；idle+GC 相对 baseline -7.60%。
- StrictMode app-main-thread disk read/write violations：0。

### Real 30-minute high-frequency editing

| Stage | PSS KB | RSS KB | Java PSS KB | Native PSS KB | Graphics PSS KB |
|---|---:|---:|---:|---:|---:|
| start | 246,469 | 383,052 | 13,860 | 19,252 | 0 |
| 5m | 256,953 | 395,292 | 19,172 | 21,452 | 0 |
| 10m | 260,022 | 398,660 | 18,476 | 21,468 | 0 |
| 15m | 259,260 | 397,976 | 18,264 | 21,512 | 0 |
| 20m | 257,090 | 395,748 | 18,688 | 21,532 | 0 |
| 25m | 259,924 | 398,732 | 18,204 | 21,556 | 0 |
| idle + GC | 239,078 | 376,888 | 13,916 | 21,620 | 0 |

- Duration：1,800,021 ms；operations：14,801；Activity recreation/orientation：9；background/resume：19。
- 峰值 PSS +5.50%；10→15→20→25 分钟为平台而非单调增长；idle+GC 相对 start -3.00%。
- ANR/FATAL/OOM/WebView renderer crash：0；renderer recovery error：0；partial file：0。
- 最终 `flushAutosave()` 成功；Room 读取的 name/spec 与 Editor 最终 state 精确一致。
- StrictMode app-main-thread disk read/write violations：0。

主线程边界静态复核：thumbnail、export preview decode、image import/resize、cache traversal/delete、SAF copy、diagnostics 与 renderer chunk/finalization 均在 `Dispatchers.IO`/IO queue；production Room 未开启 main-thread queries。20-export 与 30-minute 的 StrictMode listener 对 app stack 违规计数均为 0。

### Frame / shared WebView evidence

- connected `RendererUiLifecycleTest`：Home 不创建 WebView；进入 Step 3 后创建且跨六步与 Export 重用同一实例。
- API 36 冷进程、首次 WebView、preview 与 1x/2x export 混合窗口：68 frames，30 janky（44.12%），P50 17 ms、P90 150 ms、P99 1000 ms。该窗口记录首次初始化/导出尖峰，保留为观察项，不冒充稳态。
- renderer 预热后，真实六步横向导航、视觉操作与上下滚动：88 frames，5 janky（5.68%）；P50/P90 17 ms、P95 18 ms、P99 19 ms；missed vsync 0；slow bitmap upload 0。
- 结合 30 分钟无 ANR/crash、稳定 PSS 与 shared-WebView identity，没有发现持续性整屏重组或 AndroidView 重建；未做无证据微优化。

### Save / share / cancel / retry contracts

- Renderer 在 AVD 上真实导出 1x/2x；PNG MIME、dimensions 与完整性均验证。
- API 36 实际解析到 Print、Drive、Maps、Messages、Photos、Bluetooth、Gmail 等 image/png targets，并打开 `android/com.android.internal.app.ResolverActivity`；只验证 chooser，没有向第三方实际发送。
- API 36 实际打开 `com.google.android.documentsui/com.android.documentsui.picker.PickActivity` 的 `ACTION_CREATE_DOCUMENT`，随后取消。
- App 的 stream URI、ClipData、read grant、ActivityNotFound error、SAF cancel/failure/retry、busy/finalizing/cancel/no-duplicate 由双 variant JVM 与 connected UI state tests 覆盖。

## G. Remaining external blockers

Gate 2 对物理 Xiaomi API 36 的唯一安装尝试返回 `INSTALL_FAILED_USER_RESTRICTED`，因此设备侧为 0 tests。按明确约束，本 Gate 未再次安装、未修改 USB 安装/未知来源/MIUI 优化/安全策略，也未向该序列号执行测试命令。

结论分离：

- **Quality automation：PASS**（AVD API 26/30/33/36、Golden、Accessibility/ATF、JVM、connected、stress、release/lint/R8 全门通过）。
- **Physical-device verification：external BLOCKED**（Xiaomi 安全策略）。
- **Overall：BLOCKED**。

## H. Commit / workspace / artifacts

当前分支提交（从 Gate 2 base 起）：

```text
55ad8bc test(renderer): add deterministic pixel golden gate
ade2585 test(android): add accessibility matrix and release stress gates
bf041e2 fix(a11y): announce screen pane titles
2547366 fix(renderer): move export chunk writes off main thread
80acb27 fix(renderer): support API 26 WebView transport
775dc2d test(android): stabilize API matrix smoke
7509055 fix(navigation): commit async routes on main
b83ed6f test(a11y): harden ATF navigation flow
f865ead fix(a11y): stabilize focus and action semantics
9ddcbe1 test(android): exercise real windows and synchronized UI
d69f31c fix(renderer): avoid forced high-density preview layers
f167202 test(android): measure auto-height stress exports
b65d2a1 test(renderer): normalize golden source fingerprints
```

脱敏证据与产物：`C:\CodexTmp\lcg-quality-artifacts-20260808-final`（94 files；APK、R8/resource mappings、lint/JVM/connected XML、npm/Golden/Gradle/AVD/performance/process-restoration logs）。

| Artifact | Size | SHA-256 |
|---|---:|---|
| `app-alpha-debug-androidTest.apk` | 4,723,928 | `BAB4FAA23774C4AC1AA69F5C3C02CB7E1F63B09C06707592F7465B45A23361F0` |
| `app-production-debug-androidTest.apk` | 4,723,948 | `9D6450ECD49FB108AB6783DA1B2662C799394105EE8F6F74DEA128F0CAB4972D` |
| `app-production-release-unsigned.apk` | 45,157,201 | `63842A3F4F8D942AFEF83B2A4C3E5804C0CBA201055E3D28CE29F31E92B4CD01` |
| `mapping.txt` | 35,679,776 | `4BD16B1108EDFB6BD7004404F404DEC5FD2E0AD4F7BD97A7969EBF815D46CB49` |
| `resources.txt` | 179,190 | `B4162CC11298249CED08C67DF7F8353A462656F2982D92ABC32AE0550304FEF0` |

稳定 SDK system images 可保留。独立 Reviewer 完成后删除 `C:\CodexTmp\lcg-quality-avds-20260808` 与 `C:\CodexTmp\lcg-quality-staging-20260808`；证据目录保留。最终要求：主 worktree clean；不 push/PR/tag/release。
