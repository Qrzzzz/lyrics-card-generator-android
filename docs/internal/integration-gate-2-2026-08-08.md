# Integration Gate 2 evidence — 2026-08-08

## Verdict

**BUILD/AUTOMATION PASS; DEVICE GATE BLOCKED**

The only blocked sub-gate is installation and connected instrumentation on the
Xiaomi device. The one authorized attempt was rejected by the device policy
before the test runner started. No device-flow item is reported as passed.

## A. Candidate / scope

- Source branch: `codex/editor-export-production`
- Required candidate and verified starting HEAD:
  `b66bfaa3889d8db1de08347fca8d01ad4b926a81`
- Gate branch: `codex/integration-gate2`, created directly from that candidate
- Starting source worktree: clean; no staged, unstaged, or untracked changes
- Validation staging: detached worktree under the ASCII-only temporary root
  `C:\CodexTmp\lcg-g2-1b37-20260808`
- Scope was limited to cross-domain Native UI + Renderer/Data integration.
  No feature, visual design, Renderer CSS/pixel output, Room schema, Gradle,
  signing, public documentation, CI, release, or publishing change was made.
- Upstream prerequisites supplied for this exact candidate were Final Reviewer
  PASS, lint with zero errors, and successful R8/release validation. Gate 2 did
  not reclassify those Gate 3 inputs; it independently reran the overlapping
  Renderer, JVM, debug-build, and androidTest-build gates below.

## B. Integration findings / fixes

- No reproducible cross-domain source defect was found in the executable
  build, Renderer, JVM, Room, or androidTest compile gates.
- No source fix was made.
- Kotlin compilation retained two non-blocking warnings already present in the
  candidate: unnecessary safe calls in `ProjectAssetStore.kt:102` and
  `RendererController.kt:605`. They are not integration failures and were not
  changed outside this gate's repair boundary.
- The device failure is external to the candidate: MIUI rejected the package
  install before instrumentation began. It was not treated as a product PASS
  or as a reason to weaken device security.

## C. Invariants / hashes

- Protected `app/src` Git tree OID:
  `ace8fd3e189a9c926fdd3ab8c542f4b10755b6a2`
- Renderer Git tree OID:
  `86c5c57dbe21932144936e5e928cd7e6456cec88`
- Room schema Git tree OID:
  `b31be00bcc6ebca0a0a7a721b5131678797dc0b9`
- Renderer build parity: the eight files in `renderer/dist` and the eight files
  in `app/build/generated/renderer/assets/renderer` had identical relative
  paths and per-file SHA-256 values. The SHA-256 of the sorted
  `relative-path|file-sha256` manifest was
  `e8d34ea34f0e2e60e7224302c966aef6a5b672679bc3ae7975a0a1ff4b73eaf0`
  for both trees.
- Room schema v1 identity hash:
  `5a2578675a808b8139314eb2ff95fe49`; schema-file SHA-256:
  `5550f5abc7f31a2219ff67316b934e01e85de3d9816b8c38b8ca6878103fa990`
- Room schema v2/current identity hash:
  `f41250ec73e28f0d369bee74a378b9dc`; schema-file SHA-256:
  `aa9de80b264991eceb3cb43dac38e1b8fa743e09b83e7a25c508a0915106f988`
- `AppDatabase.VERSION` remained `2`; `MIGRATION_1_2` remained the only
  migration. `AppDatabaseTest` passed 3/3 tests in both Alpha and Production,
  including v1→v2 preservation and opening an existing v2 database.
- The candidate's UDF/AppContainer ownership, exact six-step flow, persistent
  mobile BottomSheet, 1x/2x-only export, FINALIZING atomicity, and Renderer
  security/session/latest-wins/cancel/recovery contracts were preserved by
  zero source changes and covered by the passing JVM/Renderer suites. The 17
  device test methods that cover the UI/lifecycle surfaces compiled for both
  variants but could not run because installation was rejected.
- `git diff --check` passed and the staging worktree remained clean after all
  builds. Normal builds did not modify tracked Renderer or source files.

## D. Commands and exact test counts

Environment used:

- Node `v24.14.1`; npm `11.11.0`
- Gradle `8.13`; Kotlin `2.0.21`
- JetBrains Runtime `21.0.10`
- Android SDK platforms `android-35` and `android-36.1`; Build Tools `36.1.0`
  used for artifact inspection; ADB `37.0.0-14910828`

Commands executed from the ASCII staging worktree:

1. `npm.cmd ci` — PASS; 111 packages added, 112 audited, 0 vulnerabilities.
2. `npm.cmd audit` — PASS; 0 vulnerabilities.
3. `npm.cmd run check` — PASS:
   - validator consistency: PASS
   - TypeScript: PASS
   - Vitest: **8 suites / 27 tests / 0 failures**
   - Vite production Renderer build: PASS, 54 modules transformed
