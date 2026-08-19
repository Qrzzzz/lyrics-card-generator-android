<div align="center">

# 🎧 Lyrics Card Generator Android

### Create polished lyric sharing cards on Android

**NetEase Cloud Music import · Six-step card editor · Offline live preview · High-resolution PNG export**

<p>
  <strong>Language</strong><br/>
  <a href="./README.md">简体中文</a> ·
  <strong>English</strong>
</p>

<p>
  <strong>Navigation</strong><br/>
  <a href="https://github.com/Qrzzzz/lyrics-card-generator-android/releases/latest">Download</a> ·
  <a href="#features">Features</a> ·
  <a href="#how-to-use">How to Use</a> ·
  <a href="#local-development">Local Development</a> ·
  <a href="https://github.com/Qrzzzz/lyrics-card-generator-android/blob/main/PRIVACY.md">Privacy</a> ·
  <a href="./LICENSE">License</a>
</p>

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Stack](https://img.shields.io/badge/Stack-Kotlin%20%2B%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Renderer](https://img.shields.io/badge/Renderer-React%20%2B%20CSS-149ECA?logo=react&logoColor=white)
![Output](https://img.shields.io/badge/Output-PNG%201%C3%97%20%2F%202%C3%97-FF5722)
[![Release](https://img.shields.io/github/v/release/Qrzzzz/lyrics-card-generator-android)](https://github.com/Qrzzzz/lyrics-card-generator-android/releases/latest)

</div>

---

<details open>
<summary><strong>✨ Output references</strong></summary>

<table>
  <tr>
    <td width="50%" align="center" valign="top"><sub><b>Without translation · English</b></sub><br/><img src="./docs/readme-assets/cards/en.single.webp" alt="Reference output of an English lyric card without translation" width="100%" /></td>
    <td width="50%" align="center" valign="top"><sub><b>With translation · Japanese original + English translation</b></sub><br/><img src="./docs/readme-assets/cards/en.bilingual.webp" alt="Reference output of a Japanese lyric card with English translation" width="100%" /></td>
  </tr>
</table>

These images were exported by [Lyrics Card Generator for desktop](https://github.com/Qrzzzz/lyrics-card-generator) as references for the card design and typography. The Android app bundles a compatible React/CSS renderer to preserve the same visual language and output contract.

</details>

## 📦 Download and Installation

Go to [GitHub Releases](https://github.com/Qrzzzz/lyrics-card-generator-android/releases/latest) to download the latest version:

- Most users should download the `.apk` file and follow Android's installation prompts.
- The `.aab` is intended for app distribution and release verification; it cannot be installed directly on a phone.
- The app requires Android 8.0 (API 26) or later and a working Android System WebView.

When sideloading the APK from a browser, Android may ask you to temporarily allow that download source to install unknown apps. You can turn the permission off again after installation.

<a id="features"></a>

## ✨ Features

### 🎨 Card Editing and Typography

- Build a card through six focused steps: song, lyrics, layout, typography, visual style, and export.
- Use original lyrics, translations, or instrumental copy with portrait, landscape, and automatic-size layouts.
- Adjust Source Han Sans / Source Han Serif, font size, letter spacing, line height, alignment, text color, and borders.
- Extract colors from cover art and customize gradient backgrounds, grid density, platform marks, sharer details, and the generator watermark.

### 🎵 Song Information and Cover Art

- Enter title, artist, album, lyrics, and translation manually.
- Search NetEase Cloud Music and import song metadata, lyrics, and cover art.
- Paste a NetEase Cloud Music song link or shared text for parsing.
- Import local cover art through Android's system file picker.

### 🗂️ Local Project Management

- Create blank or sample projects, then duplicate, rename, and delete them from the home screen.
- Store projects locally with Room, including autosave and 50-step undo / redo.
- Restore editing state after configuration changes or process recreation.
- Track cover references, thumbnails, and export cache while cleaning interrupted temporary files.

### 🖼️ Preview, Export, and Sharing

- Start live preview only when it becomes useful in step three, then reuse the renderer session through later steps and export.
- Render entirely from the React/CSS bundle included in the APK—no remote web page is required.
- Export high-resolution PNG files at 1× or 2×.
- Save through the system file picker or send the result through Android's share sheet.

### 🌓 Android Experience

- Native Jetpack Compose UI with compact, medium, and expanded layouts.
- Follow-system, light, and dark themes.
- Only the Internet permission is declared; imports and exports use Android's system pickers instead of broad storage permissions.

## 🔒 Offline Use and Privacy

Manual editing, project management, card preview, and PNG export all work offline. The native client makes restricted HTTPS requests only when you explicitly search or parse NetEase Cloud Music content, fetch lyrics, or download selected cover art.

The renderer WebView blocks network access, external navigation, file access, and mixed content. The app includes no analytics, tracking, advertising, telemetry, or crash-reporting SDK. Projects, cover art, and export cache remain on the device. See [PRIVACY.md](https://github.com/Qrzzzz/lyrics-card-generator-android/blob/main/PRIVACY.md) for the complete data and network behavior.

<a id="how-to-use"></a>

## 🚀 How to Use

1. Create a blank project or start with the sample project.
2. Search NetEase Cloud Music, paste a song link, or enter song information manually and choose cover art.
3. Edit the lyrics and translation, then select the appropriate content mode.
4. Adjust canvas layout, typography, and visual style while checking the live preview.
5. Choose 1× or 2× and export a PNG.
6. Save the image to a selected location or share it directly with another app.

<a id="local-development"></a>

## 🛠️ Local Development

### Requirements

- JDK 17
- Node.js 20 or later
- Android SDK Platform 36.1
- Android SDK Build Tools 36.1.0

### Windows

```powershell
cd renderer
npm.cmd ci
npm.cmd run check

cd ..
.\gradlew.bat :app:test :app:lintProductionRelease :app:assembleAlphaDebug --no-parallel
```

### macOS / Linux

```bash
cd renderer
npm ci
npm run check

cd ..
./gradlew :app:test :app:lintProductionRelease :app:assembleAlphaDebug --no-parallel
```

Gradle's `preBuild` task builds the renderer automatically and writes generated assets to the Gradle build directory. Nothing needs to be copied manually into `app/src/main/assets`.

## 🧩 Project Structure

```text
app/                         Android Compose app and Android tests
renderer/                    Local React/CSS renderer, schema, tests, and Golden files
docs/                        Architecture, release readiness, and internal evidence
.github/workflows/           CI and release-candidate workflows
```

## 🖥️ Desktop Version

This project's card design and cross-platform renderer contract originate from [Lyrics Card Generator](https://github.com/Qrzzzz/lyrics-card-generator). The desktop app targets Windows and adds more music-platform parsers, AI translation, PNG / WebP / JPG export, and Web Lite. The Android app focuses on native mobile editing, local project management, and system-level saving and sharing.

## 🙏 Acknowledgements

Thanks to [Source Han Sans](https://github.com/adobe-fonts/source-han-sans) and [Source Han Serif](https://github.com/adobe-fonts/source-han-serif) for a dependable foundation for CJK typography, and to the maintainers of AndroidX, Jetpack Compose, React, and `html-to-image`. See [THIRD_PARTY_NOTICES.md](https://github.com/Qrzzzz/lyrics-card-generator-android/blob/main/THIRD_PARTY_NOTICES.md) for the complete third-party component and license list.

## 📄 License

This project uses a custom [Source Available License](./LICENSE), not a conventional open-source license.

You may view, download, and run the source for personal, non-commercial, learning, and evaluation purposes, and make private modifications for personal use. Commercial use, redistribution, repackaging, public distribution of modified versions, and competing products require prior written permission from the author. Third-party components remain under their respective licenses.
