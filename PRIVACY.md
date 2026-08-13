# Privacy

本文说明当前 Lyrics Card Generator Android 源码的数据、存储与网络行为。

## 本地数据

应用在本机保存：

- 项目名称、歌词、翻译、歌曲 metadata、卡片设置和编辑时间；
- 导入并规范化后的封面文件；
- 最近导出的缩略图；
- 导出 PNG cache；
- 主题、默认导出倍率和安全区显示偏好。

项目数据库、封面和 cache 位于应用私有存储。Manifest 设置 `android:allowBackup="false"`，应用不把这些数据同步到本项目提供的账号或云服务。删除项目会更新封面引用账本并清理不再引用的私有资源；应用启动时也会 reconciliation orphan/partial files。

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
