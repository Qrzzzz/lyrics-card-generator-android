import { getFontEmbedCSS, toSvg } from "html-to-image";
import type { RenderSpec } from "./types";

const FONT_TIMEOUT_MS = 8_000;
const MAX_EXPORT_WORKING_SET = 180 * 1024 * 1024;

export interface SingleSlotStringCache<TKey> {
  get(key: TKey, createValue: () => Promise<string>): Promise<string>;
  clear(): void;
}

export type SvgSourceCache = SingleSlotStringCache<number>;
export type FontEmbedCssCache = SingleSlotStringCache<string>;

export interface ExportCanvasSurface {
  acquire(width: number, height: number, pixelRatio: 1 | 2): HTMLCanvasElement;
  release(): void;
}

export function createSvgSourceCache(): SvgSourceCache {
  return createSingleSlotStringCache<number>();
}

export function createFontEmbedCssCache(): FontEmbedCssCache {
  return createSingleSlotStringCache<string>();
}

export function createExportCanvasSurface(
  createCanvas: () => HTMLCanvasElement = () => document.createElement("canvas")
): ExportCanvasSurface {
  let canvas: HTMLCanvasElement | undefined;

  return {
    acquire(width, height, pixelRatio) {
      const activeCanvas = canvas ?? createCanvas();
      canvas = activeCanvas;
      const pixelWidth = width * pixelRatio;
      const pixelHeight = height * pixelRatio;
      if (activeCanvas.width !== pixelWidth) activeCanvas.width = pixelWidth;
      if (activeCanvas.height !== pixelHeight) activeCanvas.height = pixelHeight;
      activeCanvas.style.width = `${width}`;
      activeCanvas.style.height = `${height}`;
      if (!activeCanvas.getContext("2d")) {
        releaseCanvas(activeCanvas);
        canvas = undefined;
        throw new Error("Canvas 2D context is unavailable");
      }
      return activeCanvas;
    },
    release() {
      releaseCanvas(canvas);
      canvas = undefined;
    }
  };
}

function createSingleSlotStringCache<TKey>(): SingleSlotStringCache<TKey> {
  let slot: { key: TKey; value: Promise<string> } | undefined;

  return {
    async get(key, createValue) {
      if (slot?.key === key) return slot.value;

      const value = createValue();
      slot = { key, value };
      try {
        return await value;
      } catch (error) {
        if (slot?.value === value) slot = undefined;
        throw error;
      }
    },
    clear() {
      slot = undefined;
    }
  };
}

export function createRendererDomKey(spec: RenderSpec) {
  return canonicalJson({
    ...spec,
    canvas: {
      ...spec.canvas,
      // Card and CSS never read this field; it only sizes the destination canvas.
      pixelRatio: 1
    }
  });
}

