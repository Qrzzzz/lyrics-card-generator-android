# Production source and build provenance

本文定义 `Production Release Candidate` 的仓库内可审计合同。它约束候选来源、Quality Gate、版本唯一性、生产证书连续性、release metadata 与 GitHub artifact attestation；它不创建 tag、GitHub Release 或商店发布，也不替代 GitHub Settings 中的保护规则。

## Source policy

正式候选必须同时满足：

1. workflow 由 `workflow_dispatch` 从 `refs/heads/main` 启动；
2. 输入的完整 candidate SHA 等于 dispatch 的 `github.sha` 与 workflow 定义的 `github.workflow_sha`；fresh main 必须仍包含这个冻结提交；
3. `app/build.gradle.kts` 的 `versionName` 等于输入版本，版本格式为 `x.y.z`；
4. 远端不存在 `v<version>` tag，GitHub 也不存在同 tag Release；
5. `.github/workflows/ci.yml` 对同一仓库、同一 SHA 存在 `Android Quality Gate` 的成功 run，且该 run 必须是 `push` event、`main` branch、`completed` status、`success` conclusion。

候选在 main dispatch 时冻结，environment 审批针对这个明确的 SHA。审批或构建期间 main 前进不会使候选失效；API/git 仍检查候选属于主干历史。被移出主干历史的提交、未合并提交、错配 workflow/trigger、其他 SHA 或 PR 的绿灯均被拒绝。tag/Release 冲突仍立即停止。修改候选内容需要新的 source 和对应 CI，不能借用旧候选证据。

`authorize-candidate` 只有 `actions: read` 与 `contents: read`，不绑定 environment，也不引用 Secrets。两个 job 都先执行可信 workflow 定义内的内联检查，在任何 checkout 或仓库脚本执行前确认 candidate、workflow SHA、trigger SHA 相同且候选仍在实时主干历史中；签名 job 同时核对上游授权输出。checkout 始终使用 `github.workflow_sha`，不使用 candidate 输入或上游输出选择校验代码，防止未合并候选替换自己的验证器后自行授权。授权通过后，`signed-candidate` 才进入现有 `production-signing` environment；签名 job 在 environment 审批后和 provenance 前再次运行完整合同，复核版本唯一性和 exact-SHA Quality Gate。

纯逻辑位于 `scripts/ProductionReleasePolicy.psm1`，GitHub/git 取证入口为 `scripts/verify-production-candidate.ps1`。`scripts/test-production-release-contract.ps1` 提供主机可运行的正/负向合同，并由正常 `Android Quality Gate` 执行；它还直接运行从 workflow 提取的两个内联入口，通过本地替换验证器的候选夹具检验拒绝时未执行候选代码，以及主干正常前进可继续、候选移出主干或授权输出污染会停止流程。此夹具不调用 GitHub、不申请生产凭据，也不触发真实签名。

## Certificate continuity

`config/production-signing-policy.json` 是审计锚点，不含密钥或密码。当前锚点来自公开 `v1.0.0`：

- source commit `661b3e6e2af47a6898266adfdd2347dbc0e4a5fc`；
- APK SHA-256 `f9be5e8b4850c84b5b66fc30d18655f89eae7dd87c5eed9cb4e0dd62ab8f91dc`；
- production certificate SHA-256 `b02a1b6c391545c2bdbcc33bda3a708741e259a4efea60bdc4958522f5bc82f5`。

`lastVerifiedRelease` 另记录上一正式版 `v1.1.0` 的 source commit `bdd93a074ba577e9f2de230515052eb69c7e13d2`、公开 APK SHA-256 `5033a45104f7642147ad5ffd79ab3573fd6dc59cdecb63cf5f463f7978672902` 及从该公开 APK 复算的相同证书 SHA-256。这样既保留首次公开披露的 trust anchor，也直接绑定上一正式版本。

签名 job 从构建后的 APK 验签输出读取证书 digest，并从 AAB 的 PEM certificate 独立计算 SHA-256；两者都必须等于锚点和上一正式版记录。候选 metadata 只记录验证结果，不能反过来作为连续性证明。更换生产证书必须是单独、经审核的 trust-anchor 变更，并说明迁移/轮换依据，不能在一次候选运行中自动接受新证书。

## Metadata and attestations

`release-metadata.json` schema v2 记录：

- source repository、commit、ref、workflow name/ref/SHA、run id/attempt；
- exact Quality Gate run id 与 URL；
- package、versionName/versionCode、minSdk/targetSdk；
- APK、AAB 与可选 mapping 的文件名、bytes、SHA-256；
- production certificate SHA-256 与 trust-anchor release/APK digest。

随后生成的 `SHA256SUMS` 覆盖 APK、AAB、可选 mapping 与 metadata。`signed-candidate` job 在删除临时 keystore 后，以只授予该 job 的 `id-token: write`、`attestations: write` 对 `release-assets/*` 生成 GitHub build provenance；因此 APK、AAB、mapping、metadata 与 checksums 都是 attestation subjects。普通 CI/PR 没有这些写权限或 production Secrets。

