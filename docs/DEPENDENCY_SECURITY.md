# Dependency Security

This document separates repository-controlled dependency checks from GitHub repository settings. A configuration file in the repository is not evidence that a GitHub security feature is enabled, and a clean audit is only a time-bounded result.

## Repository-controlled coverage

- `.github/dependabot.yml` schedules weekly update pull requests for Renderer npm manifests in `/renderer`, the Gradle build in `/`, and GitHub Actions workflows in `/`. Each ecosystem is limited to five open version-update pull requests. There is no auto-merge configuration.
- `npm run audit:security` audits the complete locked Renderer tree, including development dependencies. Normal CI and the manually dispatched production-candidate workflow fail when npm reports a high or critical advisory.
- Pull requests run GitHub Dependency Review for runtime, development, and unknown scopes. It rejects high or critical advisories that are visible in GitHub's dependency diff; license policy and OpenSSF score warnings are deliberately outside this gate.
- Each push to `main` resolves the Gradle build and submits its dependency graph with the pinned Gradle action. Submission failure fails the job. After the repository feature is enabled, this default-branch snapshot supports alerts for resolved Gradle dependencies.
- `scripts/test-dependency-security-contract.ps1` checks ecosystem/directory coverage, audit wiring, narrow permissions, full-SHA Action pins, and the absence of signing, secret, or release-artifact capability in the dependency workflow.

The Gradle version catalog pins direct versions, but this repository does not currently commit Gradle dependency lock files or dependency-verification metadata. Dependency submission records the resolved graph; it does not turn the catalog into a transitive lock or prove artifact integrity.

## GitHub settings required after merge

An administrator must still enable the Dependency Graph, Dependabot alerts, and Dependabot security updates in the repository's code-security settings. The Actions policy must also allow the `submit-gradle-dependencies` job's narrowly scoped `GITHUB_TOKEN` to write dependency snapshots. These are repository settings and are not enabled by this commit.

Dependency Review fails while Dependency Graph is disabled. An administrator must therefore complete the settings steps below **before this branch is pushed and before a pull request is opened**; merging the files first is not an activation path.

Activation order:

1. Enable Dependency Graph and Dependabot alerts, then Dependabot security updates.
2. Ensure Actions may grant `contents: write` to the Gradle submission job. No PAT or new secret is required.
3. Only after steps 1–2, push the branch and open the pull request. Confirm Dependency Review starts with Dependency Graph available rather than failing for missing repository capability.
4. After merge, confirm the resulting `Dependency Security` push run submits a Gradle snapshot and the SBOM/dependency graph includes Gradle packages.
5. Confirm the Dependabot alerts API is readable and record the initial open high/critical baseline. Zero is acceptable; every existing high/critical alert must otherwise have a remediation issue with an owner and deadline.
6. On a dependency-changing pull request, confirm Dependency Review plus the existing Renderer/JVM/lint/R8/APK/AAB quality gates run on the same candidate.

At the implementation check on 2026-08-24 (Asia/Shanghai), the alerts API returned `403 Dependabot alerts are disabled for this repository`, automated security fixes reported `enabled: false`, and the dependency-graph SBOM endpoint returned `404`. Therefore this commit must not be described as having enabled alerts or security updates.

## Thresholds, exceptions, and evidence

High and critical advisories fail the npm and pull-request gates. Low and moderate npm findings remain visible in command output but do not fail the build. The gates query live advisory services, so every result must be reported with its execution time and exact commit; a result of zero is not a permanent security guarantee and does not cover an ecosystem that was not successfully analyzed.

The `main` snapshot is sufficient for default-branch Gradle alerts. It is not evidence that every pull request's resolved Gradle delta was generated or reviewed: this workflow does not submit a separately resolved Gradle snapshot for each pull request. Dependency Review's PASS is limited to the dependency changes that GitHub actually exposes for that comparison. Until a pull-request snapshot path is separately implemented and directly validated, reports must preserve this limitation.

There is no standing advisory allowlist. A temporary exception requires a public tracking issue naming the GHSA, package/version and scope, owner, reason, compensating control, and an expiry date no more than 30 days away. Adding an `allow-ghsas` entry or weakening a threshold requires a separately reviewed commit that links that issue; expired exceptions fail review until removed or explicitly renewed.

Dependabot creates reviewable pull requests only. It does not auto-merge, receive production-signing secrets, run the `production-signing` environment, publish a release, or replace the release checklist. The dependency workflow has no signing environment, secret reference, artifact upload, tag, or release step. GitHub Actions remain pinned to full commit SHAs, including updates proposed by Dependabot.
