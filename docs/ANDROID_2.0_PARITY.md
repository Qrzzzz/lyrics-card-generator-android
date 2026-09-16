# Android 2.0 对齐记录

桌面来源：`Qrzzzz/lyrics-card-generator` main，2026-09-16 fetch 后锁定
`4f2b411c1d9903727ab2680cd727247f751fd6ad`（v6.3.5）。
Android 起点：`55c6177177df5324b20b9b04d52f3c4263c4155a`（1.1.7 / 10107）。
此表保留实施前的差异盘点；完成状态与实测证据见下方。保留六步原生 UI；步骤 1–2 只适配文档。

| 步骤/选项 | 桌面基线、默认值、范围及联动 | Android 起点及差异 |
| --- | --- | --- |
| 数据 | LyricDocumentV2：document/block/unit 稳定 ID、revision、逐单元双语、独立轨道空行 formatting；separator 不消耗另一轨歌词 | 原译文纯字符串按行索引配对；需贯通 undo/redo、Room、桥接、Renderer；旧项目明确不兼容、不迁移、不删除 |
| 3 竖版 | 默认 custom 1040×1080，自动宽/高均开启；宽 720–1440，手动高 720–3200，自动高 640–6400；1:1=1080²、4:5=1080×1350、9:16=1080×1920；固定比例关闭自动尺寸 | 无自动宽，自动高范围不足 |
| 3 横版 | 自由比例；独立 autoLyricsWidth=true、lyricsWidth=880（520–1280）；autoHeight=true、requestedHeight=1080（720–3600）；测量后共享不可变 plan，不缩字塞入画布 | 固定比例/总画布宽高与经验字号缩放，缺 DOM 计划 |
| 3 内容 | 默认显示封面、歌曲信息、专辑；横版保留封面与信息；封面缩放固定 1；纯音乐固定竖版正方形、关闭译文和自动尺寸 | 可关闭歌曲信息、缩放封面 1–2；需统一规则 |
| 4 字体 | 思源黑体/宋体预设；自定义 CJK/Latin 混排、导入字体及字重/样式；字体就绪再测量 | 四个旧枚举，fontFamily 在 Renderer 被覆盖；缺导入和混排 |
| 4 排版 | 字号 36–72，默认 60；行距 1.5–2.1/步长 .05，默认 1.8；左/居中；译文 .6–.9，默认 .75；两行标题默认关；分隔符 dot/line，默认 dot | 行距 1.1–1.75/默认 1.4，额外右对齐；缺语义分隔符 |
| 5 文字色 | 默认白色 #FFFFFF，自定义颜色；标准化旧 auto/preset 到白色 | 旧自动色/多预设 |
| 5 背景 | 当前界面使用空间流光；palette/gradient 为持久化兼容字段，不切换两套生产渲染；完整 ExtractedPalette/analysis、长宽比适配、内容文字阴影 | 仅 dominant/secondary/accent，静态 blobs/ribbons |
| 5 网格 | 默认关，sparse/medium/dense，默认 medium | 有密度与独立透明度，需复用桌面网格算法 |
| 5 标识/页脚 | 平台标识、分享署名、生成署名独立开关，默认关；共享文本默认空；平台 unknown/qq/netease/apple/spotify | 已有独立字段，需核对展示与布局、页脚占位 |
| 6 输出 | PNG/WebP/JPG；1/1.4/2 倍；等待字体/图片和测量稳定，预览与导出同快照 | PNG、1/2 倍；扩展格式须贯通 MIME/扩展名/字节校验/原生保存分享 |
| 版本 | 新版本使用 2.0、2.1；versionCode 严格递增；标签 v2.0；历史三段版本记录仍可核验 | 1.1.7/10107；发布输入与比较器需接受两段号 |

## 提交及证据

按数据 → 布局字体 → 视觉 → 导出分批本地提交；版本与发布文档随最后一批同步。
每批运行适用核心回归；最终执行 Renderer 检查、JVM、lint、可安装 APK 构建和同样例效果对照。
不增加全设备/零警告/100% 覆盖率门槛。安装包构建不等于真机安装或生产签名。
沿用 focused-manual-v1；本任务未授权公开 Release，不伪造签名、设备或发布证据。

## 实施结果

