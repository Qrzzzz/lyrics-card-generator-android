# Release Engineering Gate — 2026-08-09

## Verdict and candidate identity

**H PROVISIONAL PASS — PROVISIONAL / NOT FINAL READY**

本记录只覆盖工作流 H 的 CI、release-candidate、signing infrastructure、正式文档与非设备本地门。它不覆盖或替代工作流 G、设备矩阵、获授权真机、生产签名 secret 或独立 Final Reviewer。

- Worktree：`C:\Users\qrzzz\.codex\worktrees\cfb5\歌词分享图片生成器安卓版`
- 起点：`ef1761bea70f727ea1c7dd20d56a0eb41575aaac`
- 起点 ref：`refs/heads/codex/quality-device-hardening-recovery`
- 产品父提交：`553556b538347298c4a6558fdb7d17a45c28e85d`
- H ref：`refs/heads/codex/release-engineering-docs`
- Immutable H commit：由本文件进入最终提交后，在 H 交付中记录；commit 不能在其自身内容中自引用。

开工前 worktree clean，HEAD 与要求起点完全一致，目标分支此前不存在。整个 H 未运行 adb、emulator、AVD 或实体设备命令，未 push、PR、merge、tag、创建 GitHub Release 或执行商店发布；当前小米设备未触碰。

## Implemented scope

### CI

- `Android Quality Gate` 使用最小 `contents: read` 权限、不可变 Action SHA、JDK 17、Node.js 24、SDK Platform 36.1 与 Build Tools 36.1.0。
- Renderer 执行 `npm ci` 与 `npm run check`。
- Android 执行全部 JVM variants、`lintProductionRelease`、Alpha Debug 保留门、Production Debug、minified/resource-shrunk Production Release APK 与 Production Release AAB。
- npm cache 只使用 lockfile 对应的下载缓存；Renderer generated assets 位于 Gradle build directory。构建后显式拒绝 source-tree drift。
- Hosted CI 不伪造 instrumentation PASS；instrumentation 仍保留为本地 Final Gate。

### Release candidate and signing

- `Production Release Candidate` 仅允许 `workflow_dispatch`，要求完整 40 位 commit SHA、匹配的 production version 和受保护的 `production-signing` environment。
- Workflow 只有 `contents: read`，只构建 signed `productionRelease` APK/AAB，验证签名和 APK/AAB manifest metadata，生成 `SHA256SUMS`、`release-metadata.json` 与可选 R8 mapping，然后上传受限 Actions artifact。
- Workflow 没有 tag、公开 Release、仓库写入或商店发布步骤。
- Gradle 从四个统一 `LYRICS_CARD_*` 环境变量读取签名值，也可从未跟踪的 `release-signing.properties` 读取。全部缺失时允许 unsigned/minified verification；部分配置会立即失败；keystore 不存在时失败且不打印值。
- CI keystore 只在 `RUNNER_TEMP` 解码，后续步骤只从 step environment 接收 secret；`always()` cleanup 校验目录仍位于 `RUNNER_TEMP` 后才递归删除。
- 固定 `bundletool 1.18.1` 从实际 AAB 提取 manifest，不以 source metadata 代替 bundle metadata。

### Formal documentation

- README 已改为稳定产品入口，不再把已实现产品写成 Alpha roadmap。
- ARCHITECTURE 与当前 WebView lazy/lifetime、navigation/ViewModel ownership、asset ledger、export state、安全边界、build/variant/signing pipeline 对齐。
- 历史 `docs/ALPHA_STATUS.md` 已由 `docs/RELEASE_READINESS.md` 替代。
- 新增 1.0.0 用户向 CHANGELOG、release checklist、privacy、third-party notices；第三方内容只使用仓库 font license、Gradle catalog/POM 与 npm lock metadata 可确认的事实。

## Configuration and signing evidence

Workflow 内嵌 PowerShell 使用本机 parser 检查：CI 4 个 run blocks、release 8 个 run blocks，均 0 parse error；10 个 `uses:` 全部为 40 位 immutable SHA。静态审计确认 CI 必需任务齐全，release 无 `contents: write`、Alpha/Debug build、`git tag` 或 `gh release`。

使用 Android 标准 debug keystore 的公开默认凭据作为非生产测试夹具执行 `:app:signingReport`，只验证 variant 选择，不生成可分发生产产物：

```text
productionDebug=debug
productionRelease=productionRelease
```

因此 production flavor 上的 production signing config 不会覆盖 `productionDebug` 的 debug signing。只提供 `LYRICS_CARD_STORE_FILE`、其余三项显式为空时，Gradle configuration 按预期以 `Incomplete production signing configuration. Missing: ...` 失败。使用同一非生产夹具执行 Production Release APK/AAB build 后，`apksigner verify` 与 `jarsigner -verify` 均通过；随后执行 clean，并在无任何 signing 变量的环境中重建下述最终 unsigned artifacts。没有读取或打印真实 secret。

最终无 secret 状态再次执行 `:app:signingReport`，结果为 `productionDebug=debug`、`productionRelease=null`，与最终 unsigned artifacts 一致。

## Commands and results

Renderer 从真实 worktree 路径执行：

```powershell
cd renderer
npm.cmd ci
npm.cmd run check
```

结果：117 packages installed/audited，0 vulnerabilities；validator consistency、TypeScript、8 suites / 78 tests、Vite production build 全部通过。最终 Vite build 转换 54 modules，输出 `index-BLYr1FuW.js`。

Android 使用 JDK 17、`ANDROID_HOME=C:\Users\qrzzz\AppData\Local\Android\Sdk`，并将同一 worktree 以 `subst R:` 暴露为纯 ASCII path 后执行：

