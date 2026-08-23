# Production source and build provenance

本文定义 `Production Release Candidate` 的仓库内可审计合同。它约束候选来源、Quality Gate、版本唯一性、生产证书连续性、release metadata 与 GitHub artifact attestation；它不创建 tag、GitHub Release 或商店发布，也不替代 GitHub Settings 中的保护规则。

## Source policy

正式候选必须同时满足：

1. workflow 由 `workflow_dispatch` 从 `refs/heads/main` 启动；
2. 输入的完整 candidate SHA 等于 dispatch 的 `github.sha`、workflow 定义的 `github.workflow_sha`，也等于 fresh fetch 后的 `origin/main` tip；
3. `app/build.gradle.kts` 的 `versionName` 等于输入版本，版本格式为 `x.y.z`；
4. 远端不存在 `v<version>` tag，GitHub 也不存在同 tag Release；
5. `.github/workflows/ci.yml` 对同一仓库、同一 SHA 存在 `Android Quality Gate` 的成功 run，且该 run 必须是 `push` event、`main` branch、`completed` status、`success` conclusion。

这里有意采用“精确 main tip”而不是“main 上任意祖先”。这样能阻止把已经被后续提交取代的 SHA、未合并 SHA、PR 绿灯或另一个 SHA 的绿灯送入生产签名环境。若 main 在审批或构建期间前进，或 tag/Release 在期间出现，recheck 会停止旧候选；需要从新的 main tip 重新取得 exact Quality Gate 证据。

`authorize-candidate` 只有 `actions: read` 与 `contents: read`，不绑定 environment，也不引用 Secrets。它通过后，`signed-candidate` 才进入现有 `production-signing` environment；签名 job 在 environment 审批后和 provenance 前再次运行同一合同。

纯逻辑位于 `scripts/ProductionReleasePolicy.psm1`，GitHub/git 取证入口为 `scripts/verify-production-candidate.ps1`。`scripts/test-production-release-contract.ps1` 提供主机可运行的正/负向合同，并由正常 `Android Quality Gate` 执行。

## Certificate continuity

`config/production-signing-policy.json` 是审计锚点，不含密钥或密码。当前锚点来自公开 `v1.0.0`：

- source commit `661b3e6e2af47a6898266adfdd2347dbc0e4a5fc`；
- APK SHA-256 `f9be5e8b4850c84b5b66fc30d18655f89eae7dd87c5eed9cb4e0dd62ab8f91dc`；
- production certificate SHA-256 `b02a1b6c391545c2bdbcc33bda3a708741e259a4efea60bdc4958522f5bc82f5`。

`lastVerifiedRelease` 另记录上一正式版 `v1.0.1` 的 source commit `5a21bb4666f2ad3e6b57709e1efb5ad9cb711481`、公开 APK SHA-256 `41e056f19913c39727da7575b06f0d48d2554bdad57585611ff9a2924e990ae0` 及从该公开 APK 复算的相同证书 SHA-256。这样既保留首次公开披露的 trust anchor，也直接绑定上一正式版本。

签名 job 从构建后的 APK 验签输出读取证书 digest，并从 AAB 的 PEM certificate 独立计算 SHA-256；两者都必须等于锚点和上一正式版记录。候选 metadata 只记录验证结果，不能反过来作为连续性证明。更换生产证书必须是单独、经审核的 trust-anchor 变更，并说明迁移/轮换依据，不能在一次候选运行中自动接受新证书。

## Metadata and attestations

`release-metadata.json` schema v2 记录：

- source repository、commit、ref、workflow name/ref/SHA、run id/attempt；
- exact Quality Gate run id 与 URL；
- package、versionName/versionCode、minSdk/targetSdk；
- APK、AAB 与可选 mapping 的文件名、bytes、SHA-256；
- production certificate SHA-256 与 trust-anchor release/APK digest。

随后生成的 `SHA256SUMS` 覆盖 APK、AAB、可选 mapping 与 metadata。`signed-candidate` job 在删除临时 keystore 后，以只授予该 job 的 `id-token: write`、`attestations: write` 对 `release-assets/*` 生成 GitHub build provenance；因此 APK、AAB、mapping、metadata 与 checksums 都是 attestation subjects。普通 CI/PR 没有这些写权限或 production Secrets。