4. `.\gradlew.bat :app:testAlphaDebugUnitTest :app:testProductionDebugUnitTest --no-parallel --stacktrace`
   — PASS. Counts recomputed from JUnit XML:
   - Alpha: **31 suites / 140 tests / 0 failures / 0 errors / 0 skipped**
   - Production: **31 suites / 140 tests / 0 failures / 0 errors / 0 skipped**
5. `.\gradlew.bat :app:assembleAlphaDebug :app:assembleProductionDebug :app:compileAlphaDebugAndroidTestKotlin :app:compileProductionDebugAndroidTestKotlin :app:assembleAlphaDebugAndroidTest :app:assembleProductionDebugAndroidTest --no-parallel --stacktrace`
   — PASS; both debug APKs, both androidTest Kotlin source sets, and both test
   APKs built successfully.
6. `git diff --check` — PASS.
7. `.\gradlew.bat :app:connectedProductionDebugAndroidTest --no-parallel --stacktrace`
   — **BLOCKED** during the sole authorized install attempt; **0 tests
   started / 0 tests executed**.

Built artifact evidence (ephemeral staging artifacts, deleted after reporting):

| Artifact | Package / version | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `app-alpha-debug.apk` | `com.qrzzzz.lyricscard.alpha.debug`, `1.0.0-alpha03-debug` (10000) | 62,729,811 | `129ea0675df36eea9c70c837f3485ea801e9c5f95b635c29d43227be09a5e546` |
| `app-production-debug.apk` | `com.qrzzzz.lyricscard.debug`, `1.0.0-debug` (10000) | 62,729,771 | `b3346b2c04c2789c42dc2c1aeedce4d9810a8b49ea9d09ab5e8a6db558e4e63f` |
| `app-alpha-debug-androidTest.apk` | `com.qrzzzz.lyricscard.alpha.debug.test` | 1,080,740 | `96c2611c10d4867cb081cec205a0e88936ce0fbe6623887be2349d937535e8ce` |
| `app-production-debug-androidTest.apk` | `com.qrzzzz.lyricscard.debug.test` | 1,080,704 | `5cede0adfa36b55d50f7ebad8f4446dc66375529a225523260e5c8b6d2118354` |

## E. Device / API / WebView and instrumentation result

- Manufacturer/model/device: Xiaomi `2210132C` / `nuwa`
- Android: `16`; API: `36`; security patch: `2026-07-01`
- System WebView: `com.google.android.webview` `150.0.7871.181`, enabled and
  valid for all users
- Before the attempt, productionDebug, its test package, alphaDebug, its test
  package, and the production package were all absent. Therefore no existing
  candidate or user data was cleared or overwritten.
- The one allowed command was:
  `.\gradlew.bat :app:connectedProductionDebugAndroidTest --no-parallel --stacktrace`
- Exact decisive error:
  `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`
- Gradle/UTP reported `Starting 0 tests` and `Finished 0 tests`. Its JUnit XML
  records `tests="0"` and the same install error. The task failed before the
  runner could start.
- After the failed attempt, both `com.qrzzzz.lyricscard.debug` and
  `com.qrzzzz.lyricscard.debug.test` were still absent.
- No retry, separate `adb install`, Alpha instrumentation, data clear,
  uninstall, permission change, MIUI optimization change, USB-install setting
  change, device-management change, or policy bypass was attempted.
- Consequently fresh launch; Home empty/sample/recent flows; six-step device
  navigation; Step 3–6 preview/persistent sheet; rotation/background; 1x/2x
  export; save/share/error/retry; light/dark; font scale 1.0/2.0; and on-device
  WebView session/recovery remain **BLOCKED / NOT EXECUTED**. Passing JVM tests
  and successfully compiled instrumentation tests are alternative evidence,
  not a substitute for those device results.

## F. Remaining blockers

1. **Device gate:** MIUI policy rejected the only permitted install attempt.
   A later, separately authorized device gate must allow the normal installer
   confirmation and rerun the same connected Production task. This Gate 2 may
   not retry or alter device security policy.
2. Until that succeeds, the 17 compiled Production instrumentation tests and
   all basic-device-flow items above have no executable device result.

No source-level blocker was found in the gates that could execute.

## G. Commit / workspace state

- Source changes: none.
- Gate change: this internal evidence report only.
- No push, PR, tag, GitHub Release, store publication, signing, or public docs
  action was performed.
- The ASCII staging worktree and its generated artifacts are removed only after
  this report is committed and the hashes/state above are rechecked.
- Final branch/worktree cleanliness and the report commit SHA are recorded in
  the task handoff after the commit succeeds.
