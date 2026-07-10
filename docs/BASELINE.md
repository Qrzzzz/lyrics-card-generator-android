# Renderer 基准

| 字段 | 值 |
| --- | --- |
| Windows 仓库 | `Qrzzzz/lyrics-card-generator` |
| Commit | `b894db9e121848122a16ddcdaaab1283ffab1e27` |
| Package version | `4.3.8` |
| Renderer | `android-alpha-renderer-1` |
| RenderSpec Schema | `1` |
| Protocol | `1` |
| Golden dataset | `android-alpha-fixtures-v1` |

基准来自公开 `main`。本机相邻 Windows 工作树在实施时有独立未提交改动，因此所有核对均以 Git 提交对象为准，没有重构或覆盖该工作树。

字体文件从该提交的 `public/fonts` 固定，并在 `renderer/renderer-manifest.json` 记录 SHA-256。静态构建每次把 Schema、manifest、字体和散列后的 JS/CSS 一起写入 APK assets。

首批 fixtures 覆盖双语竖版、自动高度、方形居中、超宽横版和纯音乐。正式 Alpha 发布前应扩展到至少 30 个样例，并从同一基准生成 Windows 参考 PNG。