该 signed candidate 仍不是最终设备结论。metadata 明确写入 `PROVISIONAL / device NOT RUN / finalReady=false`；独立 `Final Device Gate` workflow 在后置阶段以 `actions: read`、`attestations: read`、`contents: read` 下载同一 candidate artifact 和受控 device-evidence artifact，重新验证每个 candidate subject 的 provenance，再将实际 APK/AAB/test APK、日志和完整设备矩阵交给 `scripts/validate-device-gate-evidence.ps1`。后置 job 不进入 `production-signing`、不读取 Secrets、不重建或重签产物。真实 evidence 缺失或任一 gate 非 PASS 时不会产生 `FINAL READY` verdict。

`Final Device Gate` 还通过 GitHub API 将 candidate run 锁定到本仓库、精确 source SHA、`main`、成功的 `workflow_dispatch` 与 `.github/workflows/release.yml`，并将 evidence run 同样锁定到未来受控 producer `.github/workflows/capture-device-gate-evidence.yml`；JSON 内的 producer run id/attempt/path/event 必须与 API 结果一致。当前仓库尚未实现或运行该获授权设备 capture producer，本提交也没有设备权限，因此这里仅完成 consumer/validator 与 fail-closed 合同；producer、真实 test APK/logs 上传和 `final-device-gate` environment reviewer 配置仍待主任务设备阶段回填。

`gh attestation verify` 的证明对象仅是 #10 signed candidate 中的 APK/AAB/mapping/metadata/checksums，不把 device logs、test APK 或最终 verdict 自动升级为 GitHub build provenance。最终 verdict 的信任来自：允许的 producer workflow identity、GitHub API 的 same-repo/same-SHA/success 绑定、证据文件与真实 bytes/log hashes 的 validator、`final-device-gate` environment 人工 reviewer，以及独立发布者对 run/artifact IDs 的复核。若需要让 device evidence 或 verdict 也具备 cryptographic attestation，应作为后续独立权限设计；不得在本门中复用 signing secrets 或声称已有该属性。

候选下载后，对每个拟发布文件执行（`<candidate>` 必须替换为冻结的完整 SHA）：

```powershell
gh attestation verify <asset-path> `
  --repo Qrzzzz/lyrics-card-generator-android `
  --signer-workflow Qrzzzz/lyrics-card-generator-android/.github/workflows/release.yml `
  --source-ref refs/heads/main `
  --source-digest <candidate>
```

验证还必须把 `release-metadata.json` 中的 source、workflow、Quality Gate、certificate 与 artifact digests 和授权记录逐项比对。公开发布只能上传这些已验证 bytes；发布后从 Release 重新下载并对每个 asset 重跑 `SHA256SUMS` 与 `gh attestation verify`。历史 v1.0.0/v1.0.1 没有 GitHub attestation，不能追溯补造为本 workflow 的真实 provenance。

## External prerequisites and residual boundary

仓库文件不能配置或证明 GitHub Settings。正式使用前，管理员必须在 Settings 中独立完成并留存证据：

- 为 `main` 设置 ruleset/branch protection，要求 `Android Quality Gate`，限制直接写入并禁止 force push/删除；
- 为 `production-signing` environment 设置 required reviewers 与仅允许受控 main 发布路径的 deployment policy；
- 明确 ruleset/environment 的管理员 bypass 与自审策略，按风险收紧；
- 保持最小 Actions 默认权限，并确认 fork/PR/普通 CI 无法读取 production Secrets 或获得 OIDC/attestation 写权限；
- 保持 #11 的 Dependency Graph、Dependabot、Dependency Review 和相关仓库设置。

在这些外部控制尚未配置和独立核验时，本仓库实现只能称为“仓库内控制已就绪”，不能声称 Issue #10 的 P1 已仅凭本提交完全修复。真实 GitHub attestation 也只能由合并后的 workflow 在 GitHub-hosted runner、获 environment 审批并使用真实候选构建时产生；本地测试不能生成或替代该证据。
