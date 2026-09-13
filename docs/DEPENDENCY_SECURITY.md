# Dependency Security

This document separates repository-controlled dependency checks from GitHub repository settings. A configuration file in the repository is not evidence that a GitHub security feature is enabled, and a clean audit is only a time-bounded result.

## Repository-controlled coverage

- `.github/dependabot.yml` covers Renderer npm manifests in `/renderer`, the Gradle build in `/`, and GitHub Actions workflows in `/`. During the 1.1.2 backlog cleanup, all three ordinary version-update limits are temporarily zero. Security alerts and automatic security updates remain enabled; ordinary PR limits do not limit security PRs. React/React DOM/types and Vitest/mocker use separate version-update and security-update groups, without a global ignore rule. There is no auto-merge configuration.
- Restore ordinary updates after every historical PR has a final disposition: monthly, at most one ordinary PR per ecosystem, with compatible minor/patch groups. Major migrations belong in the manual maintenance queue; necessary security fixes can be advanced independently. The single [1.1.2 maintenance tracking issue #45](https://github.com/Qrzzzz/lyrics-card-generator-android/issues/45) records heads, checks, dispositions and follow-up ownership.
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

High and critical advisories fail the npm and pull-request gates. Low and moderate npm findings remain visible in command output but do not fail the build. The gates query live advisory services, so every result must be reported with its execution time and exact commit; a result of zero is not a permanent security guarantee and does not cover an ecosystem that was not successfully analyzed.

The `main` snapshot is sufficient for default-branch Gradle alerts. It is not evidence that every pull request's resolved Gradle delta was generated or reviewed: this workflow does not submit a separately resolved Gradle snapshot for each pull request. Dependency Review's PASS is limited to the dependency changes that GitHub actually exposes for that comparison. Until a pull-request snapshot path is separately implemented and directly validated, reports must preserve this limitation.

There is no standing advisory allowlist. A temporary exception requires a public tracking issue naming the GHSA, package/version and scope, owner, reason, compensating control, and an expiry date no more than 30 days away. Adding an `allow-ghsas` entry or weakening a threshold requires a separately reviewed commit that links that issue; expired exceptions fail review until removed or explicitly renewed.

Dependabot creates reviewable pull requests only. It does not auto-merge, receive production-signing secrets, run the `production-signing` environment, publish a release, or replace the release checklist. The dependency workflow has no signing environment, secret reference, tag, or release step. Diagnostic artifact uploads may use existing read permissions; they do not justify adding publication, OIDC or attestation permissions. External GitHub Actions remain pinned to full commit SHAs, including updates proposed by Dependabot; local references must resolve inside the repository.
