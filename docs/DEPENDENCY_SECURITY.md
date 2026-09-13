# Dependency Security

This document separates repository-controlled dependency checks from GitHub repository settings. A configuration file in the repository is not evidence that a GitHub security feature is enabled, and a clean audit is only a time-bounded result.

## Repository-controlled coverage

- `.github/dependabot.yml` covers Renderer npm manifests in `/renderer`, the Gradle build in `/`, and GitHub Actions workflows in `/`. Ordinary version updates run monthly, with at most one open ordinary PR per ecosystem and compatible minor/patch groups. Ecosystem-level [`allow.update-types`](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference#update-types--allow--) limits ordinary updates to compatible minor/patch releases; GitHub documents that this option affects version updates, not security updates. React/React DOM/types and Vitest/mocker retain separate version-update and security-update groups, without a global ignore rule or auto-merge configuration.
- All 16 historical update PRs now have a final closed disposition. The newer Guava update [#51](https://github.com/Qrzzzz/lyrics-card-generator-android/pull/51) remains open for independent review. The [1.1.2 maintenance tracking issue #45](https://github.com/Qrzzzz/lyrics-card-generator-android/issues/45) records heads, checks, dispositions and follow-up ownership; major migrations stay in the manual maintenance queue.
- `npm run audit:security` audits the complete locked Renderer tree, including development dependencies. Normal CI and the manually dispatched production-candidate workflow fail when npm reports a high or critical advisory. Installation uses `npm ci --no-audit --no-fund`; a separate audit wrapper performs the one explicit audit. It retries only recognized transient service/network errors, at most three attempts with 30-second request timeouts and a 75-second process bound per attempt. High/critical findings fail immediately; unavailable, malformed or unauthorized reports remain failures, never PASS.
- Pull requests run GitHub Dependency Review for runtime, development, and unknown scopes. It rejects high or critical advisories that are visible in GitHub's dependency diff; license policy and OpenSSF score warnings are deliberately outside this gate.
- Each push to `main` resolves the Gradle build and submits its dependency graph with the pinned Gradle action. Submission failure fails the job. After the repository feature is enabled, this default-branch snapshot supports alerts for resolved Gradle dependencies.
- `scripts/test-dependency-security-contract.ps1` runs semantic YAML checks and positive/negative fixtures for ecosystem/directory coverage, audit wiring, narrow permissions and full-SHA external Action pins. Field/scopes order, `npm` versus `npm.cmd`, and step display names are not security boundaries. Valid repository-local Actions and pinned diagnostic uploads are allowed; signing, secrets and publication privileges remain forbidden in the dependency workflow.

The Gradle version catalog pins direct versions, but this repository does not currently commit Gradle dependency lock files or dependency-verification metadata. Dependency submission records the resolved graph; it does not turn the catalog into a transitive lock or prove artifact integrity.

## GitHub activation and baseline

Dependency Graph, Dependabot alerts, and Dependabot security updates must be enabled in the repository's code-security settings. The Actions policy must also allow the `submit-gradle-dependencies` job's narrowly scoped `GITHUB_TOKEN` to write dependency snapshots. These settings are separate from the committed configuration.

Dependency Review fails while Dependency Graph is disabled. An administrator must therefore complete the settings steps below **before this branch is pushed and before a pull request is opened**; merging the files first is not an activation path.

Activation order:

1. Enable Dependency Graph and Dependabot alerts, then Dependabot security updates.
2. Ensure Actions may grant `contents: write` to the Gradle submission job. No PAT or new secret is required.
3. Only after steps 1–2, push the branch and open the pull request. Confirm Dependency Review starts with Dependency Graph available rather than failing for missing repository capability.
4. After merge, confirm the resulting `Dependency Security` push run submits a Gradle snapshot and the SBOM/dependency graph includes Gradle packages.
5. Confirm the Dependabot alerts API is readable and record the initial open high/critical baseline. Zero is acceptable; every existing high/critical alert must otherwise have a remediation issue with an owner and deadline.
6. On a dependency-changing pull request, confirm Dependency Review plus the existing Renderer/JVM/lint/R8/APK/AAB quality gates run on the same candidate.

The initial implementation check on 2026-08-24 preceded activation and returned disabled/404 responses. A new API check on **2026-09-04** confirmed that alerts and the SBOM are readable, and automated security fixes report `enabled: true` and `paused: false`. The [default-branch submission run](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/32768030346) succeeded on `bdd93a074ba577e9f2de230515052eb69c7e13d2`; the queried SBOM contains 474 Maven and 167 npm packages. Dependabot also opened update PRs in all three configured ecosystems.

The [2026-09-04 baseline](security/dependency-baseline-2026-09-04.md) records all 58 alerts observed in the initial snapshot, including the 24 high/critical Maven records, their scopes, owners, and deadlines. Public tracking issue [#35](https://github.com/Qrzzzz/lyrics-card-generator-android/issues/35), assigned to Qrzzzz, contains the unresolved toolchain items and their required validation. No advisory was dismissed and no gate threshold was relaxed.

The same investigation found a blocked `fast-uri` security update and additional live npm `browserslist` findings. The renderer now locks `fast-uri` 3.1.6 and `browserslist` 4.28.8. Keep live npm auditing enabled even when the GitHub alerts list has not yet reported the same advisory.

## Thresholds, exceptions, and evidence

### Bouncy Castle host dependency remediation (1.1.2)

The original critical remediation/review deadline in #35 remains **2026-09-11** (overdue at the 2026-09-13 review). AGP 8.13.2 resolved the BC 1.79 family in its buildscript classpath; Robolectric 4.16.1 resolved bcprov 1.81 in host JVM tests. These are separate configurations. Updating AndroidX instrumentation dependencies does not repair either path, and absence from app runtime is not a host-tool safety exemption.

The narrowly scoped candidate imports the regular `bc-jdk18on-bom:1.84` platform in the root buildscript classpath and `testImplementation`. It does not force unrelated configurations or upgrade AGP, Gradle or Robolectric. [BC's upstream notice](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902025%E2%80%9014813) identifies 1.84 as a fixed line; this family also covers the recorded BC medium findings. The [published BOM](https://repo.maven.apache.org/maven2/org/bouncycastle/bc-jdk18on-bom/1.84/bc-jdk18on-bom-1.84.pom) aligns bcprov, bcpkix and bcutil.

`./gradlew.bat :app:verifyBouncyCastleResolution --no-configuration-cache --console=plain` checks selected dependency components, rather than matching dependency-report prose: fixed and aligned host families, and no BC runtime artifacts introduced into the app or instrumentation APK. JVM/lint/R8/APK/AAB and bundletool/signing-path checks provide separate compatibility evidence. The same-SHA default-branch dependency submission and subsequent alert scan determine whether GitHub's affected paths have disappeared. #35 remains open for other unresolved families; no critical/high risk is waived by this constraint or by a green build.

After [PR #47](https://github.com/Qrzzzz/lyrics-card-generator-android/pull/47) merged as `997b1c26ccf3d931371b07d103c6c0f7285692f6`, [dependency submission 34768105548](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/34768105548) succeeded on that SHA. The 2026-09-14 check confirmed BC alerts #41/#42 (critical) and #23/#24 (medium) were `fixed`, with `fixed_at=2026-09-13T16:17:42Z`. This records actual remediation after the original deadline, not a renewed exception. Other #35 families remain independently tracked.

### Netty host dependency remediation (1.1.2)

The 2026-09-14 live review found Netty in AGP 8.13.2's buildscript (gRPC 1.69.1, Netty 4.1.110) and three UTP host configurations (gRPC 1.69.1/1.57.2, Netty 4.1.110/4.1.93). New critical alert #63, [GHSA-c4c3-7fpv-j4q5](https://github.com/advisories/GHSA-c4c3-7fpv-j4q5), concerns fragmented TLS ClientHello falling back to a default SSL context; it is not proof that this app exposes that server configuration. Absence from app runtime does not waive host risk. Existing high deadlines remain **2026-09-18** and the BC deadline remains **2026-09-11**; no old date is reset by this work.

A regular Netty BOM aligns the complete 4.1 family to **4.1.138.Final** in the root buildscript and the declarable parent of UTP's internal configurations. [Upstream identifies 4.1.138 as a security release](https://netty.io/news/2026/09/09/4-1-138-Final.html); it includes the repository's 4.1.137 minimum fixes while retaining the 4.1 line. No app/test implementation dependency, AGP upgrade, forced resolution, or 4.2 migration is introduced.

`:app:verifyNettyResolution` rejects unresolved graphs, old or misaligned Netty families, missing expected host roots, and Netty leakage into production/test APK or bundletool configurations. Main/full CI runs it before the existing build gates. Successful graph resolution and APK/AAB/test APK packaging establish host build compatibility, not UTP execution. The current local check found no connected adb device and no AVD: `connectedProductionReleaseAndroidTest` remains **NOT RUN**. Direct `adb am instrument` of an existing APK also does not exercise AGP's UTP host runner. This gap and any remaining #35 risks require explicit maintainer disposition before publication; no exception or alert dismissal is created here. Same-SHA dependency submission and the live alert API provide separate remediation evidence after merge.

After [PR #50](https://github.com/Qrzzzz/lyrics-card-generator-android/pull/50) merged as `d3fb19bbf50429458ad37982cd19ccbfbb7aa742`, [dependency submission 34772572936](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/34772572936) succeeded. The subsequent live API reported all **41 Netty alerts fixed**, including critical #63 at `2026-09-13T17:46:49Z`. Thirteen other alerts remained (5 high, 7 medium, 1 low). Fixed dependency versions do not supply the missing UTP/device execution evidence described above.

### JDOM and jose4j host parser remediation (1.1.2)

AGP's Jetifier path selected JDOM 2.0.6, and both AGP and the dedicated bundletool 1.18.1 CLI selected jose4j 0.9.5. Configuration-local constraints select JDOM **2.0.6.1** in the buildscript and jose4j **0.9.6** in those two host roots, without adding runtime or instrumentation dependencies. [JDOM's patch release](https://github.com/hunterhacker/jdom/releases/tag/JDOM-2.0.6.1) addresses XML external entity handling; jose4j's [GHSA-3677-xxcr-wjqv](https://github.com/advisories/GHSA-3677-xxcr-wjqv) fixes compressed JWE resource exhaustion. The original **2026-09-18** high deadline remains unchanged.

`:app:verifyHostParserResolution` rejects missing, old or unresolved host dependencies and product-scope leakage. Full CI also executes `:app:dumpProductionReleaseBundleManifest`, so the constrained bundletool must actually read the generated AAB. Resolution/build/manifest evidence and the subsequent same-SHA dependency submission remain separate; no advisory is dismissed by this patch. Protobuf in bundletool/UTP and jsoup in accessibility instrumentation are deferred to the existing #35 tasks with their device validation gaps, owner and original deadlines intact.

After [PR #52](https://github.com/Qrzzzz/lyrics-card-generator-android/pull/52) merged as `7445d36763be3dc0aafe2903c26051f340405626`, [dependency submission 34773687336](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/34773687336) succeeded. The live API reported JDOM alert #18 and jose4j alert #20 fixed, with `fixed_at=2026-09-13T18:08:47Z`.

### Vitest development dependency remediation (1.1.2)

Vitest and its coupled `@vitest/*` packages now resolve to **4.1.11**, including `@vitest/mocker`; the existing Vite 6.4.3 satisfies the declared peer range. [PR #53](https://github.com/Qrzzzz/lyrics-card-generator-android/pull/53) merged as `8ba1065fb9367e8b25faaee6391041f6d6804d66`, and [dependency submission 34774694837](https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/34774694837) succeeded. The live API reported alerts #59 and #61 fixed, with `fixed_at=2026-09-13T18:27:51Z`. This development-tool update does not change the embedded Renderer protocol.

That check left **9 open alerts: 3 high, 5 medium and 1 low**. The remaining high protobuf and jsoup records stay assigned to #35 with their device validation requirements and original **2026-09-18** deadline; this maintenance record does not waive them.

High and critical advisories fail the npm and pull-request gates. Low and moderate npm findings remain visible in command output but do not fail the build. The gates query live advisory services, so every result must be reported with its execution time and exact commit; a result of zero is not a permanent security guarantee and does not cover an ecosystem that was not successfully analyzed.

The `main` snapshot is sufficient for default-branch Gradle alerts. It is not evidence that every pull request's resolved Gradle delta was generated or reviewed: this workflow does not submit a separately resolved Gradle snapshot for each pull request. Dependency Review's PASS is limited to the dependency changes that GitHub actually exposes for that comparison. Until a pull-request snapshot path is separately implemented and directly validated, reports must preserve this limitation.

There is no standing advisory allowlist. A temporary exception requires a public tracking issue naming the GHSA, package/version and scope, owner, reason, compensating control, and an expiry date no more than 30 days away. Adding an `allow-ghsas` entry or weakening a threshold requires a separately reviewed commit that links that issue; expired exceptions fail review until removed or explicitly renewed.

Dependabot creates reviewable pull requests only. It does not auto-merge, receive production-signing secrets, run the `production-signing` environment, publish a release, or replace the release checklist. The dependency workflow has no signing environment, secret reference, tag, or release step. Diagnostic artifact uploads may use existing read permissions; they do not justify adding publication, OIDC or attestation permissions. External GitHub Actions remain pinned to full commit SHAs, including updates proposed by Dependabot; local references must resolve inside the repository.
