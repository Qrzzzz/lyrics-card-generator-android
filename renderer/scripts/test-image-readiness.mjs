import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';
import { preview } from 'vite';
const root = fileURLToPath(new URL('../', import.meta.url));
const spec = JSON.parse(readFileSync(new URL('../fixtures/landscape-short.json', import.meta.url), 'utf8'));
const server = await preview({ root, preview: { host: '127.0.0.1', port: 0 } });
let browser;
try {
  browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage();
  await page.addInitScript(() => { Object.defineProperty(HTMLImageElement.prototype, 'decode', { value: undefined }); });
  let release;
  await page.route('**/media/**', async route => {
    const id = route.request().url().split('/').pop();
    if (id === 'timeout') await new Promise(resolve => { release = resolve; });
    else await new Promise(resolve => setTimeout(resolve, 600));
    if (id === 'broken') return route.fulfill({status:404, body:'missing'});
    const [width,height] = id === 'wide' || id === 'timeout' ? [200,100] : id === 'tall' ? [100,200] : [100,100];
    await route.fulfill({contentType:'image/svg+xml',body:`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="red"/></svg>`});
  });
  await page.goto(server.resolvedUrls.local[0]);
  await dispatchRenderer(page,'initialize',{},'ready','init');
  const input = id => ({...spec,song:{...spec.song,coverAssetId:id}});
  const geometry = () => page.evaluate(() => {
    const card=document.querySelector('[data-export-card]');
    const slot=card.querySelector('.landscape-cover-slot');
    const img=card.querySelector('.cover-art img');
    return {width:card.offsetWidth,height:card.offsetHeight,aspect:parseFloat(slot.style.width)/parseFloat(slot.style.height),src:img?.getAttribute('src')};
  });
  for (const [id,aspect] of [['square',1],['wide',2],['tall',0.5],['broken',1],['timeout',1]]) {
    const start=Date.now();
    const {payload:measured}=await dispatchRenderer(page,'measure',input(id),'measured',`m-${id}`);
    const elapsed=Date.now()-start;
    assert.ok(elapsed<6500, `${id}: bounded wait ${elapsed}`);
    if (id !== 'timeout') assert.ok(elapsed>=550, `${id}: must wait for controlled load`);
    const before=await geometry();
    assert.ok(Math.abs(before.aspect-aspect)<0.01,JSON.stringify(before));
    assert.deepEqual([before.width,before.height],[measured.width,measured.height]);
    for (const scale of [1,1.4,2]) {
      const next={...input(id),canvas:{...spec.canvas,...measured}};
      const result=await dispatchRenderer(page,'exportPng',{spec:next,pixelRatio:scale},'exportCompleted',`e-${id}-${scale}`);
      const decoded=await page.evaluate(async chunks=>{
        const bytes=Uint8Array.from(atob(chunks.join('')),c=>c.charCodeAt(0));
        const image=await createImageBitmap(new Blob([bytes],{type:'image/png'}));
        const dims=[image.width,image.height];image.close();return dims;
      },result.chunks);
      assert.deepEqual(decoded,[Math.floor(measured.width*scale),Math.floor(measured.height*scale)]);
      assert.deepEqual(await geometry(),before,'export must preserve preview layout');
    }
    if(id==='timeout') {
      // A timed-out image remains a placeholder even when its original response arrives.
      release(); await page.waitForTimeout(100);
      assert.deepEqual(await geometry(),before);
    }
    console.log('PASS',id,elapsed,measured);
  }
  // Queue rapid replacements through the actual serialized host protocol.
  await dispatchRenderer(page,'measure',input('wide'),'measured','before-rapid');
  await page.evaluate(() => { window.oldCover=document.querySelector('.cover-art img'); });
  await Promise.all(['wide','tall','square'].map((id,i)=>dispatchRenderer(page,'measure',input(id),'measured',`rapid-${i}`)));
  const final=await geometry();
  await page.evaluate(()=> { window.oldCover?.dispatchEvent(new Event('error')); window.oldCover?.dispatchEvent(new Event('load')); });
  await page.waitForTimeout(100);
  assert.deepEqual(await geometry(),final);
  assert.equal(final.src,'../media/square');
  console.log('PASS rapid replacement and late events; decode unavailable throughout');
} finally { await browser?.close(); await new Promise(resolve=>server.httpServer.close(resolve)); }
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

