# Production Release Checklist

本清单是正式候选的执行门，不是当前 PASS 声明。任何失败都必须 STOP、记录 exact command/error，并在修复后重跑相关 regression；不得关闭 lint/R8/resource shrinking、删除 tests、降低 SDK、放宽 Renderer security 或复用旧 binary 证据。

## 1. Candidate identity

- [ ] 从干净 worktree 开始，记录完整 40 位 candidate commit SHA；
- [ ] 确认 candidate 包含所有计划工作流，包括最新 H release-engineering commit；
- [ ] 确认 `applicationId = com.qrzzzz.lyricscard`；
- [ ] 确认输入的 `versionName` 与源码一致，`versionCode` 高于所有已发布版本；
- [ ] 确认 `minSdk = 26`、`targetSdk = 36`、`compileSdk = 36.1`；
- [ ] `git diff --check` 通过，且没有 keystore、password、base64 secret、APK/AAB 或 build output 被追踪。

版本、package、候选 SHA 或 release notes 任一不一致时立即 STOP，不生成或发布另一个版本。

### 1.1 Protected production source boundary

- [ ] 仅从 `refs/heads/main` dispatch；输入 candidate、`github.sha`、`github.workflow_sha` 与刚 fetch 的 `origin/main` tip 必须是同一个完整 SHA；
- [ ] 对该 SHA 存在 `Android Quality Gate` 的 `push` / `main` / `completed` / `success` run；PR run、其他分支、其他仓库或其他 SHA 的绿灯均无效；
- [ ] `v<version>` remote tag 与同 tag GitHub Release 均不存在；在 environment 批准后、生成 provenance 前再次检查，任一冲突都 STOP；
- [ ] `scripts/test-production-release-contract.ps1` 通过，其中负例必须继续拒绝未合并/过期 main、错误 SHA/版本、重复 tag/Release 与缺少 exact gate；
- [ ] 无 Secrets 的 `authorize-candidate` job 先通过，之后才允许受 `production-signing` environment 保护的签名 job 启动。

仓库内 source policy 采用“dispatch 时的精确 `origin/main` tip”，而不是“任意已合并祖先”。详细合同见 `docs/RELEASE_PROVENANCE.md`。

## 2. Renderer gate

```powershell
cd renderer
npm.cmd ci
npm.cmd run audit:security
npm.cmd run check
```

- [ ] lockfile 安装成功；
- [ ] `audit:security` 在本次执行时间点没有 high/critical npm advisory；结果绑定 candidate commit，不扩写为永久或全生态无漏洞；
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

### 4.1 Machine-enforced final device evidence

`Production Release Candidate` 是两阶段流程的第一阶段：它可以构建、签名并 attestation 同一候选 APK/AAB，但 `release-metadata.json` 必须固定写入 `PROVISIONAL`、`deviceGate = NOT RUN`、`finalReady = false`。该 artifact 不是 `FINAL READY`，不得仅因签名、host gate 或 provenance 通过而发布。

设备运行完成后，将 `device-gate-evidence.json`、精确 test APK 和每个 gate 的 instrumentation/manual 与 logcat 日志作为受控 Actions artifact 上传；不要把测试 fixture 或 evidence 模板当作真实证据。随后手动运行独立 `Final Device Gate` workflow，并精确提供 signed candidate run/artifact 与 evidence run/artifact：

- [ ] evidence 满足 `config/device-gate-evidence.schema.json`，且 `testFixture = false`、顶层 `status = READY`；
- [ ] `scripts/validate-device-gate-evidence.ps1` 将 source commit、candidate run、Quality Gate run、正式 APK/AAB、test APK、package/version/certificate 与实际下载 bytes 逐项绑定；
- [ ] API 26/30/33/36 与获授权真机环境各自记录 WebView、system image/build fingerprint、RAM、设备 ID hash 及 host/device APK hash；
- [ ] 16 个必需 gate 全部为单次 `PASS`，包括 API 30 serif measure/spec 1×→2×、20×/内存、recovery/ATF、4 GB 2×、TalkBack、200% font、30 分钟耐久和真机 save/share；
- [ ] 每个 gate 的日志文件存在且 SHA-256 一致；任何缺项、旧 source、错误 artifact/log SHA、`NOT RUN`、`BLOCKED`、`FAIL` 或 attempts≠1 都必须拒绝；
- [ ] 后置 workflow 不读取 production-signing environment/Secrets，不重新签名或重建 candidate，并对候选全部 publishable assets 重跑 GitHub attestation 验证；
- [ ] GitHub API 证明 candidate run 与 evidence run 均来自本仓库、精确 source SHA、`main`、`completed/success`、允许的 workflow path/event；JSON producer run id/attempt/path/event 与 API 完全一致；
- [ ] `.github/workflows/capture-device-gate-evidence.yml` producer 已由主任务在获授权设备阶段实现并成功上传真实 test APK/logs；当前 producer 缺失时保持 fail closed，不以任意 run-id/artifact-name 替代；
- [ ] test APK 的实际 `aapt2` package/version/instrumentation target 与 `apksigner` certificate 通过；当前仅有的 `productionDebugAndroidTest` 不得冒充 target 为正式 `com.qrzzzz.lyricscard` 的 release device evidence；
- [ ] `final-device-gate` environment 已配置 required reviewer；Reviewer 理解 attestation 只覆盖 #10 candidate assets，device evidence/final verdict 的信任来自 producer identity、API/validator 绑定与人工复核，并非已有的 GitHub artifact attestation；
- [ ] `Final Device Gate` job 成功并产出 `status = FINAL READY` 的 verdict artifact；由人工批准确认其 run IDs、artifact names、source commit 与拟发布值完全相同。

