# Privacy

本文说明当前 Lyrics Card Generator Android 源码的数据、存储与网络行为。

## 本地数据

应用在本机保存：

- 项目名称、歌词、翻译、歌曲 metadata、卡片设置和编辑时间；
- 导入并规范化后的封面文件；
- 最近导出的缩略图；
- 导出 PNG cache；
- 主题、默认导出倍率和安全区显示偏好。

项目数据库、封面和 cache 位于应用私有存储。删除项目会更新封面引用账本并清理不再引用的私有资源；应用启动时也会 reconciliation orphan/partial files。

### 备份与换机策略 / Backup and device-transfer policy

本项目选择不通过 Android 的系统备份或换机通道迁移应用私有数据。Manifest 保留 `android:allowBackup="false"`；Android 11 及以下的 `fullBackupContent` 规则，以及 Android 12 及以上分别针对 cloud backup 与 device-to-device transfer 的 `dataExtractionRules`，都会排除全部可备份域。因此 Room 项目与封面引用、DataStore 偏好、私有封面、缩略图、导出 cache 及临时文件不会由这些通道自动迁移。cache 与 no-backup 目录本来也由 Android 排除，规则同时拒绝其他文件、数据库、偏好、外部应用目录和 device-protected 域，避免以后新增数据被默认纳入。

这项策略不影响应用升级时保留原设备上的数据，也不控制用户通过系统文件选择器另存或已分享出去的图片。Android 12 及以上部分厂商会在 D2D 中忽略单独的 `allowBackup=false`，因此这里使用显式规则定义边界；这不是对已经发生数据泄漏或损坏的声明。规则依据 Android 官方的 [Auto Backup](https://developer.android.com/identity/data/autobackup) 与 [Android 12 backup/restore changes](https://developer.android.com/about/versions/12/behavior-changes-12) 文档。

This project does not migrate app-private data through Android system backup or device-transfer channels. The manifest keeps `android:allowBackup="false"`; the Android 11-and-lower `fullBackupContent` rules and the Android 12+ `dataExtractionRules` both exclude every eligible domain for cloud backup and device-to-device transfer. Room projects and cover references, DataStore preferences, private cover art, thumbnails, export cache, and temporary files therefore are not moved automatically through those channels. Android already excludes cache and no-backup directories, while these rules also deny file, database, preference, app-external, and device-protected domains so future data is not included by default.

This policy does not remove data during an in-place app update and does not control images that a user saved through the system picker or shared with another app. Some Android 12+ device makers ignore `allowBackup=false` for D2D by itself, so explicit extraction rules define the intended boundary. This does not claim that a leak or corruption has occurred.

通过系统文件选择器保存到应用外部位置的 PNG，以及已经分享给其他应用的文件副本，不再由本应用控制。卸载应用不会删除用户另存到外部位置的图片。

## 网络何时发生

普通项目编辑、预览和导出离线完成。只有用户主动执行下列操作时，Native 网易云客户端才会联网：

- 按歌曲名或歌手搜索；
- 解析网易云歌曲链接或短链；
- 获取所选歌曲的 metadata 与歌词；
- 下载用户选择歌曲的封面。

请求只允许 HTTPS，并限制到代码中的网易云 API、受信链接/短链 host 和网易云图片 host。每一次 redirect 都重新校验 scheme、host、port 与 user-info；连接时间、读取时间、redirect 次数和响应大小都有上限。

搜索时会向第三方服务发送用户输入的搜索词；解析或选歌时会发送歌曲 ID/链接所需的请求；网络对端也会按互联网协议看到常规连接 metadata。网易云音乐是独立第三方服务，其可用性、条款与数据处理不由本项目控制。

## Renderer WebView

APK 内置 Renderer 从固定本地 Origin `https://appassets.androidplatform.net` 加载。WebView 设置 `blockNetworkLoads=true`，禁止外部 navigation、下载、file access、content access 和 mixed content。封面通过受限的本地 `/media/<assetId>` handler 提供；Renderer 不接触真实文件路径，也不使用 `INTERNET` 权限访问网络。

## Analytics、tracking 与日志

当前代码没有 analytics、tracking、广告、遥测、崩溃上报或第三方 logging SDK。production source 没有把歌词、用户 URI、封面路径、网易云链接内容或完整 RenderSpec 写入 Logcat 的调用。

应用可以在设置页显示本机 app/Renderer/System WebView 的技术版本信息用于本地诊断；该信息不会由本项目自动上传。

## Android permissions 与分享

Manifest 只声明 `android.permission.INTERNET`，用途如上。封面选择与保存走 Android system picker，不申请广泛存储权限。分享通过 `exported=false` 的 FileProvider 与临时 URI permission 完成；接收应用之后如何处理文件由用户选择的目标应用决定。

## 数据控制

用户可以在应用内删除项目、移除封面并清理 export cache。Android 系统设置中的“清除存储”或卸载应用会删除应用私有数据；外部另存或已分享的文件需由用户在对应位置处理。

本说明若与代码行为发生冲突，应以最终候选 Manifest 与 source audit 为准，并在公开发布前修正文档，而不是推测或扩大隐私声明。