同一签名 job 在原有主机测试与 APK/AAB 构建之后，用独立 Gradle 调用构建 `:app:assembleProductionReleaseAndroidTest`，并核对测试 APK 使用同一生产证书。文件 `app-production-release-androidTest.apk` 单独 attestation 后上传为 `production-device-test-<version>-<sha12>` artifact，供设备门从同一 candidate run 下载；它不进入 `release-assets`、公开 Release 或公开 checksums。设备 runner 无需生产签名材料来构建测试 APK。

该 signed candidate 仍不是最终设备结论。metadata 明确写入 `PROVISIONAL / device NOT RUN / finalReady=false`；独立 `Final Device Gate` workflow 在后置阶段以 `actions: read`、`attestations: read`、`contents: read` 下载同一 candidate artifact 和受控 device-evidence artifact，重新验证每个 candidate subject 的 provenance，再将实际 APK/AAB/test APK、日志和完整设备矩阵交给 `scripts/validate-device-gate-evidence.ps1`。后置 job 不进入 `production-signing`、不读取 Secrets、不重建或重签产物。真实 evidence 缺失或任一 gate 非 PASS 时不会产生 `FINAL READY` verdict。

`Final Device Gate` 仍是独立 consumer/validator：它通过 GitHub API 将 candidate run 锁定到本仓库、精确 source SHA、`main`、成功的 `workflow_dispatch` 与 `.github/workflows/release.yml`，并将 evidence run 锁定到受控 producer `.github/workflows/capture-device-gate-evidence.yml` 的实际 workflow SHA。Capture/Final 均从受保护 main dispatch，验证器 SHA 等于各自 trigger SHA；候选 source 可以更早。Final 用 GitHub compare API 验证 source → evidence workflow SHA → final validator SHA 的主干祖先链；JSON 内的 producer run id/attempt/path/event 必须与 API 结果一致。producer 只允许带 `lcg-device-gate` 标签的 Windows 自托管 Runner，从同一候选 run 下载并验证 production bytes 和带独立来源证明的 production-release test APK，并按 stop-on-first-failure 规则运行 API 26/30/33/36 与获授权实体设备矩阵。真实 producer run、test APK/logs artifact 和 `final-device-gate` environment 审批仍必须在具体发布时逐项核验。

`gh attestation verify` 的证明对象是 #10 signed candidate 中的 APK/AAB/mapping/metadata/checksums，以及同一签名 job 单独构建、证明的正式 test APK，不把 device logs 或最终 verdict 自动升级为 GitHub build provenance。最终 verdict 的信任来自：允许的 producer workflow identity、GitHub API 的 same-repo/same-SHA/success 绑定、证据文件与真实 bytes/log hashes 的 validator、`final-device-gate` environment 人工 reviewer，以及独立发布者对 run/artifact IDs 的复核。若需要让 device evidence 或 verdict 也具备 cryptographic attestation，应作为后续独立权限设计；不得在本门中复用 signing secrets 或声称已有该属性。

候选下载后，对每个拟发布文件执行（`<candidate>` 必须替换为冻结的完整 SHA）：

```powershell
gh attestation verify <asset-path> `
  --repo Qrzzzz/lyrics-card-generator-android `
  --signer-workflow Qrzzzz/lyrics-card-generator-android/.github/workflows/release.yml `
  --source-ref refs/heads/main `
  --source-digest <candidate>
```

验证还必须把 `release-metadata.json` 中的 source、workflow、Quality Gate、certificate 与 artifact digests 和授权记录逐项比对。公开发布只能上传这些已验证 bytes；发布后从 Release 重新下载并对每个 asset 重跑 `SHA256SUMS` 与 `gh attestation verify`。历史 v1.0.0/v1.0.1/v1.1.0 没有 GitHub attestation，不能追溯补造为本 workflow 的真实 provenance。

## External prerequisites and residual boundary

仓库文件不能配置或证明 GitHub Settings。正式使用前，管理员必须在 Settings 中独立完成并留存证据：

- 为 `main` 设置 ruleset/branch protection，要求 `Android Quality Gate`，限制直接写入并禁止 force push/删除；
- 为 `production-signing` environment 设置 required reviewers 与仅允许受控 main 发布路径的 deployment policy；
- 明确 ruleset/environment 的管理员 bypass 与自审策略，按风险收紧；
- 保持最小 Actions 默认权限，并确认 fork/PR/普通 CI 无法读取 production Secrets 或获得 OIDC/attestation 写权限；
- 保持 #11 的 Dependency Graph、Dependabot、Dependency Review 和相关仓库设置。

在这些外部控制尚未配置和独立核验时，本仓库实现只能称为“仓库内控制已就绪”，不能声称 Issue #10 的 P1 已仅凭本提交完全修复。真实 GitHub attestation 也只能由合并后的 workflow 在 GitHub-hosted runner、获 environment 审批并使用真实候选构建时产生；本地测试不能生成或替代该证据。
