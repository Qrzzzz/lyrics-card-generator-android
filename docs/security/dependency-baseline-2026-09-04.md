# Gradle dependency advisory remediation baseline — 2026-09-04

Tracking issue: **pending publication**. This document is the proposed body of the public remediation issue required by [Issue #11](https://github.com/Qrzzzz/lyrics-card-generator-android/issues/11). Create that issue, assign `Qrzzzz`, and replace this line with its URL before closing #11. A committed draft alone does not complete the public tracking requirement.

Owner: **Qrzzzz** for every item below. Status: **OPEN — upgrade compatibility validation pending** for the Gradle items. Due dates are remediation/reassessment deadlines, not vulnerability suppressions or release waivers. Keep the original GitHub severity and each alert open until a verified upgrade or an explicitly reviewed disposition is recorded.

## Observed baseline and activation evidence

- Source: `bdd93a074ba577e9f2de230515052eb69c7e13d2` on `main`, audited on 2026-09-04 UTC.
- [PR #14](https://github.com/Qrzzzz/lyrics-card-generator-android/pull/14), merge commit `a00bc6897ef8e0c4148bf2264c4ad434bfbc5948`, introduced the npm audit gate, three Dependabot ecosystems, Dependency Review, and default-branch Gradle submission.
- GitHub API now returns `enabled: true, paused: false` for automated security fixes. Both Dependabot alerts and Dependency Graph SBOM endpoints are readable.
- [Dependency submission run 32768030346](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/32768030346) succeeded on the source SHA. Its [Android Quality Gate run](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/32768030303) also succeeded. These August results do not replace a fresh September audit.
- The September 4 SBOM contains 651 packages: 474 Maven, 167 npm, and 10 other entries. The [normalized API snapshot](dependency-alerts-2026-09-04.json) preserves every alert ID, original severity, package, affected range, first patched version, creation time, and URL, with hashes of the original responses.
- Open GitHub alerts at observation: **58 = 2 critical + 26 high + 27 medium + 3 low**. Maven accounts for 54 alerts first recorded on August 24; npm `fast-uri` accounts for four high alerts first recorded on September 3. The 24 high/critical Maven records reduce to **23 distinct GHSA/package items** because the two Bouncy Castle alerts describe different affected version ranges of the same package/advisory.
- Dependabot produced npm, Gradle, and Actions PRs. For example, [Gradle PR #23](https://github.com/Qrzzzz/lyrics-card-generator-android/pull/23) ran [Android quality checks](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/32744270651) and [Dependency Review](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/32744270624) on `8ada805ff9d47b3ed8f6205baa9eb852ba717e4d`. Proposed updates still require ordinary review and validation.

## Scope and reachability evidence

Independent resolution used Gradle 8.13 and JBR 21.0.10 on an isolated ASCII-path checkout. The following configurations resolved successfully. Dependency inspection did not run or install anything on a device.

| Configuration | Resolved components | Alerted Maven packages present |
| --- | ---: | --- |
| `productionReleaseRuntimeClasspath` | 131 | None |
| `productionReleaseCompileClasspath` | 111 | None |
| `productionReleaseUnitTestRuntimeClasspath` | 173 | Bouncy Castle 1.81; non-vulnerable Guava 33.4.8-jre |
| `productionReleaseAndroidTestRuntimeClasspath` | 114 | jsoup 1.12.2; Guava 28.2-android |
| `productionReleaseAndroidTestCompileClasspath` | 141 | Guava 28.2-android |
| `bundletoolCli` | 18 | protobuf-java 3.22.3; jose4j 0.9.5; non-vulnerable Guava 32.0.1-jre |
| Root buildscript classpath | 158 | AGP/Kotlin build tools, Bouncy Castle 1.79, Netty 4.1.110.Final, JDOM 2.0.6, Commons Compress 1.21, jose4j 0.9.5; protobuf-java already resolves to 3.25.5 here |
| 12 `_internal-unified-test-platform-*` configurations | All resolved | UTP contains protobuf-java/protobuf-kotlin 3.24.4 and Netty 4.1.93.Final, among other tool dependencies |

The alert API labels Maven scope as unknown and its manifest as `settings.gradle.kts`; that alone does not identify application runtime exposure. The resolved production runtime and compile graphs contain none of the Maven packages named by the 54 alerts. This is dependency-scope evidence, not a claim that build/test hosts are immune to the reported vulnerabilities. No hostile exploit proof was executed against third-party build tools.

The report generated at `2026-09-04T06:56:37.811054700Z` has SHA-256 `1dce6f6c89a698d0f137d537f9e5cdd0c3ac6e203c7a64b299bbc66d89128975`. Reproduce the relevant scopes with separate invocations of `:app:dependencies --configuration <name>` and root `buildEnvironment` on the recorded SHA. Use `--no-daemon --no-configuration-cache --console=plain`; use the exact release AndroidTest configurations because this project sets `testBuildType = "release"`.

The scope codes in the item table refer to these observed dependency paths and required actions:

| Code | Observed path and scope | Upgrade plan and remaining validation |
| --- | --- | --- |
| BC | AGP 8.13.2 → builder/sdk-common/apkzlib → bcprov/bcpkix 1.79; Robolectric 4.16.1 → bcprov 1.81. Build tools and JVM tests only. | Evaluate a compatible parent-tool update or tightly scoped constraints to the fixed BC line. The critical advisory concerns GOST CTR counter reuse; there is no repository use of that cipher, but third-party tool call paths were not exhaustively audited. Validate signing, R8, bundletool, and JVM tests before changing either dependency root. |
| N | AGP → gRPC 1.69.1 → Netty 4.1.110.Final; UTP 0.0.9-alpha03 → gRPC 1.57.2 → Netty 4.1.93.Final. Host build/device-test tools only. | Evaluate parent-tool upgrades or align the complete Netty family in the affected host configurations. Candidate 4.1.137.Final covers the highest first-patched requirement currently reported. Validate gRPC/UTP compatibility and the existing device gate. Do not add a Netty runtime dependency to the app as a substitute. |
| P | `bundletoolCli` → protobuf-java 3.22.3; UTP → protobuf-java/protobuf-kotlin 3.24.4. Host tools only. | Evaluate bundletool/UTP-compatible updates to at least 3.25.5 for each affected root. AGP's own classpath already resolving protobuf-java 3.25.5 does not repair the separate configurations. Validate AAB manifest extraction and device-test tooling. |
| J | AGP → Jetifier processor 1.0.0-beta10 → JDOM 2.0.6. Build tools only. | Evaluate the parent-tool path or a scoped JDOM 2.0.6.1 update. Verify dependency transformation/build compatibility; absence of direct app SAXBuilder use is not a host-level exemption. |
| O | bundletool 1.18.1 → jose4j 0.9.5, present in the dedicated CLI and AGP classpath. Build tools only. | Evaluate a bundletool-compatible update to at least 0.9.6 and validate AAB manifest extraction. The vulnerable operation parses compressed JWE; the repository invokes bundletool on its generated AAB, but no third-party call-path exemption is claimed. |
| S | Espresso accessibility → Accessibility Test Framework 3.1.2 → jsoup 1.12.2 and Guava 28.2-android. Instrumentation test APK only; Guava is also explicitly compile-only for instrumentation. | Evaluate a compatible accessibility-test-framework/Espresso update or scoped test dependency constraints. jsoup 1.15.3 covers both recorded jsoup findings. Verify accessibility instrumentation and duplicate-class behavior before changing the Guava Android/JRE variant. |
| C | AGP → sdk-common/sdklib/repository → Commons Compress 1.21. Build tools only. | Evaluate an AGP-compatible update to at least 1.26.0 and validate SDK/archive handling. |
| K | Kotlin Gradle plugin 2.3.21. Build tools only. | Follow the fixed upstream Kotlin line and validate it with AGP/KSP/Compose. The alert reports 2.4.20-Beta1 as the first fix; do not promote a prerelease toolchain solely to clear a medium advisory without the full compatibility gates. The repository has no configured remote build cache. |

Maven Central returned HTTP 200 for the POMs of BC 1.84, Netty `netty-codec-http` 4.1.137.Final, JDOM 2.0.6.1, jsoup 1.15.3, jose4j 0.9.6, and protobuf-kotlin 3.25.5 on September 4. This proves artifact availability, not project compatibility. [Bouncy Castle's upstream notice](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902025%E2%80%9014813) confirms fixes in 1.80.2, 1.81.1, and 1.84; 1.84 is the family candidate because separate medium BC advisories also require it.

## Every high/critical Maven item

`OPEN` means a fix version exists but the affected tool configuration has not yet been upgraded and validated. Each row retains its original severity. Observed versions are described by the scope code above; the normalized snapshot contains the precise affected range for each alert, so an unaffected version in another configuration is not counted as a repair.

| Alert(s) | Original severity | Advisory | Package | Scope | First patched version | Owner | Due | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 41, 42 | critical | [GHSA-574f-3g2m-x479](https://github.com/advisories/GHSA-574f-3g2m-x479) | `org.bouncycastle:bcprov-jdk18on` | BC | 1.80.2 / 1.81.1; family candidate 1.84 | Qrzzzz | 2026-09-11 | OPEN |
| 1 | high | [GHSA-m72m-mhq2-9p6c](https://github.com/advisories/GHSA-m72m-mhq2-9p6c) | `org.jsoup:jsoup` | S | 1.14.2; candidate 1.15.3 also covers alert 2 | Qrzzzz | 2026-09-18 | OPEN |
| 4 | high | [GHSA-xpw8-rcwv-8f8p](https://github.com/advisories/GHSA-xpw8-rcwv-8f8p) | `io.netty:netty-codec-http2` | N | 4.1.100.Final | Qrzzzz | 2026-09-18 | OPEN |
| 10 | high | [GHSA-735f-pc8j-v9w8](https://github.com/advisories/GHSA-735f-pc8j-v9w8) | `com.google.protobuf:protobuf-java` | P | 3.25.5 | Qrzzzz | 2026-09-18 | OPEN |
| 11 | high | [GHSA-735f-pc8j-v9w8](https://github.com/advisories/GHSA-735f-pc8j-v9w8) | `com.google.protobuf:protobuf-kotlin` | P | 3.25.5 | Qrzzzz | 2026-09-18 | OPEN |
| 12 | high | [GHSA-4g8c-wm8x-jfhw](https://github.com/advisories/GHSA-4g8c-wm8x-jfhw) | `io.netty:netty-handler` | N | 4.1.118.Final | Qrzzzz | 2026-09-18 | OPEN |
| 15 | high | [GHSA-prj3-ccx8-p6x4](https://github.com/advisories/GHSA-prj3-ccx8-p6x4) | `io.netty:netty-codec-http2` | N | 4.1.124.Final | Qrzzzz | 2026-09-18 | OPEN |
| 18 | high | [GHSA-2363-cqg2-863c](https://github.com/advisories/GHSA-2363-cqg2-863c) | `org.jdom:jdom2` | J | 2.0.6.1 | Qrzzzz | 2026-09-18 | OPEN |
| 20 | high | [GHSA-3677-xxcr-wjqv](https://github.com/advisories/GHSA-3677-xxcr-wjqv) | `org.bitbucket.b_c:jose4j` | O | 0.9.6 | Qrzzzz | 2026-09-18 | OPEN |
| 21 | high | [GHSA-pwqr-wmgm-9rr8](https://github.com/advisories/GHSA-pwqr-wmgm-9rr8) | `io.netty:netty-codec-http` | N | 4.1.132.Final | Qrzzzz | 2026-09-18 | OPEN |
| 22 | high | [GHSA-w9fj-cfpg-grvv](https://github.com/advisories/GHSA-w9fj-cfpg-grvv) | `io.netty:netty-codec-http2` | N | 4.1.132.Final | Qrzzzz | 2026-09-18 | OPEN |
| 29 | high | [GHSA-mj4r-2hfc-f8p6](https://github.com/advisories/GHSA-mj4r-2hfc-f8p6) | `io.netty:netty-codec` | N | 4.1.133.Final | Qrzzzz | 2026-09-18 | OPEN |
| 30 | high | [GHSA-57rv-r2g8-2cj3](https://github.com/advisories/GHSA-57rv-r2g8-2cj3) | `io.netty:netty-codec-http` | N | 4.1.133.Final | Qrzzzz | 2026-09-18 | OPEN |
| 32 | high | [GHSA-f6hv-jmp6-3vwv](https://github.com/advisories/GHSA-f6hv-jmp6-3vwv) | `io.netty:netty-codec-http` | N | 4.1.133.Final | Qrzzzz | 2026-09-18 | OPEN |
| 33 | high | [GHSA-f6hv-jmp6-3vwv](https://github.com/advisories/GHSA-f6hv-jmp6-3vwv) | `io.netty:netty-codec-http2` | N | 4.1.133.Final | Qrzzzz | 2026-09-18 | OPEN |
| 34 | high | [GHSA-3qp7-7mw8-wx86](https://github.com/advisories/GHSA-3qp7-7mw8-wx86) | `io.netty:netty-handler` | N | 4.1.135.Final | Qrzzzz | 2026-09-18 | OPEN |
| 35 | high | [GHSA-x4gw-5cx5-pgmh](https://github.com/advisories/GHSA-x4gw-5cx5-pgmh) | `io.netty:netty-handler` | N | 4.1.135.Final | Qrzzzz | 2026-09-18 | OPEN |
| 38 | high | [GHSA-c653-97m9-rcg9](https://github.com/advisories/GHSA-c653-97m9-rcg9) | `io.netty:netty-handler` | N | 4.1.135.Final | Qrzzzz | 2026-09-18 | OPEN |
| 43 | high | [GHSA-6jqx-86gh-f27w](https://github.com/advisories/GHSA-6jqx-86gh-f27w) | `io.netty:netty-codec-http` | N | 4.1.136.Final | Qrzzzz | 2026-09-18 | OPEN |
| 44 | high | [GHSA-mvh2-crg5-v77c](https://github.com/advisories/GHSA-mvh2-crg5-v77c) | `io.netty:netty-codec-http` | N | 4.1.136.Final | Qrzzzz | 2026-09-18 | OPEN |
| 45 | high | [GHSA-jppx-w49h-x2qq](https://github.com/advisories/GHSA-jppx-w49h-x2qq) | `io.netty:netty-codec-http` | N | 4.1.136.Final | Qrzzzz | 2026-09-18 | OPEN |
| 50 | high | [GHSA-558v-64gr-wgg4](https://github.com/advisories/GHSA-558v-64gr-wgg4) | `io.netty:netty-codec` | N | 4.1.136.Final | Qrzzzz | 2026-09-18 | OPEN |
| 52 | high | [GHSA-93wv-jw9v-4972](https://github.com/advisories/GHSA-93wv-jw9v-4972) | `io.netty:netty-codec-http2` | N | 4.1.136.Final | Qrzzzz | 2026-09-18 | OPEN |

## Lower-severity Maven backlog

The snapshot preserves all 27 medium and three low findings. Each following group is also assigned to **Qrzzzz**, status **OPEN**, due **2026-10-02**. This date does not defer the earlier high/critical deadline for a shared package family.

| Scope | Exact alert IDs | Remediation target |
| --- | --- | --- |
| S | 2 | jsoup 1.15.3 |
| S | 5, 6 | Guava Android variant at least 32.0.0-android, after accessibility compatibility checks |
| C | 7, 8 | Commons Compress 1.26.0 |
| BC | 23, 24 | Align bcpkix/bcprov and related BC artifacts to a compatible fixed family, candidate 1.84 |
| K | 53 | Track the Kotlin fixed line and validate AGP/KSP/Compose compatibility |
| N | 3, 9, 13, 14, 16, 17, 19, 25, 26, 27, 28, 31, 36, 37, 39, 40, 46, 47, 48, 49, 51, 54 | Align the relevant Netty host-tool configurations; candidate 4.1.137.Final |

## npm remediation in the current candidate

The [latest failed Dependabot job](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/33815529752) reports `security_update_not_possible`: the current override made the AJV dependency path continue to resolve `fast-uri` 3.1.5 even though 3.1.6 exists. It is a dependency constraint failure, not evidence that alerts or automated security fixes are disabled.

| Advisory | Source severity | Previous → candidate | Scope and status |
| --- | --- | --- | --- |
| [GHSA-5jgf-p345-68v8](https://github.com/advisories/GHSA-5jgf-p345-68v8), alert 55 | high | fast-uri 3.1.5 → 3.1.6 | AJV schema generator; fixed in candidate, default-branch alert closure awaits merge/scan |
| [GHSA-fph4-wmhf-6fwf](https://github.com/advisories/GHSA-fph4-wmhf-6fwf), alert 56 | high | fast-uri 3.1.5 → 3.1.6 | Same |
| [GHSA-f65p-4m7j-42xc](https://github.com/advisories/GHSA-f65p-4m7j-42xc), alert 57 | high | fast-uri 3.1.5 → 3.1.6 | Same |
| [GHSA-jqff-g426-hqxp](https://github.com/advisories/GHSA-jqff-g426-hqxp), alert 58 | high | fast-uri 3.1.5 → 3.1.6 | Same |
| [GHSA-c83g-rgw3-j3cx](https://github.com/advisories/GHSA-c83g-rgw3-j3cx) | high in npm audit/global GitHub advisory API | browserslist 4.28.5 → 4.28.8 | Babel/Vite development tool; fixed in candidate |
| [GHSA-73wf-gq98-2v4g](https://github.com/advisories/GHSA-73wf-gq98-2v4g) | high in npm audit/global GitHub advisory API | browserslist 4.28.5 → 4.28.8 | Same |

The two Browserslist advisories were returned by the live npm audit even though the repository alerts endpoint did not yet list them. Their upstream repository advisories display lower severity than the npm/global-advisory records; the gate uses and this record preserves the actual npm result. Both sources agree that 4.28.7 is patched. The candidate uses compatible patch 4.28.8 and its required browser-data dependencies. No React, Vite, Babel, TypeScript, Android, or Kotlin major version was upgraded.

## Local candidate validation

On 2026-09-04, using Node.js 24.14.1 and npm 11.11.0:

- `npm ci` installed the committed lockfile successfully.
- `npm audit --audit-level=high --json` returned **0 vulnerabilities**, after the original lockfile had returned two high-severity package findings. A transient audit endpoint connection failure was retained as a failed transport attempt; the subsequent complete audit succeeded.
- `npm run check` passed the standalone-validator consistency check, TypeScript checking, all **83 tests across eight test files**, and the production Vite build. No tracked generated renderer source changed.
- `scripts/test-dependency-security-contract.ps1` passed. The existing high/critical thresholds and full-SHA Action pins were preserved.
- A snapshot-to-table cross-check confirmed all 58 original alert records and exactly one owner/deadline/status row for each of the 23 distinct high/critical Maven GHSA/package items. Production runtime and compile scope checks matched zero alerted Maven package names.

This local validation does not close the Gradle alerts or replace the subsequent integration CI and final device gate.

## Completion evidence required for the follow-up issue

1. For each OPEN row, land a focused dependency PR or record a separately reviewed disposition with scope evidence. Link its exact commit and passing Renderer/JVM/lint/R8/APK/AAB gates; retain the device gate where host test tooling changes.
2. After each merge, verify a new successful Gradle submission on that exact SHA and re-query the alerts API. A green Dependency Review result only covers the dependency delta GitHub exposes; it does not by itself close this existing baseline.
3. Keep each unresolved advisory's owner, deadline, and next action current. Any temporary gate exception must follow `docs/DEPENDENCY_SECURITY.md`, including a public issue and expiry within 30 days; this baseline introduces no such exception.
4. Attach the final default-branch alert snapshot and confirm that no new or unresolved high/critical item lacks a tracked disposition. Do not report zero Maven vulnerabilities merely because the production runtime graph has no matching packages.
