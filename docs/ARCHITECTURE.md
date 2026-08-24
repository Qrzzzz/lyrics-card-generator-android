# Lyric Card Generator 架构

本文描述当前 production 代码。历史 Alpha 设计说明不再作为架构事实来源。

## 运行时边界与所有权

```text
LyricsCardApplication
  └─ DefaultAppContainer（进程级依赖与资源所有者）
      ├─ Room AppDatabase / ProjectRepository
      ├─ UserPreferencesRepository
      ├─ ProjectAssetStore
      ├─ NeteaseMusicService（用户主动触发的 HTTPS）
      ├─ RendererController（延迟实例化）
      └─ EditorSessionRegistry

Navigation back-stack entry
  ├─ HomeViewModel
  ├─ EditorViewModel + SavedStateHandle
  └─ ExportViewModel + SavedStateHandle

MainActivity
  └─ SettingsViewModel
```

`DefaultAppContainer` 使用 application context，负责进程级 repository、资源文件、Renderer controller 和数据库的生命周期。它不把 Activity 传入长生命周期对象。`SettingsViewModel` 属于 Activity；Home、Editor、Export ViewModel 属于各自 Navigation back-stack entry。Screen 收集不可变 `StateFlow` 并将事件回传 ViewModel，Compose 不直接拥有持久化或导出 Job。

应用启动时只在后台执行存储 reconciliation。Home 不预热或创建 WebView。

## 导航与状态恢复

Navigation Compose 使用可序列化的 typed routes：`HomeRoute`、`EditorRoute(projectId)`、`ExportRoute(projectId)` 和 `SettingsRoute`。Editor/Export 路由只持有项目 UUID，不传递 RenderSpec、Bitmap、URI 或文件路径。

Editor 与 Export 通过 `SavedStateHandle` 保存路由 ID 和可恢复的 UI/operation metadata，并从 Room 重新加载项目。Editor 的编辑状态由 `EditorViewModel` 统一拥有：500 ms 防抖自动保存、50 步 undo/redo、离开/`onStop` flush 与导航提交状态都在 ViewModel 层完成。进程恢复不依赖仍存活的 Composable。

## RenderSpec 合同

`RenderSpec v1` 是 Native 与 Renderer 的唯一卡片输入。Kotlin 模型位于 `app/src/main/java/com/qrzzzz/lyricscard/model/RenderSpec.kt`，JSON Schema 位于 `renderer/schema/render-spec-v1.schema.json`，构建生成的独立 validator 位于 `renderer/src/generated/renderSpecValidator.ts`。

合同不保存 Android URI、真实文件路径、Bitmap 或面板展开状态。封面只使用 UUID logical asset ID。Room 保存完整 `specJson`，并冗余 schema、renderer 与 cover ID 以发现损坏或不一致数据。

内部兼容标识 `android-alpha-renderer-1` 仍是既有 RenderSpec v1 的不可变合同值；它不是当前产品 channel，也不能仅为移除名称中的 Alpha 而改变，否则会破坏已保存项目、Schema 与 Golden 兼容性。

## WebView lazy creation、复用与恢复

`RendererController` 在首次创建 Editor/Export 依赖时延迟实例化，但构造 controller 本身不会创建 WebView。`EditorScreen` 只有在第三步 `LAYOUT` 及之后组合 `RendererPreview`；此时 `AndroidView.factory` 首次调用 `acquireWebView`，WebView 才创建并加载本地 Renderer。前两步和 Home 没有 WebView。Export route 使用同一个进程级 controller。

WebView 脱离某个 Compose host 时会从父 View 移除、暂停并将 `MutableContextWrapper` 恢复为 application context，但实例、当前 spec 与 renderer session 会保留。新的 Editor/Export/Activity host 通过 owner identity 重新绑定 context，从而避免旧 composition 的延迟 release 误拆新 host。controller close、明确 retry/recovery、Renderer process termination 或进程结束才销毁对应 WebView；恢复会增加 generation，并让 Compose 创建新的安全 session。

Preview update 采用单一 pending spec 与串行 pump：进行中的 spec 完成后只处理最新值。导出与 preview 共享 operation mutex，并使用 session/request ID 拒绝过期消息。timeout、cancel、bridge failure 或 renderer process termination 会终止 pending request、清理 partial export 并按既有 recovery 规则重建 session。

## WebView 安全边界

唯一主入口为：

```text
https://appassets.androidplatform.net/renderer/index.html
```

