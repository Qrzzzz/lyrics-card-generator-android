import type { RenderSpec, TextColorPreset } from "./types";

const TEXT_PRESETS: Record<TextColorPreset, string> = {
  white: "#FFFFFF",
  black: "#090A0C",
  warmWhite: "#F8F4EA",
  cream: "#F4E7C8",
  charcoal: "#24272D",
  softBlue: "#D6E9F8",
  softGold: "#F2D18B"
};

export function normalizeHex(value: string, fallback = "#111827") {
  const trimmed = value.trim();
  if (/^#[0-9a-f]{6}$/i.test(trimmed) || /^#[0-9a-f]{8}$/i.test(trimmed)) {
    return trimmed.toUpperCase();
  }
  return fallback;
}

export function resolveTextColor(spec: RenderSpec) {
  const { textColorMode, textColorPreset, customTextColor } = spec.typography;
  if (textColorMode === "custom" && customTextColor) {
    return normalizeHex(customTextColor, "#F8F4EA");
  }
  if (textColorMode === "preset") {
    return TEXT_PRESETS[textColorPreset];
  }
  return "#F8F4EA";
}

export function withAlpha(color: string, alpha: number) {
  const normalized = normalizeHex(color).slice(1, 7);
  const red = Number.parseInt(normalized.slice(0, 2), 16);
  const green = Number.parseInt(normalized.slice(2, 4), 16);
  const blue = Number.parseInt(normalized.slice(4, 6), 16);
  return `rgba(${red}, ${green}, ${blue}, ${clamp(alpha, 0, 1)})`;
}

export function mixColors(color: string, target: string, targetWeight: number) {
  const from = toRgb(color);
  const to = toRgb(target);
  const weight = clamp(targetWeight, 0, 1);
  return rgbToHex({
    r: from.r + (to.r - from.r) * weight,
    g: from.g + (to.g - from.g) * weight,
    b: from.b + (to.b - from.b) * weight
  });
}

export function isDarkColor(color: string) {
  const { r, g, b } = toRgb(color);
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255 < 0.42;
}

function toRgb(color: string) {
  const normalized = normalizeHex(color).slice(1, 7);
  return {
    r: Number.parseInt(normalized.slice(0, 2), 16),
    g: Number.parseInt(normalized.slice(2, 4), 16),
    b: Number.parseInt(normalized.slice(4, 6), 16)
  };
}

function rgbToHex({ r, g, b }: { r: number; g: number; b: number }) {
  return `#${[r, g, b]
    .map((value) => Math.round(clamp(value, 0, 255)).toString(16).padStart(2, "0"))
    .join("")}`.toUpperCase();
}

function clamp(value: number, minimum: number, maximum: number) {
  return Math.min(maximum, Math.max(minimum, value));
}
