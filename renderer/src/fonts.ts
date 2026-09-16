import manifest from "../renderer-manifest.json";
import type { RenderSpec } from "./types";

export async function prepareCustomFont(spec: RenderSpec) {
  const asset = spec.typography.customFontEnabled ? spec.typography.customFontAsset : null;
  let style = document.querySelector<HTMLStyleElement>("style[data-custom-font]");
  if (!style) { style = document.createElement("style"); style.dataset.customFont = "true"; document.head.append(style); }
  const css = asset && /^[a-f0-9]{64}\.(ttf|otf|woff2?)$/.test(asset)
    ? `@font-face { font-family: "Imported Lyric Font"; src: url("./custom-fonts/${asset}"); font-weight: 100 900; font-style: ${spec.typography.fontItalic ? "italic" : "normal"}; }` : "";
  if (style.textContent !== css) style.textContent = css;
  if (css) {
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      await Promise.race([document.fonts.load(`${spec.typography.fontItalic ? "italic " : ""}60px "Imported Lyric Font"`), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error("Custom font load timed out")), 8000); })]);
    } finally { clearTimeout(timer); }
  }
}

export function installLocalFonts() {
  const fontVersion = encodeURIComponent(manifest.fontManifestHash.slice(0, 16));
  const stylesheet = document.createElement("style");
  stylesheet.dataset.rendererFonts = "true";
  stylesheet.textContent = `
    @font-face {
      font-family: "Source Han Sans SC";
      src: url("./fonts/SourceHanSansSC-Heavy.otf?v=${fontVersion}") format("opentype");
      font-style: normal;
      font-weight: 100 900;
      font-display: swap;
    }
    @font-face {
      font-family: "Source Han Serif SC";
      src: url("./fonts/SourceHanSerifSC-Heavy.otf?v=${fontVersion}") format("opentype");
      font-style: normal;
      font-weight: 100 900;
      font-display: swap;
    }
  `;
  document.head.append(stylesheet);
}
