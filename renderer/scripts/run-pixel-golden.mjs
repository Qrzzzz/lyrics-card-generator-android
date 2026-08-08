import { createHash } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync
} from "node:fs";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import pixelmatch from "pixelmatch";
import { chromium } from "@playwright/test";
import { PNG } from "pngjs";
import { ssim } from "ssim.js";
import { build, preview } from "vite";

const rendererRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const goldenRoot = resolve(rendererRoot, "golden");
const referenceRoot = resolve(goldenRoot, "reference");
const diffRoot = resolve(goldenRoot, "diffs");
const manifestPath = resolve(goldenRoot, "manifest.json");
const matrixPath = resolve(goldenRoot, "cases.json");
const update = process.argv.includes("--update");
const thresholds = Object.freeze({ pixelmatchThreshold: 0.02, maxMismatchRatio: 0.0005, minSsim: 0.9995 });
const environment = Object.freeze({
  platform: "win32",
  viewport: { width: 1280, height: 960 },
  deviceScaleFactor: 1,
  locale: "en-US",
  timezoneId: "UTC",
  colorScheme: "light",
  reducedMotion: "reduce",
  chromiumArgs: ["--disable-lcd-text", "--font-render-hinting=none", "--force-color-profile=srgb"]
});

if (process.platform !== environment.platform) {
  throw new Error(`Pixel Golden requires ${environment.platform}; current platform is ${process.platform}`);
}
if (update && process.env.RENDERER_GOLDEN_UPDATE !== "reviewed") {
  throw new Error("Refusing baseline update: set RENDERER_GOLDEN_UPDATE=reviewed after inspecting Renderer scope");
}

const matrix = readJson(matrixPath);
const baseSpec = readJson(resolve(rendererRoot, matrix.baseFixture));
const cases = matrix.cases.map((entry) => ({
  ...entry,
  spec: deepMerge(baseSpec, entry.overrides)
}));
const sourceFingerprint = hashTree([
  resolve(rendererRoot, "src"),
  resolve(rendererRoot, "schema"),
  resolve(rendererRoot, "index.html"),
  resolve(rendererRoot, "renderer-manifest.json"),
  resolve(rendererRoot, "public", "fonts")
]);
const fixtureFingerprint = sha256(Buffer.concat([
  readFileSync(matrixPath),
  readFileSync(resolve(rendererRoot, matrix.baseFixture))
]));

