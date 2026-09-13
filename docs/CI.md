# CI 与发布检查分工

## 验收按用途分层

| 用途 | 保留的验收 | 不应被当作本次完成证据的内容 |
| --- | --- | --- |
| 普通合并 | 本次涉及的产品正确性、依赖差异与流程合同；保护分支要求的检查结果 | 其他 SHA 的绿灯、缺失或被取消的应执行检查 |
| 常规发布 | `focused-manual-v1`：主干同 SHA 全量 CI、可信签名候选、来源/证书/哈希/attestation、实际安装哈希与真机六项 | PR 的轻量检查、自动填写的人工结果、旧候选的验收 |
| 按需专项 | API 30 probe、跨 API 矩阵、耐久、低内存、TalkBack/大字体；旧 Capture/Final 仅在安排专项时使用 | 未运行的专项，或以专项缺设备阻止无关补丁合并 |
| 历史记录 | 绑定原 SHA/run 的旧失败、阻塞、签名 metadata 与已发布验收 | 将旧 FAIL/NOT RUN 改写成 PASS 或作为每版必须重跑的清单 |

上述表说明证据用途；当前下方日常工作流仍使用整套门禁，PR 范围调度另以独立 CI 补丁实现。普通修改不追加零历史告警、全平台、完整耐久、100% 覆盖率或全仓零警告要求。

每次交付使用 PASS、FAIL、BLOCKED、NOT RUN、NOT APPLICABLE，并注明 commit、命令或 Actions run。区分产品缺陷、检查脚本缺陷、外部服务失败、既有基线和缺设备/权限。任务交付、可合并、可发布分别判断。

合同保护行为与权限，不保护 YAML 字段顺序、步骤显示名称或 `npm.cmd` 的特定写法。依赖合同使用锁定的 YAML parser；合法本地 Action 与只读权限下的诊断上传可通过，外部 Action 仍须完整 SHA pin。生产合同从首步 inline script 执行身份校验夹具；改名可通过，跳过首步、错误绑定、未授权来源与 checkout 提前仍拒绝。

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

先在 `renderer` 执行 `npm ci --no-audit --no-fund`，以安装合同使用的锁定 YAML parser。依赖合同入口同时运行合法改写/越权负例；生产合同保留恶意候选替换自身校验器、错误 SHA、错误主干祖先等行为测试。旧 frozen-source/device 合同保护按需专项，在改动这些工具时仍须执行。

### 候选 test APK 的保留范围

当前签名 job 仍构建、验证、attest 并单独上传生产 test APK。调用者包括 Capture、Final、device evidence validator 及其测试夹具；普通 `Publish Verified Candidate` 消费的是五个公开候选附件与人工验收记录，不消费旧 verdict。1.1.2 的合同整理不为减少文件而重做这条签名/专项链，也不把 test APK 构建误报为 instrumentation 已运行。
