# Android static renderer

This package is the offline React/CSS renderer embedded by the Android app at
`https://appassets.androidplatform.net/renderer/index.html`. Its production build is written to
`../app/src/main/assets/renderer`.

The native side sends protocol v1 envelopes through
`window.LyricsCardRenderer.receive(message)`. The renderer replies through the origin-filtered
`window.LyricsCardNative.postMessage(JSON.stringify(envelope))` bridge, or `parent.postMessage`
when running in a browser harness.

Supported requests are `initialize`, `setSpec`, `measure`, `extractPalette`, `exportPng`, `cancel`,
and `ping`. `exportPng` emits numbered `exportChunk` envelopes containing at most 384 KiB of raw
PNG data per Base64-encoded chunk, followed by an `exportCompleted` metadata envelope. The native
side validates request ownership, ordering, chunk and byte counts while streaming decoded bytes to
a temporary file, then validates the completed PNG before exposing it. A WebMessage ArrayBuffer
fast path remains a possible future memory and throughput optimization.

Run `npm run check` to type-check, test, and build. The build emits the frozen RenderSpec schema,
renderer manifest, local Source Han fonts, hashed application bundles, and `index.html`.
