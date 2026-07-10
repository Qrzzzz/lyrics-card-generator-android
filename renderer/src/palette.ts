import { mixColors, normalizeHex } from "./color";
import { resolveCoverAssetUrl } from "./spec";

export type SampledPalette = {
  dominant: string;
  secondary: string;
  accent: string;
};

export async function extractPaletteFromAsset(assetId: string): Promise<SampledPalette> {
  const source = resolveCoverAssetUrl(assetId);
  if (!source) {
    throw new PaletteExtractionError("Asset ID is invalid", "ASSET_NOT_FOUND");
  }

  const image = await loadImage(source);
  const canvas = document.createElement("canvas");
  const size = 64;
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) {
    throw new PaletteExtractionError("Canvas 2D context is unavailable", "IMAGE_DECODE_FAILED");
  }

  const sourceSize = Math.min(image.naturalWidth, image.naturalHeight);
  const sourceX = Math.max(0, (image.naturalWidth - sourceSize) / 2);
  const sourceY = Math.max(0, (image.naturalHeight - sourceSize) / 2);
  context.drawImage(image, sourceX, sourceY, sourceSize, sourceSize, 0, 0, size, size);

  let data: Uint8ClampedArray;
  try {
    data = context.getImageData(0, 0, size, size).data;
  } catch {
    throw new PaletteExtractionError("Cover pixels could not be sampled", "IMAGE_DECODE_FAILED");
  }

  return samplePalette(data);
}

export function samplePalette(data: Uint8ClampedArray): SampledPalette {
  const buckets = new Map<string, { count: number; r: number; g: number; b: number }>();

  for (let index = 0; index < data.length; index += 16) {
    const alpha = data[index + 3] ?? 0;
    if (alpha < 180) continue;
    const r = data[index] ?? 0;
    const g = data[index + 1] ?? 0;
    const b = data[index + 2] ?? 0;
    const key = `${Math.round(r / 32)}:${Math.round(g / 32)}:${Math.round(b / 32)}`;
    const bucket = buckets.get(key) ?? { count: 0, r: 0, g: 0, b: 0 };
    bucket.count += 1;
    bucket.r += r;
    bucket.g += g;
    bucket.b += b;
    buckets.set(key, bucket);
  }

  const candidates = [...buckets.values()]
    .map((bucket) => ({
      count: bucket.count,
      r: bucket.r / bucket.count,
      g: bucket.g / bucket.count,
      b: bucket.b / bucket.count
    }))
    .sort((left, right) => score(right) - score(left));

  if (candidates.length === 0) {
    return { dominant: "#111827", secondary: "#334155", accent: "#F97316" };
  }

  const selected = [candidates[0]];
  for (const candidate of candidates.slice(1)) {
    if (selected.every((color) => colorDistance(color, candidate) >= 72)) {
      selected.push(candidate);
    }
    if (selected.length === 3) break;
  }

  const dominant = toHex(selected[0]);
  const secondary = selected[1] ? toHex(selected[1]) : mixColors(dominant, "#FFFFFF", 0.28);
  const accent = selected[2] ? toHex(selected[2]) : mixColors(dominant, "#F97316", 0.46);
  return {
    dominant: normalizeHex(dominant),
    secondary: normalizeHex(secondary),
    accent: normalizeHex(accent)
  };
}

export class PaletteExtractionError extends Error {
  constructor(
    message: string,
    readonly code: "ASSET_NOT_FOUND" | "IMAGE_DECODE_FAILED"
  ) {
    super(message);
    this.name = "PaletteExtractionError";
  }
}

function loadImage(source: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    const timeout = window.setTimeout(
      () => reject(new PaletteExtractionError("Cover image loading timed out", "ASSET_NOT_FOUND")),
      10_000
    );
    image.decoding = "async";
    image.onload = () => {
      window.clearTimeout(timeout);
      resolve(image);
    };
    image.onerror = () => {
      window.clearTimeout(timeout);
      reject(new PaletteExtractionError("Cover asset could not be loaded", "ASSET_NOT_FOUND"));
    };
    image.src = source;
  });
}

function score(color: { count: number; r: number; g: number; b: number }) {
  const maximum = Math.max(color.r, color.g, color.b);
  const minimum = Math.min(color.r, color.g, color.b);
  const saturation = maximum === 0 ? 0 : (maximum - minimum) / maximum;
  const luminance = (0.2126 * color.r + 0.7152 * color.g + 0.0722 * color.b) / 255;
  const usefulLuminance = 1 - Math.min(1, Math.abs(luminance - 0.48) * 1.55);
  return color.count * (0.64 + saturation * 0.36) * (0.68 + usefulLuminance * 0.32);
}

function colorDistance(
  left: { r: number; g: number; b: number },
  right: { r: number; g: number; b: number }
) {
  return Math.hypot(left.r - right.r, left.g - right.g, left.b - right.b);
}

function toHex(color: { r: number; g: number; b: number }) {
  return `#${[color.r, color.g, color.b]
    .map((value) => Math.round(Math.min(255, Math.max(0, value))).toString(16).padStart(2, "0"))
    .join("")}`;
}
