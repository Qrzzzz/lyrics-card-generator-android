# Android Alpha 架构

## 数据流

```text
Compose Screen
  → AppViewModel / StateFlow
  ├─→ NeteaseMusicService (optional HTTPS lookup)
  └─→ RenderSpec v1
       ├─→ Room ProjectRepository
       └─→ RendererController
            → trusted local WebView
            → React/CSS card
            → html-to-image PNG
            → cache file
            → SAF / FileProvider
```

Compose 只负责产品交互。卡片最终像素来自同一份本地静态渲染器，预览阶段只更新 DOM，不反复生成 PNG。

## 跨端合同

`RenderSpec` 是渲染器唯一输入。Kotlin 模型位于 `app/src/main/java/com/qrzzzz/lyricscard/model/RenderSpec.kt`，JSON Schema 位于 `renderer/schema/render-spec-v1.schema.json`。

合同约束：

- `schemaVersion = 1`，枚举使用稳定字符串；
- 不保存 Android URI、文件路径、Compose 状态或面板展开状态；
- 封面使用 UUID 逻辑 ID；
- 默认值在发送前补齐，尺寸和颜色先在 Native 端验证；
- Room 保存完整 `specJson`，冗余的 schema/renderer/cover 字段用于发现损坏数据。

编辑与导出导航路由都携带项目 UUID。若 Android 在后台回收进程，恢复后的路由会按该 ID 从 Room 重建编辑状态；普通离开页面和 `onStop` 则先尝试落盘尚未完成的自动保存。

## WebView 安全边界

唯一入口是：

```text
https://appassets.androidplatform.net/renderer/index.html
```

仅允许 `/renderer/` 与 `/media/`，其余请求返回 403。WebView 禁用文件/内容访问、DOM Storage、混合内容、弹窗、新窗口、下载和外部导航，并启用 `blockNetworkLoads`。应用声明的 `INTERNET` 权限只供 Native 侧 `NeteaseMusicService` 访问固定的网易云 HTTPS API、短链和图片域名，用户输入不会交给 WebView 导航。

Web → Native 使用 `WebViewCompat.addWebMessageListener`，注入对象为 `LyricsCardNative`，允许 Origin 仅为 `https://appassets.androidplatform.net`。Native → Web 调用 `window.LyricsCardRenderer.receive(...)`。所有消息都有 `protocolVersion`、`requestId`、`type` 和 `payload`。

RenderSpec Schema 由 `renderer/scripts/generate-render-spec-validator.mjs` 在类型检查、测试与生产构建前编译为独立校验模块。WebView 不在运行时调用 Ajv 编译器，因此 CSP 无需开放 `unsafe-eval`，也不会因 Schema 动态代码生成而卡在启动阶段。

## 资源与导出

封面由系统 Picker 选取，或由 Native 侧从网易云允许的图片域名下载后，统一校验、缩放并复制到应用私有目录。`ProjectAssetStore` 限制输入大小，并只接受 UUID 路径。渲染器通过 `/media/<assetId>` 读取，不接触真实文件路径。

`RendererController` 由 Activity 级 `AppViewModel` 持有：项目列表显示后延迟预热，编辑与导出页面只重新挂载同一个 WebView。APK 内带内容版本的字体 URL与散列 JS/CSS 使用长期缓存，入口 HTML/合同 JSON 每次重新验证；字体未完成时先显示系统回退字体，导出仍通过 `document.fonts.ready` 等待字体稳定。

导出按串行 Mutex 执行：先等待 `specApplied`，再请求 `exportPng`。Renderer 将 PNG Blob 按每块最多 384 KiB 原始数据切分，通过带索引、总块数和字节数的 `exportChunk` 消息依次回传；Native 校验请求归属、顺序、块数与长度，并将解码后的数据流式写入临时文件。收到完成消息后，Native 再核对 MIME、总字节数、PNG 签名、结束标记和实际尺寸，验证通过才生成缓存 PNG。保存走 SAF，分享走 FileProvider。

分块内容仍以 Base64 编码以兼容当前 WebMessage 字符串桥接，但不会构造整张 PNG 的单一 Base64 字符串；失败、取消或校验不通过时会关闭并删除临时文件。WebMessage ArrayBuffer 快速通道可在真机压力测试后作为进一步的内存与吞吐优化评估，不是当前协议正确性的前提。
