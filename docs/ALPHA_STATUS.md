# Alpha 0.1 状态

## 已验证

- Renderer TypeScript 类型检查、Vitest 与 Vite 生产构建；
- RenderSpec 序列化、默认值、校验和歌词清理；
- Room DAO 与 ProjectRepository 的增删改查；
- Kotlin/Compose 主代码编译；
- Alpha Debug APK 构建；
- 编辑与导出路由携带项目 ID，进程重建后可从 Room 重新载入当前项目；
- 六步编辑流程与 Web 版顺序一致，并保留网易云歌名搜索、分享链接贴入、歌曲/歌词/封面导入；
- Renderer 在项目列表阶段预热，编辑与导出复用同一 WebView，本地字体与静态资源可缓存；
- Renderer 浏览器烟雾截图（竖版与横版）。

PNG 导出协议已改为每块最多 384 KiB 原始数据的编号 Base64 分块。Native 按顺序校验并流式写入临时文件，完成时再核对块数、总字节数、PNG 结构和实际尺寸，避免持有整张图片的 Base64 字符串。

## 发布前必做

- 将 5 个 fixtures 扩展为 30 个 Windows/Android 共用 Golden；
- 固定参考模拟器、System WebView 版本和 SSIM 阈值；
- 在 API 26、30、33、36 启动和完成导出；
- 4 GB 真机完成标准 2× 导出；
- 连续导出 20 次并记录内存曲线；
- 连续编辑 30 分钟并验证后台回收后的项目恢复；
- 在 4 GB 真机压力测试后评估 WebMessage ArrayBuffer 快速通道，作为现有分块协议的进一步内存与吞吐优化；
- 为封面资源补充 Room `AssetEntity` 引用计数与孤儿清理；
- 补齐无障碍语义、TalkBack 和大字体测试；
- 把 Windows `lib/palette-extraction.ts` 提取为共享算法，替换当前精简取色器；
- 抽离并共用 Windows 预览组件，完成逐组件像素核对，替换当前仍属近似移植的布局与品牌图形。

除网易云外的音乐平台搜索/链接解析、AI 翻译、音频元数据、任意字体、账号/云同步、模板市场和视频卡片仍按方案留到 Beta。网易云接口属于第三方在线能力，发布前仍需补充弱网、超时、限流与接口变化的真机验证。