- 数据：V2 文档与文本投影互校，编辑、历史快照、Room JSON、Native 消息、Renderer 使用同一文档；旧 schema 缺文档时拒绝打开，并保留原记录。
- 布局字体：复用桌面自动宽度、横版计划和竖版几何；切回竖版恢复其尺寸设置。字体就绪后测量，导入字体保存在私有目录，通过哈希文件名提供给本地 WebView。支持中西文字体族、字重、斜体；移除界面中无效的封面缩放。
- 视觉：完整封面取色、空间色场、比例种子、默认六色调色板、网格、Explicit、分隔符及独立署名；可读性使用当前桌面生产实现的文字阴影，未复活已停用遮罩。原生平台标识保留现有符号风格。
- 输出：实际编码 PNG / WebP / JPG，原生检查容器、尺寸与 MIME，保存扩展名及分享类型随格式变化。1.4 倍尺寸按桌面向下取整；兼容内部旧整型质量偏好用 14 表示 1.4，桥接仍发送真实倍率。
- 版本：2.0 / 20000，标签约定 v2.0；发布脚本、工作流和设备证据 schema 接受新两段号，历史证据与生产证书锚点不变。没有创建签名候选或 Release 授权记录。

## 同样例核对

自编歌词「晚风把城市写成一封信」，标题「在时间的风里」，歌手「Lyrics Card」，专辑「Android 2.0」，思源黑体 60、行距 1.8、默认六色、无署名/网格、自动尺寸。真实桌面界面录入，与 Android Renderer 的相同输入比较：竖版均为 **1040×640**，横版均为 **1227×697**，目视检查背景色场、标题/封面占位、歌词位置与比例。截图使用各自界面缩放，未宣称逐像素相同。

`scripts/browser-v2-smoke.js` 另以三行原创双语内容实际执行横竖两种布局 × 三种格式，逐个重新解码输出字节并核对尺寸、MIME 与 1/1.4/2 倍。浏览器验证不替代 Android 真机 WebView 验收。桌面 Next 开发模式的生产 CSP 与开发 loader 冲突，仅在本地参考浏览器响应中去掉 CSP；桌面源码未修改，此参考不作为安全验证证据。

保留差异：Android 的原生六步组织、4:5/9:16 快捷比例、平台符号和保存分享入口继续保留；桌面网络字体来源不能离线照搬，Android 通过本地字体导入实现自定义字体。

## 最终本地验收（2026-09-16）

产品代码：`a0935a1`，分支 `codex/android-2.0`。后续验收记录提交只更新本文。

| 结果 | 命令 / 证据 | 范围 |
| --- | --- | --- |
| PASS | `npm --prefix renderer run typecheck`、`test`、`build` | 87 项 Renderer 回归、schema 生成一致性、生产打包 |
| PASS | `scripts/gradle-via-ascii-worktree.ps1 -GradleArguments :app:testProductionDebugUnitTest` | HEAD 独立 ASCII worktree，真实 JVM 回归；最终轮 gradleExitCode=0、cleanup=complete |
| PASS | `gradlew :app:lintProductionDebug :app:assembleProductionDebug` | 最终生产 flavor/debug 变体 lint 和可安装 APK；未添加更严 CI 门槛 |
| PASS | `node --test scripts/test-ci-tools.mjs scripts/test-publish-release.mjs` | 28 项流程合同回归 |
| PASS | `test-production-release-contract.ps1`、`test-device-gate-evidence.ps1`、`test-frozen-source-contract.ps1`、`test-dependency-security-contract.ps1` | 版本两段号、历史版本兼容、来源与拒绝路径；测试 fixture 不是真实设备证据 |
| PASS | Playwright CLI + `browser-v2-smoke.js` | 横竖版各 PNG 1×、WebP 1.4×、JPG 2×，实际重新解码；预览/编码尺寸一致 |
| PASS | `aapt dump badging`、`apksigner verify --verbose` | `com.qrzzzz.lyricscard.debug` / `2.0-debug` / 20000，API 26+，debug APK v2 签名有效 |
| NOT RUN | 真机安装、Android System WebView 跨版本、字体文件选择器与系统分享实机操作 | 浏览器和 JVM 证据不替代这些操作 |
| NOT RUN | main 远端 CI、生产签名 APK/AAB、attestation、标签与公开 Release | 本次为本地实现与构建，没有提交远端发布 |

本地产物 `build/v2-evidence/lyrics-card-generator-2.0-debug.apk`，64,156,267 字节；SHA-256：
`59f771e567afac494bdda20fd8fcf7e821a96fc3b5f2bc9b634abc9d930c8d53`。
日志、SHA256SUMS 与对照截图一并保存在 `build/v2-evidence/`（生成文件，不进入源码提交）。

工具问题单列：早期 ASCII lint 构建已成功，但 Windows 对 lint 缓存 JAR 的清理失败；最终 JVM 单独运行与清理通过。两个早期临时目录仍保留，额外清理被自动审批以 `blocked by policy` 拒绝，不改变产品 PASS 结论，也不记作已清理。
