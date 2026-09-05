# Production Release Checklist

自 2026-09-05 起，1.1.1 及后续版本采用 `focused-manual-v1`。这是维护者明确批准的发布范围调整。原 Capture/Final 完整矩阵保留为按需专项诊断，历史失败不被改写为通过。当前发布入口为 `Publish Verified Candidate`。

## 1. 冻结签名候选

- [ ] 使用干净主干提交，版本与源码一致；已有候选继续复用，流程改动不重建相同 APK。
- [ ] 同一 source SHA 的 Android Quality Gate 和 Dependency Security 已成功；引用已有 run，不在本地重复全套。
- [ ] Production Release Candidate 从受保护 main dispatch，生产签名环境保留审批、来源限制和连续证书锚点。
- [ ] 同一签名运行生成 APK/AAB、mapping、metadata、SHA256SUMS，并通过 GitHub attestation。生产密钥不进入源码、日志或发布附件。

## 2. 真机核心验收

- [ ] 获授权手机上实际安装的版本和 base.apk SHA-256 与候选匹配。
- [ ] 维护者实际完成：打开、编辑歌词、预览、导出 PNG、保存后打开图片、打开分享面板。无需向联系人发送。
- [ ] 在 `docs/releases/v<version>-acceptance.json` 记录 source/run/attempt/artifact、设备型号/API/版本/安装哈希、确认人/时间、六项结果和未覆盖范围。
- [ ] 失败或未执行保持 FAIL/NOT RUN，不能填写 PASS；修复影响 APK 时重新冻结候选并验证受影响操作。

API 30 专项复核、多版本完整矩阵、20 次导出、4 GB 内存、30 分钟耐久、TalkBack 与大字体测试按实际风险单独安排。它们不再是每版发布前提；未执行时在 Release 明确列出，不能声称专项通过。

## 3. 审阅与发布

- [ ] 提交验收记录、版本说明和流程改动；通过主干保护要求的 PR 检查。
- [ ] 从 main dispatch `Publish Verified Candidate`，仅输入版本。已有发布授权持续有效；通过 GitHub 正常环境审批继续，不反复要求聊天确认。
- [ ] 托管 runner 从记录指定的原 candidate run 下载五个附件，检查 source/run/attempt、同 SHA CI、主干祖先关系、生产证书、安装哈希、metadata/checksums 和所有附件的 attestation。
- [ ] 在冻结 source 创建 annotated tag；先创建草稿、上传原始五附件，核对 GitHub asset digest 后公开。冲突标签或不同附件必须停止，不能强制覆盖。
- [ ] 原始 metadata 保留构建时 PROVISIONAL/NOT RUN/finalReady=false；当前验收由 main 上的人工记录和 publication receipt 表达，不伪造旧 FINAL READY。
- [ ] 公开核验 Release、tag/source、五个附件名称和 GitHub SHA-256 digest。正常成功后不重复下载数百 MB 文件。
- [ ] 按实际验收范围绑定 PR 和处理 Issue；尚未复核的 API 30 专项保留跟踪，不以本机 API 36 人工操作替代。

## 4. 清理与失败恢复

保留生产密钥、源码、已发布附件和必要失败记录；只清理明确归属本任务的可再生目录，不清空用户其他回收站内容。移入回收站不等于释放空间。

发布失败时从同一候选继续：已有正确 annotated tag 可复用，草稿只补传缺失且同哈希的附件，已发布版本仅核验后返回。不重签、不覆盖同版本不同字节。执行策略拒绝后停止该动作，不能把重新构建、反复确认或更换执行工具当作恢复办法。