export function createUsedFontFamilyKey(node: HTMLElement) {
  const families = new Set<string>();
  const visit = (element: HTMLElement) => {
    const familyList = element.style.fontFamily || getComputedStyle(element).fontFamily;
    familyList.split(",").forEach((family) => {
      const normalized = family.trim().replace(/["']/g, "");
      if (normalized) families.add(normalized);
    });
    Array.from(element.children).forEach((child) => visit(child as HTMLElement));
  };
  visit(node);
  return Array.from(families).sort().join("\u0000");
}

export class ExportRenderError extends Error {
  constructor(
    message: string,
    readonly code: "FONT_LOAD_TIMEOUT" | "EXPORT_FAILED" | "EXPORT_OUT_OF_MEMORY"
  ) {
    super(message);
    this.name = "ExportRenderError";
  }
}

export async function waitForStableRender() {
  if ("fonts" in document) {
    let timeoutId: number | undefined;
    try {
      await Promise.race([
        document.fonts.ready,
        new Promise<never>((_, reject) => {
          timeoutId = window.setTimeout(
            () => reject(new ExportRenderError("Font loading exceeded 8 seconds", "FONT_LOAD_TIMEOUT")),
            FONT_TIMEOUT_MS
          );
        })
      ]);
    } finally {
      if (timeoutId !== undefined) {
        window.clearTimeout(timeoutId);
      }
    }
  }

  await nextAnimationFrame();
  await nextAnimationFrame();
}

export async function renderNodeAsPng(
  node: HTMLElement,
  width: number,
  height: number,
  pixelRatio: 1 | 2,
  sourceCache: SvgSourceCache,
  fontEmbedCssCache: FontEmbedCssCache,
  canvasSurface: ExportCanvasSurface,
  domRevision: number
) {
  assertExportMemory(width, height, pixelRatio);
  await waitForStableRender();

  let svgDataUrl: string | undefined;
  let image: HTMLImageElement | undefined;
  try {
    svgDataUrl = await sourceCache.get(domRevision, () =>
      fontEmbedCssCache
        .get(createUsedFontFamilyKey(node), () => getFontEmbedCSS(node))
        .then((fontEmbedCSS) =>
          toSvg(node, {
            cacheBust: true,
            pixelRatio,
            width,
            height,
            fontEmbedCSS,
            preferredFontFormat: "opentype",
            style: {
              width: `${width}px`,
              height: `${height}px`,
              transform: "none"
            }
          })
        )
    );

    image = await loadSvgImage(svgDataUrl);
    svgDataUrl = undefined;
    const canvas = canvasSurface.acquire(width, height, pixelRatio);
    return await drawImageAndEncodePngAndClear(image, canvas);
  } catch (error) {
    throw sanitizeExportError(error);
  } finally {
    svgDataUrl = undefined;
    releaseDecodedImage(image);
  }
}

export function sanitizeExportError(error: unknown) {
  if (error instanceof ExportRenderError) return error;
  return new ExportRenderError("PNG export failed", "EXPORT_FAILED");
}

export async function encodeCanvasAsPngAndClear(canvas: HTMLCanvasElement) {
  try {
    return await encodeCanvasAsPng(canvas);
  } finally {
    clearCanvasPixels(canvas);
  }
}

export async function drawImageAndEncodePngAndClear(
  image: HTMLImageElement,
  canvas: HTMLCanvasElement
) {
  try {
    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("Canvas 2D context is unavailable");
    }
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    // The canvas owns the drawn pixels now. Drop the SVG-backed Image before
    // PNG readback so old WebView does not retain both decoded surfaces.
    releaseDecodedImage(image);
    return await encodeCanvasAsPng(canvas);
  } finally {
    releaseDecodedImage(image);
    clearCanvasPixels(canvas);
  }
}

async function encodeCanvasAsPng(canvas: HTMLCanvasElement) {
  const blob = await new Promise<Blob | null>((resolve) => {
    canvas.toBlob(resolve, "image/png", 1);
  });
  if (!blob) {
    throw new Error("html-to-image returned an empty PNG");
  }
  return blob;
}

function clearCanvasPixels(canvas: HTMLCanvasElement) {
  const context = canvas.getContext("2d");
  if (!context) {
    releaseCanvas(canvas);
    throw new Error("Canvas 2D context is unavailable");
  }
  try {
    context.clearRect(0, 0, canvas.width, canvas.height);
  } catch (error) {
    releaseCanvas(canvas);
    throw error;
  }
}

function loadSvgImage(source: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.onload = () => {
      image.decode().then(
        () => requestAnimationFrame(() => resolve(image)),
        (error) => {
          releaseDecodedImage(image);
          reject(error);
        }
      );
    };
    image.onerror = (error) => {
      releaseDecodedImage(image);
      reject(error);
    };
    image.crossOrigin = "anonymous";
    image.decoding = "async";
    image.src = source;
  });
}

function releaseDecodedImage(image: HTMLImageElement | undefined) {
  if (!image) return;
  image.onload = null;
  image.onerror = null;
  image.src = "";
  image.removeAttribute("src");
}

function releaseCanvas(canvas: HTMLCanvasElement | undefined) {
  if (!canvas) return;
  canvas.width = 0;
  canvas.height = 0;
}

export function estimateExportBytes(width: number, height: number, pixelRatio: number) {
  return width * height * pixelRatio * pixelRatio * 4;
}

function assertExportMemory(width: number, height: number, pixelRatio: number) {
  const estimate = estimateExportBytes(width, height, pixelRatio);
  if (!Number.isSafeInteger(estimate) || estimate > MAX_EXPORT_WORKING_SET) {
    throw new ExportRenderError(
      `Estimated RGBA working set ${estimate} bytes exceeds the Alpha limit`,
      "EXPORT_OUT_OF_MEMORY"
    );
  }
}

function nextAnimationFrame() {
  return new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
}

function canonicalJson(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(canonicalJson).join(",")}]`;
  }
  if (value !== null && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, entry]) => `${JSON.stringify(key)}:${canonicalJson(entry)}`);
    return `{${entries.join(",")}}`;
  }
  return JSON.stringify(value);
}
