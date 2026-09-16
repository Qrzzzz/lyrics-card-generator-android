# 1.1.6 security and CI maintenance

Scope: maintenance plan Step 5–6; baseline main `a281698cc24f4cb008945fc5119102a98df701fa`, published v1.1.5. On 2026-09-16 the live API reported exactly three open medium alerts: Kotlin #53 and Commons Compress #7/#8. Original owner Qrzzzz and deadline 2026-10-02 remain unchanged until verified remediation; no advisory dismissal or threshold change.

## Dependency choices

- [Kotlin GHSA-r937-wjx7-w2jp](https://github.com/advisories/GHSA-r937-wjx7-w2jp) affects versions before 2.4.20-Beta1. Use [stable Kotlin 2.4.20](https://github.com/JetBrains/kotlin/releases/tag/v2.4.20), not a prerelease. Android, Compose compiler and serialization plugins share one version catalog entry. Runtime serialization stays 1.7.3; Room stays 2.8.4. KSP 2.3.12 is validated with the existing Room processor.
- [Android's Kotlin support table](https://developer.android.com/build/kotlin-support) requires R8 9.1.29 for Kotlin 2.4. Keep AGP 8.13.2 / Gradle 8.13 and override only the buildscript R8 dependency. Hosted builds continue JDK 17; the local ASCII wrapper uses installed JBR 21 with JVM target 17.
- Commons Compress #7/#8 both require 1.26.0. Add a buildscript-only constraint. The existing `verifyHostParserResolution` now rejects old Compress, Kotlin plugin/compiler or R8 components and checks host-parser isolation from production and AndroidTest compile/runtime. Build, shrink, lint and actual bundletool AAB reading provide separate compatibility evidence.

## Actions and scope review

The replacement patch includes all #57 pins: checkout 4.4.0, setup-java 4.9.1, Gradle setup/submission 4.4.4. Upload/download move to 7.0.1/8.0.1, with exact upstream tag commits. [Upload documentation](https://github.com/actions/upload-artifact/tree/v7.0.1) retains `archive: true` by default; do not use the new direct-file mode, which changes artifact naming. Candidate/test APK groups, cross-run downloads and publication receipt retain their existing names and 90-day retention. Metadata, individual asset hashes, certificate continuity and attestations remain independently verified before publication.

CI already routes ordinary PRs by scope, reuses same-source main checks for release, and keeps signing-time audit/JVM/lint/build verification. No demonstrably redundant check was removed. Dependency Review still covers GitHub's visible diff only, while the main submission supplies the resolved Gradle graph.

## Evidence

- PASS at implementation commit `38423ca`: Node CI/publish tests 28/28; dependency-security, production-release, frozen-source and device-evidence contracts. Device fixtures are not real-device evidence.
- Local compatibility, same-source hosted CI, dependency submission, live fixed-state confirmation and signed publication are recorded below when completed.
- NOT RUN: this version's physical-device manual actions and extended device matrix. Historical failures and 1.1.5 API 30 results are retained without reinterpretation.

#35 closes only after current default-branch submission and alert state confirm remediation. #45 stays open for 1.1.7 runtime/Renderer work; #56 is outside this patch.