当前仓库没有 v1.0.1 的真实 `device-gate-evidence.json`，所以该门应 fail closed；`tests/fixtures/device-gate/pass` 只能用于 validator 正例测试。

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
- [ ] APK 与 AAB 的证书 SHA-256 均等于 `config/production-signing-policy.json` 的审计锚点；不得以本次签名产物的自我声明替代连续性来源；
- [ ] release asset set 不含 Alpha、Debug 或 unsigned artifact。

生产 secret 缺失时，只允许报告 infrastructure complete + unsigned/minified verification；public release 保持 blocked。

## 6. Artifact and metadata gate

- [ ] APK 与 AAB 均来自同一 `productionRelease` candidate commit；
- [ ] APK 内 package/version/minSdk/targetSdk 与第 1 节完全一致；
- [ ] 固定 bundletool 从实际 AAB 提取的 manifest 中，package/version/minSdk/targetSdk 与第 1 节完全一致；
- [ ] AAB 签名通过，且 source/variant 与 APK 相同；
- [ ] R8 `mapping.txt` 与 candidate 一同保存；
- [ ] `release-metadata.json` 记录 source repository/commit/ref/workflow/workflow SHA/run、exact Quality Gate run、package/version/SDK、APK/AAB/mapping digest 与生产证书 SHA-256；
- [ ] `SHA256SUMS` 覆盖 staged assets，并逐项重新计算一致；
- [ ] 同一 `signed-candidate` job 对 `release-assets/*`（APK、AAB、mapping、metadata、`SHA256SUMS`）生成 GitHub build provenance；
- [ ] 下载 Actions artifact 后，对每个拟公开文件执行 `gh attestation verify --repo Qrzzzz/lyrics-card-generator-android --signer-workflow Qrzzzz/lyrics-card-generator-android/.github/workflows/release.yml --source-ref refs/heads/main --source-digest <candidate>`；
- [ ] 最终报告记录 artifact 绝对路径、bytes、SHA-256 与 signed/unsigned 状态。

## 7. Documentation and independent review

- [ ] README、ARCHITECTURE、CHANGELOG、PRIVACY、THIRD_PARTY_NOTICES、`docs/DEPENDENCY_SECURITY.md` 与本清单匹配最终代码；
- [ ] 同一 candidate 的 Dependency Review 已通过，Dependency Graph/Dependabot alerts/security updates 的仓库设置已按 `docs/DEPENDENCY_SECURITY.md` 独立核验，且开放 high/critical 告警为 0 或逐项有 owner/时限；不得把 default-branch snapshot 或 Dependency Review 扩写为每个 PR 的 Gradle resolved delta 已被完整审查；
- [ ] 网络、日志、permission、exported components 与 Manifest/source 一致；
- [ ] `docs/RELEASE_READINESS.md` 和 internal gate evidence 没有 READY 误报；
- [ ] 独立 release/signing/CI Reviewer 阅读 Source of Truth、actual diff 并运行针对性验证；
- [ ] 独立 Final Integration & Release Readiness Reviewer 从完整最新仓库重验 tests、lint、R8、Renderer regression、database、UI、accessibility、adaptive、device、stress、CI、signing 与 docs；
- [ ] Reviewer 的失败清单已修复并完成 relevant regression。

## 8. Publication authorization

`Production Release Candidate` workflow 只上传受限 Actions artifact，不创建 tag 或公开 Release。

- [ ] 用户在所有 Final Gates 后另行明确授权 publication；
- [ ] 同一 candidate 的独立 `Final Device Gate` workflow 为 success，且下载的 verdict 为 `FINAL READY`；缺失、失败或只存在 fixture 时立即 STOP；
- [ ] 授权版本、commit、APK/AAB hash 与 release notes 完全一致；
- [ ] 发布动作只复用已验签、已 attested 的 candidate bytes，不得重新构建；发布后对每个公开 asset 重跑 attestation 与 checksum 验证；
- [ ] 只有在上述条件成立后，才可执行 push/tag/GitHub Release/store publication。

没有单独 publication 授权时必须停止在 candidate artifact，不能自动公开发布。

## 9. GitHub Settings external prerequisites

以下控制不在 Git commit 能力范围内；由仓库管理员在 GitHub Settings 配置并由发布者独立截图/API 核验前，Issue #10 的 P1 不能声称完全关闭：

- [ ] `main` ruleset/branch protection 要求 `Android Quality Gate`，禁止 force push/删除，并限制直接写入；
- [ ] `production-signing` environment 配置 required reviewers，deployment branch/tag policy 仅允许受控 `main` 发布路径；
- [ ] 明确并收紧管理员 bypass（ruleset 与 environment 的 bypass/自审策略均需记录）；
- [ ] Actions policy 保持最小 `GITHUB_TOKEN` 默认权限；PR/普通 CI 不获得 production Secrets、`id-token: write`、`attestations: write` 或 contents write；
- [ ] Dependency Graph、Dependabot 与 Dependency Review 的外部设置继续满足 `docs/DEPENDENCY_SECURITY.md`，不得因 #10 回退 #11。
