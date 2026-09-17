import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';
import { preview } from 'vite';
const root = fileURLToPath(new URL('../', import.meta.url));
const spec = JSON.parse(readFileSync(new URL('../fixtures/landscape-short.json', import.meta.url), 'utf8'));
const server = await preview({ root, server: { host: '127.0.0.1' }, preview: { host: '127.0.0.1', port: 0 } });
let browser;
try {
  browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage();
  await page.goto(server.resolvedUrls.local[0]);
  await page.waitForSelector('[data-export-card="true"]');
  await dispatchRenderer(page, 'initialize', {}, 'ready', 'init');
  const { payload: measured } = await dispatchRenderer(page, 'measure', spec, 'measured', 'measure');
  assert.ok(measured.height >= 640 && measured.height < 720, JSON.stringify(measured));
  console.log('Short landscape measured:', measured);
  for (const format of ['png', 'webp', 'jpg']) for (const scale of [1, 1.4, 2]) {
    const mime = format === 'jpg' ? 'image/jpeg' : `image/${format}`;
    const next = { ...spec, canvas: { ...spec.canvas, ...measured, exportFormat: format, exportScale: scale } };
    await dispatchRenderer(page, 'setSpec', next, 'specApplied', `apply-${format}-${scale}`);
    const result = await dispatchRenderer(page, 'exportPng', { spec: next, pixelRatio: scale }, 'exportCompleted', `${format}-${scale}`);
    const expected = [Math.floor(measured.width * scale), Math.floor(measured.height * scale)];
    assert.deepEqual([result.payload.width, result.payload.height], expected);
    assert.equal(result.payload.mimeType, mime);
    const decoded = await page.evaluate(async ({ chunks, mime }) => {
      const bytes = Uint8Array.from(atob(chunks.join('')), c => c.charCodeAt(0));
      const bitmap = await createImageBitmap(new Blob([bytes], { type: mime }));
      const size = [bitmap.width, bitmap.height]; bitmap.close(); return size;
    }, { chunks: result.chunks, mime });
    assert.deepEqual(decoded, expected);
    console.log(`PASS ${format} ${scale}x ${decoded.join('x')}`);
  }
} finally {
  await browser?.close(); await new Promise(resolve => server.httpServer.close(resolve));
}

async function dispatchRenderer(page, type, payload, terminalType, requestId) {
  return page.evaluate(
    ({ type: messageType, payload: messagePayload, terminalType: terminal, requestId: id }) =>
      new Promise((resolvePromise, rejectPromise) => {
        const chunks = [];
        const timeout = window.setTimeout(() => {
          cleanup();
          rejectPromise(new Error(`${id} timed out`));
        }, 30_000);
        const listener = (event) => {
          const message = event.detail;
          if (!message || message.requestId !== id) return;
          if (message.type === "exportChunk") chunks[message.payload.index] = message.payload.base64;
          if (message.type === "renderError") {
            cleanup();
            rejectPromise(new Error(`${id} failed with ${message.payload.code}`));
            return;
          }
          if (message.type === terminal) {
            cleanup();
            resolvePromise({ payload: message.payload, chunks });
          }
        };
        const cleanup = () => {
          window.clearTimeout(timeout);
          window.removeEventListener("lyrics-card-renderer-message", listener);
        };
        window.addEventListener("lyrics-card-renderer-message", listener);
        window.LyricsCardRenderer.receive({
          protocolVersion: 1,
          requestId: id,
          type: messageType,
          payload: messagePayload
        });
      }),
    { type, payload, terminalType, requestId }
  );
}