`WebViewAssetLoader` 只服务安全的 `GET /renderer/` 与 `GET /media/` 路径。其余请求返回阻断响应。WebView 禁用文件访问、内容访问、DOM Storage、混合内容、弹窗、新窗口、下载和网络加载；navigation 只允许固定 Renderer URL。Native/Web 消息带 protocol、request 和 session identity，listener origin 固定为 `https://appassets.androidplatform.net`。Release variant 中 `BuildConfig.DEBUG` 为 false，因此 Web contents debugging 被关闭。

Manifest 设置 `usesCleartextTraffic=false`。除 launcher MainActivity 外没有 exported activity；FileProvider 为 `exported=false` 并只通过临时 URI grant 分享文件。release manifest 中 ProfileInstaller receiver 也强制为 `exported=false`。

## Asset ledger 与存储生命周期

`ProjectAssetStore` 将导入封面规范化后写入应用私有 `files/project-assets`，用 UUID 定位；Renderer 只能通过 `/media/<assetId>` 读取，永远看不到真实路径。Room v2 的 `cover_assets` 是从 `projects.cover_asset_id` 派生的 reference ledger。

项目 create/save/duplicate/delete 与 cover reference 更新在 Room transaction 中完成。提交后，repository 才标记文件已引用或删除引用数归零的文件。应用启动 reconciliation 会重建 ledger，保护尚未提交的导入，删除 orphan cover、orphan thumbnail、partial export，并清除指向缺失缩略图的数据库字段。Room v1→v2 migration 从现有项目重建引用计数。

缩略图采用原子发布；项目 export metadata 与 thumbnail path 通过单一 Room update 提交。导出 PNG 位于 cache，用户选择保存后通过 SAF 写入目标位置，分享使用 FileProvider。

## Export state 与原子完成

`ExportViewModel` 明确建模 `IDLE → PREPARING → RENDERING → FINALIZING → SUCCESS`，并保留 `CANCELLED`、`FAILURE`、`INTERRUPTED`。PREPARING/RENDERING 可取消；进入 `FINALIZING` 后在 `NonCancellable` 中完成缩略图、Room export record 和可恢复成功 metadata，避免 UI 显示半完成状态。

Renderer 先应用/测量 spec，再在 mutex 内生成 PNG。PNG Blob 使用单次 ArrayBuffer read 与有界 Base64 chunks 回传；Native 校验 session、request、chunk 顺序、块数、总字节数、MIME、PNG 签名和实际尺寸，并先写 `.part`，验证成功才原子发布。取消或失败会关闭并删除 partial/final 临时文件，recovery 不改变 latest-wins/cancel 语义。Export preview 在后台解码，并回收 retired Bitmap。

## Native 网络边界

`INTERNET` 权限仅供 `NeteaseMusicService`。请求必须是 HTTPS、无 user-info、默认或 443 端口，并限制到网易云 API、受信短链和封面 host；redirect 每一跳都会重新校验。连接、读取、redirect 次数和响应大小都有上限。Renderer WebView 不使用这项权限，不访问互联网。

代码未接入 analytics、tracking、广告、崩溃上报或第三方 logging SDK，production source 也没有记录歌词、用户 URI、封面路径、网易云链接或完整 RenderSpec 的 Logcat 调用。

## Build、variants 与 release pipeline

Gradle `buildRenderer` 的 inputs 包含 Renderer source、scripts、public assets、schema、manifest 与 lockfile，output 固定在 `app/build/generated/renderer/assets/renderer`。`preBuild` 依赖它，因此 APK/AAB 使用同一次受控的本地 Vite build；generated assets 不写入 `app/src`。Renderer 的 `npm run check` 独立执行 validator consistency、TypeScript、Vitest 和 production build。

`alpha` 与 `production` 是 channel flavors。正式版使用 `productionRelease`：`minSdk 26`、`targetSdk 36`、`versionName 1.1.0`、`versionCode 10100`、package `com.qrzzzz.lyricscard`，并保持 R8 minification 与 resource shrinking 开启。正式产物使用独立生产签名；此前公开 Beta 使用测试签名，不能直接升级到正式版。

Production signing 统一从四个 `LYRICS_CARD_*` 环境变量或未跟踪 `release-signing.properties` 读取。四项全部缺失时 productionRelease 保持 unsigned/minified 可验证；只配置一部分会在 configuration 阶段失败；全部配置时只有 `productionRelease` 使用该 signing config，`productionDebug` 仍由 Android Gradle Plugin 的 debug signing config 签名。固定版本的 bundletool 从实际 AAB 提取 manifest，用于核对 package、version 与 SDK。CI 不接收 signing secrets。手动 release-candidate workflow 在临时 runner 目录解码 keystore、构建后验证 APK/AAB 签名与元数据、生成 SHA-256，并在 `always()` cleanup 中删除临时 signing directory；workflow 没有公开发布步骤或仓库写权限。
