# Renderer pixel Golden gate

This gate renders 30 audited `RenderSpec` cases through the real local Renderer
protocol and `exportPng` implementation. It does not treat JSON, HTML, or a
source hash as a pixel result.

Reference environment:

- Windows runner (`win32`, matching this repository's CI runner family)
- exact `@playwright/test` version from `package-lock.json`
- Playwright-managed Chromium
- viewport `1280x960`, device scale factor `1`, UTC, `en-US`, sRGB
- bundled Source Han Sans/Serif OTF files only for the required sans/serif cases
- all non-loopback requests blocked; `golden-cover.png` is fulfilled with a
  deterministic locally generated PNG

Run:

```powershell
npm ci
npm run golden:install
npm run golden:test
```

The comparator checks dimensions, a per-channel pixel delta, mismatch ratio,
and SSIM. Failing actual/diff images go to the ignored `golden/diffs` folder.

Baseline updates are intentionally gated and never happen during a normal test:

```powershell
$env:RENDERER_GOLDEN_UPDATE='reviewed'
npm run golden:update
Remove-Item Env:RENDERER_GOLDEN_UPDATE
```

Review the Renderer source fingerprint, fixture fingerprint, generated PNGs,
and manifest diff independently before accepting a baseline update.
