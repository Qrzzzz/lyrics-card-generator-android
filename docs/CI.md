# CI 与发布检查分工

## 日常检查

`Android Quality Gate` 在 PR、main push 和手动触发时运行。`quality-gate` 保留 Renderer 类型/单测、完整四变体 JVM、生产 lint/R8、Debug 与 Production APK/AAB 构建。Renderer 类型与测试步骤不再单独调用 Vite build，最终 assets 由 Gradle `buildRenderer` 生成。

`unicode-path-jvm-smoke` 保留 required check 名称。PR 先比较完整 base/head SHA；只有 wrapper、Gradle、构建配置、依赖和相关检查脚本等输入改变时才安装工具链并运行真实 JVM smoke。main 和手动运行始终执行。筛选失败会使检查失败，不能冒充通过；无关 PR 明确报告不需要该 smoke。使用 task 级 `--rerun` 保证测试实际执行，前置编译可复用。

## 依赖审计

安装统一使用 `npm ci --no-audit --no-fund`，显式的 `audit:security` 单独执行。审计结果包括开发依赖；high/critical 立即失败。已识别的网络错误与 HTTP 429/500/502/503/504 最多尝试三次，每次请求超时 30 秒、进程上限 75 秒。未知错误、鉴权失败、无效报告和重试耗尽仍非零退出；不会修改 lockfile 或把审计不可用写成安全通过。

Dependabot 将 React、React DOM 及类型定义放在同一组，其余兼容的小版本按生态分组。大版本迁移和安全阈值仍需审查。

## 发布复用

引用冻结 source SHA 的成功 main Quality Gate，无需在本地再跑同一套 Renderer/JVM 全量测试。签名 job 安装锁定依赖、重新审计，然后运行 productionRelease JVM/lint 与生产 APK/AAB/test APK 构建、证书和 provenance 检查；alpha/debug 测试由原 Quality Gate 证明。

签名 source 与 main dispatch/工作流 SHA 相同，审批期间允许 main 正常前进，来源必须仍属于主干历史。常规发布由 Publish Verified Candidate 读取主干中已确认的人工验收记录，验证 source/发布 validator 的祖先链、run/attempt、原产物字节和 attestation，再发布五个原始附件。

生产候选、测试 APK、设备证据与最终 verdict 的 Actions artifact 均保留 90 天。此设置只影响新上传的 artifact，已经过期或按旧设置上传的产物不会自动延期。

产品或测试 APK 改变仍要重新冻结候选。仅修改 CI/验证流程时不需要重新签同一 APK；真机按发布清单确认核心操作，专项设备测试按风险选择，历史失败记录保留。旧 Capture/Final 工作流默认禁用，可在明确安排完整矩阵时另行启用。

## 验证脚本

```powershell
node --test scripts/test-ci-tools.mjs scripts/test-publish-release.mjs
pwsh -NoProfile -File scripts/test-dependency-security-contract.ps1
pwsh -NoProfile -File scripts/test-production-release-contract.ps1
pwsh -NoProfile -File scripts/test-frozen-source-contract.ps1
pwsh -NoProfile -File scripts/test-device-gate-evidence.ps1
```

这些合同验证流程与失败处理，不替代真实签名或设备证据。
