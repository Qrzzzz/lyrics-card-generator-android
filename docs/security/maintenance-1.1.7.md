# 1.1.7 runtime and Renderer maintenance

Scope: plan Step 7–9, baseline main `03dd96b934683455b99f27ee163eced59ad74165`, public v1.1.6. Baseline Quality Gate 35049078866 and Dependency Security 35049078872 both completed successfully. Live baseline: only issue #45 and React PR #56 open; zero open Dependabot alerts. Prior failures and original advisory deadlines remain in historical records.

## Decisions

- [Coroutines 1.11.0](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0) replaces 1.9.0, including cancellation/SharedFlow and R8 stateIn fixes. Android and test artifacts share the catalog version.
- [Serialization 1.11.0](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.11.0) replaces 1.7.3. Kotlin 2.4.20 remains; the newer serialization 1.12.0-RC is a prerelease and is not needed. Persisted schema 1 and `android-alpha-renderer-1` remain unchanged.
- #56 was updated to current main before review. Its four React/runtime/types 19.3 updates are included equivalently here, preserving serial human PR integration.
- React 19.3 passed typecheck/tests/build before Vite migration. Vite 8.3.0 and plugin-react 6.1.1 satisfy the plugin's Vite ^8 peer and Node24 engines; that pair passed the same checks before TypeScript 7.0.2 was installed and checked independently. No force/legacy-peer-deps. Explicit chrome69 build target, local base URL, schema/font emission and pixel references remain intact. See [Vite migration](https://vite.dev/guide/migration).
- AGP 8.13.2 / Gradle 8.14.4 / JDK17 hosted builds / KSP 2.3.12 / Kotlin 2.4.20 / R8 9.1.29 and the host-only security constraints stay in place. AGP9 and unrelated major migrations offer no needed repair here. Actions and AndroidTest backlog decisions/evidence remain in the 1.1.5 and 1.1.6 reports. Optional extended device matrices are on-demand diagnostics, not an indefinitely deferred release prerequisite.

## Regression coverage and evidence

Implementation `06da61a56857490db173605ac4aaabb60528c9e7`:

- PASS: separate React, Vite/plugin, and TypeScript `npm run check`; final clean `npm ci --no-audit --no-fund`.
- PASS: `npm run golden:test`, all 30 existing PNG references exact, including 1×/2× and fresh-page serif ordering. References were not regenerated.
- PASS: `npm run audit:security`, zero findings including development dependencies.
- Added queued-export cancellation coverage: cancelling a mutex waiter cannot cancel the active export or prevent another export. Existing tests cover active cancellation, timeout, stale callbacks, temporary-file cleanup, delayed save outcomes and finalization.
- Added sparse legacy project edit/save/reopen, invalid JSON/type/enum rejection, and Native/Renderer envelope roundtrip/unknown fields/missing required fields. Existing repository tests retain corrupt rows and preserve concurrent export metadata.
- Local first invocation was NOT RUN due to an invalid debug AndroidTest task name; corrected to this repository's release test variant. This tooling error is not a product failure.
- Android JVM/build, actual emulator, full hosted CI, dependency submission and signed publication evidence will be appended when complete. No pending run is treated as PASS.
- NOT RUN: physical-device six manual checks, complete multi-API matrix, 20 exports, 30-minute endurance, low-memory, TalkBack and large-font专项. Local emulator evidence does not replace physical-device observations or signed-candidate provenance.

Issue #45 closes only after integration and public release verification. New ordinary Dependabot updates after this baseline belong to the normal monthly maintenance cycle.
