# Production Release Checklist

本清单是正式候选的执行门，不是当前 PASS 声明。任何失败都必须 STOP、记录 exact command/error，并在修复后重跑相关 regression；不得关闭 lint/R8/resource shrinking、删除 tests、降低 SDK、放宽 Renderer security 或复用旧 binary 证据。

## 1. Candidate identity

- [ ] 从干净 worktree 开始，记录完整 40 位 candidate commit SHA；
- [ ] 确认 candidate 包含所有计划工作流，包括最新 H release-engineering commit；
- [ ] 确认 `applicationId = com.qrzzzz.lyricscard`；
- [ ] 确认正式版为 `versionName = 1.0.1`、`versionCode = 10003`，高于所有已发布版本；
- [ ] 确认 `minSdk = 26`、`targetSdk = 36`、`compileSdk = 36.1`；
- [ ] `git diff --check` 通过，且没有 keystore、password、base64 secret、APK/AAB 或 build output 被追踪。

版本、package、候选 SHA 或 release notes 任一不一致时立即 STOP，不生成或发布另一个版本。

## 2. Renderer gate

```powershell
cd renderer
npm.cmd ci
npm.cmd run check
```

- [ ] lockfile 安装成功；
- [ ] validator consistency、TypeScript、Vitest 与 Vite production build 全部通过；
- [ ] 需要 pixel evidence 的最终 Reviewer 运行既有 30-case Golden gate；
- [ ] Renderer output、安全/session/latest-wins/cancel/recovery invariants 未改变或已有独立 diff justification。

## 3. Non-device Android gate

```powershell
.\gradlew.bat :app:test `
  :app:lintProductionRelease `
  :app:assembleAlphaDebug `
  :app:assembleProductionDebug `
  :app:assembleProductionRelease `
  :app:bundleProductionRelease `
  :app:dumpProductionReleaseBundleManifest `
  --no-parallel --stacktrace --console=plain
```

- [ ] 全部 JVM tests 通过；
- [ ] `lintProductionRelease` 为 0 Fatal / 0 Error；
- [ ] Alpha Debug 保留门通过；
- [ ] Production Debug 通过；
- [ ] Production Release APK 经过 R8/minification 与 resource shrinking；
- [ ] Production Release AAB 生成；
- [ ] 普通 build 后 `git status --porcelain=v1 --untracked-files=all` 为空，Renderer generated assets 未污染 source tree。

GitHub Actions 的 `Android Quality Gate` 必须在同一 candidate 上通过。Hosted CI 不运行 instrumentation；不得把 instrumentation source 编译或 JVM test 结果写成设备 PASS。

## 4. Final G / authorized device gate

对 H 后同一 Production candidate APK 执行本地 Final Gate，并把 host artifact SHA-256 与设备上实际安装的 APK 绑定：

- [ ] API 26：fresh install、核心流程、Renderer/WebView compatibility、recovery；
- [ ] API 30：先重验 serif 1×→2× probe，确认既有 measure/spec timeout 已解决；
- [ ] API 30：通过前项后执行 20 次连续导出、cancel/retry/temp cleanup 与内存记录；
- [ ] API 33：核心流程、recovery、TalkBack；
- [ ] API 36：完整 connected Production instrumentation、20 次连续导出；
- [ ] 4 GB 环境：标准 2× 导出与峰值/回落证据；
- [ ] 30 分钟连续编辑、rotation、background、process recreation；
- [ ] 200% font scale 不破坏主要任务；
- [ ] 一台获明确授权实体设备完成核心流程与 save/share；
- [ ] 无 ANR、不可恢复 Renderer 错误、orphan temp file 或明显 memory leak。

当前小米设备不得在没有新授权时被触碰。API 30 blocker 未通过前，不得将 API 33/36 的旧 binary 结果套用到最终 candidate。

## 5. Signing gate

本地签名使用未跟踪 `release-signing.properties` 或以下环境变量：

```text
LYRICS_CARD_STORE_FILE
LYRICS_CARD_STORE_PASSWORD
LYRICS_CARD_KEY_ALIAS
LYRICS_CARD_KEY_PASSWORD
```

CI 的 `production-signing` environment 另外保存 `LYRICS_CARD_KEYSTORE_BASE64`，只用于 runner 临时解码。所有真实值都不得写入 workflow、logs、issues、commits 或 release notes。

- [ ] `production-signing` environment 已配置 required reviewers；
- [ ] 四项 Gradle signing 值完整，keystore file 存在；
- [ ] `signingReport` 确认 `productionDebug` 使用 debug signing，只有 `productionRelease` 使用 production signing；
- [ ] release workflow 临时目录位于 `RUNNER_TEMP`，cleanup step 在 `always()` 中执行；
- [ ] `apksigner verify` 通过 signed Production APK；
- [ ] `jarsigner -verify` 通过 signed Production AAB；
- [ ] release asset set 不含 Alpha、Debug 或 unsigned artifact。

生产 secret 缺失时，只允许报告 infrastructure complete + unsigned/minified verification；public release 保持 blocked。

## 6. Artifact and metadata gate

- [ ] APK 与 AAB 均来自同一 `productionRelease` candidate commit；
- [ ] APK 内 package/version/minSdk/targetSdk 与第 1 节完全一致；
- [ ] 固定 bundletool 从实际 AAB 提取的 manifest 中，package/version/minSdk/targetSdk 与第 1 节完全一致；
- [ ] AAB 签名通过，且 source/variant 与 APK 相同；
- [ ] R8 `mapping.txt` 与 candidate 一同保存；
- [ ] `release-metadata.json` 记录 commit/package/version/SDK/signing；
- [ ] `SHA256SUMS` 覆盖 staged assets，并逐项重新计算一致；
- [ ] 最终报告记录 artifact 绝对路径、bytes、SHA-256 与 signed/unsigned 状态。

## 7. Documentation and independent review

- [ ] README、ARCHITECTURE、CHANGELOG、PRIVACY、THIRD_PARTY_NOTICES 与本清单匹配最终代码；
- [ ] 网络、日志、permission、exported components 与 Manifest/source 一致；
- [ ] `docs/RELEASE_READINESS.md` 和 internal gate evidence 没有 READY 误报；
- [ ] 独立 release/signing/CI Reviewer 阅读 Source of Truth、actual diff 并运行针对性验证；
- [ ] 独立 Final Integration & Release Readiness Reviewer 从完整最新仓库重验 tests、lint、R8、Renderer regression、database、UI、accessibility、adaptive、device、stress、CI、signing 与 docs；
- [ ] Reviewer 的失败清单已修复并完成 relevant regression。

## 8. Publication authorization

`Production Release Candidate` workflow 只上传受限 Actions artifact，不创建 tag 或公开 Release。

- [ ] 用户在所有 Final Gates 后另行明确授权 publication；
- [ ] 授权版本、commit、APK/AAB hash 与 release notes 完全一致；
- [ ] 只有在上述条件成立后，才可执行 push/tag/GitHub Release/store publication。

没有单独 publication 授权时必须停止在 candidate artifact，不能自动公开发布。