```powershell
.\gradlew.bat :app:clean `
  :app:test `
  :app:lintProductionRelease `
  :app:assembleAlphaDebug `
  :app:assembleProductionDebug `
  :app:assembleProductionRelease `
  :app:bundleProductionRelease `
  :app:dumpProductionReleaseBundleManifest `
  --rerun-tasks --no-parallel --no-daemon --stacktrace --console=plain
```

结果：`BUILD SUCCESSFUL in 3m 22s`，213/213 actionable tasks executed。

| JVM variant | Suites | Tests | Failure / Error / Skipped |
| --- | ---: | ---: | ---: |
| Alpha Debug | 31 | 143 | 0 / 0 / 0 |
| Alpha Release | 31 | 143 | 0 / 0 / 0 |
| Production Debug | 31 | 143 | 0 / 0 / 0 |
| Production Release | 31 | 143 | 0 / 0 / 0 |
| Total | 124 | 572 | 0 / 0 / 0 |

`lintProductionRelease`：0 Fatal、0 Error、35 Warning。R8 task `minifyProductionReleaseWithR8`、resource shrink/optimize tasks 与 35,736,554-byte `mapping.txt` 均存在；未关闭 lint、R8 或 resource shrinking。

Windows 路径说明：原始中文路径中的 Gradle/JUnit runner 会把 31 个 test suites 各自报告为 `ClassNotFoundException`；普通 junction 仍被 canonicalize 回中文路径。`subst R:` 指向同一 worktree 后 targeted `testAlphaReleaseUnitTest` 与完整门均通过。反向地，Vitest 从 `R:` 运行会把 module ID canonicalize 回真实路径并导致 8 suites load failure，所以 Renderer 最终门从真实 worktree 路径执行并通过。没有复制源码、删除测试或降低门。

## Final local artifacts

下列是冻结 H working-tree 内容的最终无 secret clean build。SHA-256 已逐项重新计算复核。

| Artifact | Bytes | SHA-256 | Signing |
| --- | ---: | --- | --- |
| `C:\Users\qrzzz\.codex\worktrees\cfb5\歌词分享图片生成器安卓版\app\build\outputs\apk\alpha\debug\app-alpha-debug.apk` | 62,747,399 | `d1c4d108a6f766d252a8ac1f26a0a3632cc1bdb8e167c8da8863ea5fd1ba431a` | debug |
| `C:\Users\qrzzz\.codex\worktrees\cfb5\歌词分享图片生成器安卓版\app\build\outputs\apk\production\debug\app-production-debug.apk` | 62,747,363 | `af5f1d8d8c4b32c9a4fab49e4d80db1a317c21dc0d81b1f26ef17dd82f996783` | debug |
| `C:\Users\qrzzz\.codex\worktrees\cfb5\歌词分享图片生成器安卓版\app\build\outputs\apk\production\release\app-production-release-unsigned.apk` | 45,157,865 | `e35e7235b07b7aca55e9592c44ec41e09a9faa4f566d2c9e53b9be883bd58c96` | **unsigned** |
| `C:\Users\qrzzz\.codex\worktrees\cfb5\歌词分享图片生成器安卓版\app\build\outputs\bundle\productionRelease\app-production-release.aab` | 39,916,315 | `038ba3179be6887ea2c8e7b0fa77026546f2fc696d540e05685503fe8cec1ac2` | **unsigned** (`jarsigner`: `jar is unsigned`) |
| `C:\Users\qrzzz\.codex\worktrees\cfb5\歌词分享图片生成器安卓版\app\build\outputs\mapping\productionRelease\mapping.txt` | 35,736,554 | `3b4fdcde20b4478252d9b7bafb8ba82f5c3b731d283872867f449d590976d816` | n/a |

`apkanalyzer` 读取 Production APK、固定 bundletool 读取实际 AAB，二者均为：

```text
package = com.qrzzzz.lyricscard
versionCode = 10000
versionName = 1.0.0
minSdk = 26
targetSdk = 36
```

`apksigner verify` 对最终 APK 返回 `DOES NOT VERIFY`；`jarsigner` 对最终 AAB返回 `jar is unsigned`。这正是缺少 production secret 时的预期状态，不得将其描述为公开可分发或 signed candidate。

Renderer `dist` 与 Gradle generated renderer 的 8 个文件逐项 SHA-256 相同。最终 build 后 `git status -- renderer app/src` 为空，完整 status 只有已知 H workflow/build/docs 改动；没有 generated renderer、APK/AAB、build output、keystore 或 local signing properties 被追踪。最终提交后的 source cleanliness 由 H 交付再次确认。

## Unresolved external and Final Gates

工作流 G 的终局 Reviewer 已判定设备门 NOT PASS；此前 fresh API 30 上 serif 1×→2× probe 在导出前的 measure/spec 请求触发既有 8 秒 timeout。H 没有修改 Renderer output，也不能覆盖该失败。

H 修改了 build/variant/release infrastructure，因此 G 与独立 Final Reviewer 必须从 H 最终 immutable commit 重新构建 signed Production candidate，并把新 APK/AAB SHA-256 与设备证据绑定；H 之前 binary 或本文件的 unsigned hash 不能替代 signed device candidate hash。

仍未决：

- 最终候选 API 26、30、33、36；
- API 30 blocker 修复与 20 次连续导出；
- 4 GB 环境 2× 导出与内存证据；
- 30 分钟连续编辑、background/process recovery 与 temp cleanup；
- API 33 TalkBack；
- 200% font scale；
- 一台获明确授权实体设备；
- production signing keystore/password/alias secrets；
- 独立 Final Integration & Release Readiness Reviewer PASS；
- 用户对 push/tag/GitHub Release/store publication 的另行明确授权。

在上述门全部完成前，本候选保持 **PROVISIONAL / NOT FINAL READY**。
