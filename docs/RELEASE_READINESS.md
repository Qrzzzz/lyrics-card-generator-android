# Release Readiness

## 当前发布策略：focused-manual-v1

维护者于 2026-09-05 明确批准重做发布流程，保留现有源码、修复与签名候选，采用成功的同 SHA CI、实际安装哈希绑定、真机核心操作确认和 GitHub 托管发布。此前强制依赖 self-hosted runner 的四 API 完整矩阵不再是每版的发布前提。

唯一常规发布入口为 `.github/workflows/publish.yml`（Publish Verified Candidate）。从 main 输入版本后，工作流读取已合并的 `docs/releases/v<version>-acceptance.json`，在保留 `final-device-gate` 环境审批的前提下验证并发布。该历史环境名称继续承担发布审批，不表示旧完整设备门已经通过。

## 必需证据

1. 冻结源码的 Android Quality Gate、Dependency Security 和 Production Release Candidate 成功；既有同 SHA 结果直接引用。
2. 生产证书连续、五个候选附件的 metadata/checksums 和 GitHub attestation 有效；候选 source 仍属于受保护主干历史。
3. 真机实际安装版本与 APK 哈希匹配，维护者确认打开、编辑、预览、PNG 导出、保存后打开和分享面板六项正常。
4. 人工记录明确确认人、时间和未覆盖范围，提交主干并接受发布环境审批。脚本只验证记录完整性与字节绑定，不能代替人工观察，也不把人工声明称为自动化设备证明。
5. 原字节上传、annotated tag 指向冻结源码、公开五附件的 GitHub SHA-256 digest 完全匹配。结果保存为 publication receipt。

签名候选 metadata 是构建时事实，仍可保留 PROVISIONAL / NOT RUN / finalReady=false。发布流程不改写已 attested metadata，不生成表示旧全矩阵通过的 FINAL READY。

## 验收范围和历史

- v1.1.1 使用候选 33877855131 / attempt 1，source `2dacf491985b6e46974e9c11cb3c94745d800f82`。维护者已在 Xiaomi 2210132C / API 36 确认核心操作；详细机器可读记录见 [v1.1.1 acceptance](releases/v1.1.1-acceptance.json)。
- Capture 33881243214 从未执行设备门，因本次流程调整已取消，不能写成成功。
- 旧 source `d7f3b2e` 的 Capture 33872306376 在 API 26 系统保存测试中失败；相关修复见 PR #37，原始记录保留。
- 后续本地 instrumentation 被 MIUI 阻止后台启动；新人工验证不改变该历史运行的失败/阻塞状态。
- API 30 超时复核、跨 API 矩阵、TalkBack、大字体、内存和耐久属于专项范围。尚未实际复核的项目保持未验证，#9 继续跟踪 API 30 专项，不能仅凭 API 36 核心流程关闭为已修复。

旧 Capture/Final 工作流及其严格 validator 保留为可选诊断工具，默认在 Actions 中禁用；确需完整矩阵时单独启用并提供正常受控执行环境。常规发布不注册或启动本机 runner。

## 来源保护与依赖

生产签名仍需要受保护 main、同 SHA Quality Gate、版本唯一性、环境审批及固定证书。发布 job 不读取生产密钥，不构建或重签。main 的 required checks 与环境 reviewer 不因设备范围调整而取消。

构建和测试工具链的既有告警继续由 [#35](https://github.com/Qrzzzz/lyrics-card-generator-android/issues/35) 和[依赖基线](security/dependency-baseline-2026-09-04.md) 跟踪；不宣称所有生态零漏洞。

执行步骤见 [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md)，来源合同见 [RELEASE_PROVENANCE.md](RELEASE_PROVENANCE.md)。
