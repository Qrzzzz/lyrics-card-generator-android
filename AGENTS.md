# Working agreements

- Start from current main and inspect the working tree. Preserve existing work and keep one active human work branch/PR; merge before starting the next independent patch.
- Keep each patch within its stated scope. Do not bundle unrelated dependency majors, product redesigns or cleanup into a fix.
- Use [docs/CI.md](docs/CI.md) for applicable checks and evidence categories. Report PASS, FAIL, BLOCKED, NOT RUN or NOT APPLICABLE with the commit and command/run. Separate product failures from tooling, services, baseline issues and missing devices/permissions.
- Tests protect behavior and security boundaries, not display names or file layout. Replace brittle assertions with equivalent positive/negative coverage; do not remove correctness/security checks merely to get green results.
- Preserve package identity, production signing identity, saved-project compatibility and the RenderSpec → local WebView → React/CSS → PNG path. `android-alpha-renderer-1` is a persistent contract identifier.
- Use only the existing `focused-manual-v1` release path in [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md). From 1.1.5 physical-device manual checks are optional; preserve CI, signing and provenance checks. Do not fabricate manual/device evidence, rewrite historical failures, or infer production readiness from PR checks.
- Keep dependency risks in the existing tracking issues with original severity/deadline and a concrete next action. Agent work does not waive unresolved release risks.
- Bound exploratory command output and retain exit status when it matters. Inspect narrower ranges after truncation. Verify resolved target paths before removing generated files, and preserve work that cannot safely be classified.
