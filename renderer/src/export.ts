import { getFontEmbedCSS, toBlob } from "html-to-image";

const FONT_TIMEOUT_MS = 8_000;
const MAX_EXPORT_WORKING_SET = 180 * 1024 * 1024;

let cachedFontEmbedCss: string | undefined;

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
  pixelRatio: 1 | 2
) {
  assertExportMemory(width, height, pixelRatio);
  await waitForStableRender();

  try {
    cachedFontEmbedCss ??= await getFontEmbedCSS(node);
    const blob = await toBlob(node, {
      cacheBust: true,
      pixelRatio,
      width,
      height,
      fontEmbedCSS: cachedFontEmbedCss,
      preferredFontFormat: "opentype",
      style: {
        width: `${width}px`,
        height: `${height}px`,
        transform: "none"
      }
    });

    if (!blob) {
      throw new Error("html-to-image returned an empty PNG");
    }
    return blob;
  } catch (error) {
    if (error instanceof ExportRenderError) {
      throw error;
    }
    throw new ExportRenderError(error instanceof Error ? error.message : "PNG export failed", "EXPORT_FAILED");
  }
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