let previewServer;
let browser;
try {
  await build({ configFile: resolve(rendererRoot, "vite.config.ts"), logLevel: "error" });
  previewServer = await preview({
    configFile: resolve(rendererRoot, "vite.config.ts"),
    logLevel: "error",
    preview: { host: "127.0.0.1", port: 0, strictPort: false }
  });
  const baseUrl = previewServer.resolvedUrls?.local?.[0];
  if (!baseUrl) throw new Error("Vite preview did not expose a loopback URL");

  browser = await chromium.launch({ headless: true, args: environment.chromiumArgs });
  const browserVersion = browser.version();
  const expectedManifest = update ? null : readJson(manifestPath);
  if (expectedManifest) {
    assertEqual(expectedManifest.datasetVersion, matrix.datasetVersion, "dataset version");
    assertEqual(expectedManifest.browserVersion, browserVersion, "Chromium version");
    assertEqual(expectedManifest.sourceFingerprint, sourceFingerprint, "Renderer source fingerprint");
    assertEqual(expectedManifest.fixtureFingerprint, fixtureFingerprint, "fixture fingerprint");
    assertEqual(JSON.stringify(expectedManifest.environment), JSON.stringify(environment), "reference environment");
    assertEqual(JSON.stringify(expectedManifest.thresholds), JSON.stringify(thresholds), "comparison thresholds");
  }

  const context = await browser.newContext({
    viewport: environment.viewport,
    deviceScaleFactor: environment.deviceScaleFactor,
    locale: environment.locale,
    timezoneId: environment.timezoneId,
    colorScheme: environment.colorScheme,
    reducedMotion: environment.reducedMotion,
    serviceWorkers: "block"
  });
  const page = await context.newPage();
  const cover = createDeterministicCover();
  const allowedOrigin = new URL(baseUrl).origin;
  await page.route("**/*", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith("/media/golden-cover.png")) {
      await route.fulfill({ status: 200, contentType: "image/png", body: cover });
      return;
    }
    if (url.origin === allowedOrigin) {
      await route.continue();
      return;
    }
    await route.abort("blockedbyclient");
  });
  await page.goto(baseUrl, { waitUntil: "networkidle" });
  await page.waitForSelector("[data-export-card='true']");
  await dispatchRenderer(page, "initialize", {}, "ready", "golden-init");

  if (update) {
    mkdirSync(referenceRoot, { recursive: true });
  } else {
    rmSync(diffRoot, { recursive: true, force: true });
  }

  const outputs = [];
  const failures = [];
  for (const entry of cases) {
    let spec = structuredClone(entry.spec);
    if (spec.canvas.autoHeight) {
      const measured = await dispatchRenderer(page, "measure", { spec }, "measured", `measure-${entry.id}`);
      spec.canvas.height = measured.payload.height;
    }
    const result = await dispatchRenderer(
      page,
      "exportPng",
      { spec, pixelRatio: spec.canvas.pixelRatio },
      "exportCompleted",
      `export-${entry.id}`
    );
    if (spec.canvas.autoHeight) {
      const geometry = await page.evaluate(() => {
        const card = document.querySelector("[data-export-card='true']");
        const viewport = document.querySelector("[data-card-lyrics-viewport='true']");
        const lyrics = document.querySelector("[data-card-lyrics='true']");
        const content = document.querySelector("[data-card-content='true']");
        const header = document.querySelector("[data-card-header='true']");
        const finalLine = lyrics?.querySelector(".lyric-pair:last-child > :last-child");
        const footer = document.querySelector("[data-card-footer='true']");
        if (!(card instanceof HTMLElement) || !(viewport instanceof HTMLElement) ||
            !(lyrics instanceof HTMLElement) || !(finalLine instanceof HTMLElement)) {
          throw new Error("Auto-height geometry nodes are unavailable");
        }
        return {
          cardBottom: card.getBoundingClientRect().bottom,
          viewportBottom: viewport.getBoundingClientRect().bottom,
          lyricsBottom: lyrics.getBoundingClientRect().bottom,
          finalLineBottom: finalLine.getBoundingClientRect().bottom,
          footerTop: footer instanceof HTMLElement ? footer.getBoundingClientRect().top : null,
          footerBottom: footer instanceof HTMLElement ? footer.getBoundingClientRect().bottom : null,
          lyricsClientHeight: lyrics.clientHeight,
          lyricsScrollHeight: lyrics.scrollHeight,
          contentPadding: content instanceof HTMLElement
            ? `${getComputedStyle(content).paddingTop}/${getComputedStyle(content).paddingBottom}`
            : null,
          headerHeight: header instanceof HTMLElement ? header.scrollHeight : null,
          viewportPadding: `${getComputedStyle(viewport).paddingTop}/${getComputedStyle(viewport).paddingBottom}`,
          viewportClientHeight: viewport.clientHeight,
          footerHeight: footer instanceof HTMLElement ? footer.scrollHeight : null
        };
      });
      const clipped = geometry.lyricsScrollHeight > geometry.lyricsClientHeight + 2 ||
        geometry.finalLineBottom > geometry.viewportBottom + 1 ||
        (geometry.footerTop !== null && geometry.finalLineBottom > geometry.footerTop + 1) ||
        (geometry.footerBottom !== null && geometry.footerBottom > geometry.cardBottom + 1);
      if (clipped) {
        throw new Error(`${entry.id}: auto-height content is clipped ${JSON.stringify(geometry)}`);
      }
    }
    const actual = Buffer.concat(result.chunks.map((chunk) => Buffer.from(chunk, "base64")));
    const actualPng = PNG.sync.read(actual);
    const expectedWidth = spec.canvas.width * spec.canvas.pixelRatio;
    const expectedHeight = spec.canvas.height * spec.canvas.pixelRatio;
    if (actualPng.width !== expectedWidth || actualPng.height !== expectedHeight) {
      throw new Error(`${entry.id}: expected ${expectedWidth}x${expectedHeight}, got ${actualPng.width}x${actualPng.height}`);
    }

    const outputPath = resolve(referenceRoot, `${entry.id}.png`);
    const record = {
      id: entry.id,
      width: actualPng.width,
      height: actualPng.height,
      pixelRatio: spec.canvas.pixelRatio,
      bytes: actual.length,
      sha256: sha256(actual)
    };
    outputs.push(record);

    if (update) {
      writeFileSync(outputPath, actual);
      process.stdout.write(`UPDATED ${entry.id} ${record.width}x${record.height} ${record.sha256}\n`);
      continue;
    }
    if (!existsSync(outputPath)) {
      failures.push(`${entry.id}: missing reference PNG`);
      continue;
    }
    const expectedBytes = readFileSync(outputPath);
    const expectedPng = PNG.sync.read(expectedBytes);
    if (expectedPng.width !== actualPng.width || expectedPng.height !== actualPng.height) {
      failures.push(`${entry.id}: reference dimensions ${expectedPng.width}x${expectedPng.height} != actual ${actualPng.width}x${actualPng.height}`);
      continue;
    }
    const diff = new PNG({ width: actualPng.width, height: actualPng.height });
    const mismatched = pixelmatch(
      expectedPng.data,
      actualPng.data,
      diff.data,
      actualPng.width,
      actualPng.height,
      { threshold: thresholds.pixelmatchThreshold, includeAA: false }
    );
    const mismatchRatio = mismatched / (actualPng.width * actualPng.height);
    const similarity = ssim(expectedPng, actualPng, { ssim: "fast" }).mssim;
    const exact = sha256(expectedBytes) === record.sha256;
    if (mismatchRatio > thresholds.maxMismatchRatio || similarity < thresholds.minSsim) {
      mkdirSync(diffRoot, { recursive: true });
      writeFileSync(resolve(diffRoot, `${entry.id}-actual.png`), actual);
      writeFileSync(resolve(diffRoot, `${entry.id}-diff.png`), PNG.sync.write(diff));
      failures.push(`${entry.id}: mismatchRatio=${mismatchRatio.toFixed(8)} ssim=${similarity.toFixed(8)}`);
    } else {
      process.stdout.write(`PASS ${entry.id} exact=${exact} mismatchRatio=${mismatchRatio.toFixed(8)} ssim=${similarity.toFixed(8)}\n`);
    }
  }

  if (update) {
    const manifest = {
      datasetVersion: matrix.datasetVersion,
      browserVersion,
      sourceFingerprint,
      fixtureFingerprint,
      environment,
      thresholds,
      outputs
    };
    writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
  } else {
    const expectedOutputs = new Map(expectedManifest.outputs.map((entry) => [entry.id, entry]));
    for (const output of outputs) {
      const expected = expectedOutputs.get(output.id);
      if (!expected) failures.push(`${output.id}: missing manifest output record`);
      else if (expected.width !== output.width || expected.height !== output.height || expected.pixelRatio !== output.pixelRatio) {
        failures.push(`${output.id}: manifest geometry changed`);
      }
    }
    if (outputs.length !== expectedManifest.outputs.length) failures.push("manifest output count differs from matrix");
  }

  await context.close();
  if (failures.length > 0) {
    throw new Error(`Pixel Golden failed:\n- ${failures.join("\n- ")}`);
  }
  process.stdout.write(`Renderer pixel Golden PASS: ${outputs.length} cases\n`);
} finally {
  if (browser) await browser.close();
  if (previewServer) await previewServer.httpServer.close();
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

function createDeterministicCover() {
  const png = new PNG({ width: 128, height: 128 });
  for (let y = 0; y < png.height; y += 1) {
    for (let x = 0; x < png.width; x += 1) {
      const index = (y * png.width + x) * 4;
      const ring = Math.abs(Math.hypot(x - 64, y - 64) - 38) < 5;
      png.data[index] = ring ? 244 : 24 + Math.floor((x / 127) * 66);
      png.data[index + 1] = ring ? 184 : 46 + Math.floor((y / 127) * 74);
      png.data[index + 2] = ring ? 108 : 96 + Math.floor(((x + y) / 254) * 82);
      png.data[index + 3] = 255;
    }
  }
  return PNG.sync.write(png);
}

function deepMerge(base, override) {
  if (!override || typeof override !== "object" || Array.isArray(override)) return override ?? base;
  const result = { ...base };
  for (const [key, value] of Object.entries(override)) {
    const current = base?.[key];
    result[key] = value && typeof value === "object" && !Array.isArray(value)
      ? deepMerge(current ?? {}, value)
      : value;
  }
  return result;
}

function hashTree(paths) {
  const files = paths.flatMap((path) => listFiles(path)).sort();
  const hash = createHash("sha256");
  for (const file of files) {
    hash.update(relative(rendererRoot, file).replaceAll("\\", "/"));
    hash.update("\0");
    hash.update(readFileSync(file));
    hash.update("\0");
  }
  return hash.digest("hex");
}

function listFiles(path) {
  const stats = statSync(path);
  if (stats.isFile()) return [path];
  return readdirSync(path).flatMap((name) => listFiles(resolve(path, name)));
}

function readJson(path) {
  return JSON.parse(readFileSync(path, "utf8"));
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function assertEqual(actual, expected, label) {
  if (actual !== expected) throw new Error(`${label} changed: expected ${expected}, got ${actual}`);
}
