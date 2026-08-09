# Third-Party Notices

Lyrics Card Generator 的原创代码、设计、文档、项目资产、build scripts 与 release configuration 受根目录 `Lyrics Card Generator Source Available License` 约束：

> Copyright (c) 2026 Qrzzzz. All rights reserved.

该 Source Available License 不替代、修改或扩大任何第三方组件的许可证。以下内容只列出能够从当前仓库的字体 license、Gradle version catalog 和 npm lock metadata 确认的组件。

## Bundled fonts

APK 内置：

- Source Han Sans SC Heavy — Copyright 2014–2025 Adobe，SIL Open Font License 1.1；
- Source Han Serif SC Heavy — Copyright 2017–2022 Adobe，SIL Open Font License 1.1。

字体文件及完整版权/OFL 文本位于 `renderer/public/fonts/` 与 `renderer/public/fonts/LICENSE.txt`。`Source` 是 Adobe 在美国和/或其他国家的商标；Reserved Font Name 为 `Source`。

## Bundled Android libraries

当前 `gradle/libs.versions.toml` 声明并由 APK/AAB 使用的主要 Android 组件：

| Component | Catalog version | License |
| --- | --- | --- |
| AndroidX Activity Compose | 1.9.3 | Apache License 2.0 |
| AndroidX Core KTX | 1.15.0 | Apache License 2.0 |
| AndroidX Core SplashScreen | 1.2.0 | Apache License 2.0 |
| AndroidX Lifecycle Runtime/ViewModel Compose | 2.8.7 | Apache License 2.0 |
| AndroidX Navigation Compose | 2.8.5 | Apache License 2.0 |
| AndroidX Compose UI / Material 3 / Material Icons | Compose BOM 2024.10.01 | Apache License 2.0 |
| AndroidX WebKit | 1.16.0 | Apache License 2.0 |
| AndroidX Room Runtime/KTX | 2.8.4 | Apache License 2.0 |
| AndroidX DataStore Preferences | 1.1.1 | Apache License 2.0 |
| Kotlin Coroutines Android | 1.9.0 | Apache License 2.0 |
| Kotlin Serialization JSON | 1.7.3 | Apache License 2.0 |

Android System WebView 与 Android platform 由设备/系统提供，不打包在本项目 APK 内。

Apache License 2.0 全文：<https://www.apache.org/licenses/LICENSE-2.0>

## Bundled Renderer npm runtime

精确版本与 license 来自 `renderer/package-lock.json`（lockfile v3）：

| Package | Locked version | License |
| --- | ---: | --- |
| ajv | 8.20.0 | MIT |
| fast-deep-equal | 3.1.3 | MIT |
| fast-uri | 3.1.5 | BSD-3-Clause |
| html-to-image | 1.11.13 | MIT |
| json-schema-traverse | 1.0.0 | MIT |
| react | 19.2.7 | MIT |
| react-dom | 19.2.7 | MIT |
| require-from-string | 2.0.2 | MIT |
| scheduler | 0.27.0 | MIT |

- MIT License：<https://opensource.org/license/mit>
- BSD 3-Clause License：<https://opensource.org/license/bsd-3-clause>

## Build and test dependencies

下列直接依赖用于构建、类型定义或自动化测试，不作为独立 runtime API 提供；版本和 license 同样来自 repository metadata：

| Component | Version | License |
| --- | ---: | --- |
| Android Gradle Plugin | 8.13.2 | Apache License 2.0 |
| Android App Bundle tool (bundletool) | 1.18.1 | Apache License 2.0 |
| Kotlin Gradle/Compose/Serialization plugins | 2.3.21 | Apache License 2.0 |
| KSP | 2.3.8 | Apache License 2.0 |
| Room Gradle plugin/compiler | 2.8.4 | Apache License 2.0 |
| JUnit | 4.13.2 | Eclipse Public License 1.0 |
| Robolectric | 4.16.1 | MIT |
| AndroidX Test / Espresso | 1.6.1 / 1.2.1 / 3.6.1 | Apache License 2.0 |
| Guava ATF compile-only | 28.2-android | Apache License 2.0 |
| @playwright/test | 1.62.1 | Apache License 2.0 |
| TypeScript | 5.9.3 | Apache License 2.0 |
| Vite / @vitejs/plugin-react / Vitest | 6.4.3 / 4.7.0 / 3.2.7 | MIT |
| @types/node / react / react-dom | 22.20.1 / 19.2.17 / 19.2.3 | MIT |
| pixelmatch | 7.2.0 | ISC |
| pngjs / ssim.js | 7.0.0 / 3.5.0 | MIT |

- Eclipse Public License 1.0：<https://www.eclipse.org/legal/epl-v10.html>
- ISC License：<https://opensource.org/license/isc-license-txt>

Transitive build/test dependencies remain governed by their own metadata and license files in the resolved Gradle/npm distributions. `renderer/package-lock.json` is the authoritative npm version inventory for this commit.

## Third-party service and names

网易云音乐搜索、解析与 cover download 是用户主动触发的第三方在线能力，不是本项目托管服务。第三方服务、平台名称、商标和内容分别属于其权利人；本项目不表示官方关联、认可或授权。网络行为见 `PRIVACY.md`。
