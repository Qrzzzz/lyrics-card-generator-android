import manifest from "../renderer-manifest.json";

export function installLocalFonts() {
  const fontVersion = encodeURIComponent(manifest.fontManifestHash.slice(0, 16));
  const stylesheet = document.createElement("style");
  stylesheet.dataset.rendererFonts = "true";
  stylesheet.textContent = `
    @font-face {
      font-family: "Source Han Sans Heavy Local";
      src: url("./fonts/SourceHanSansSC-Heavy.otf?v=${fontVersion}") format("opentype");
      font-style: normal;
      font-weight: 900;
      font-display: swap;
    }
    @font-face {
      font-family: "Source Han Sans SC";
      src: url("./fonts/SourceHanSansSC-Heavy.otf?v=${fontVersion}") format("opentype");
      font-style: normal;
      font-weight: 100 900;
      font-display: swap;
    }
    @font-face {
      font-family: "Source Han Serif Heavy Local";
      src: url("./fonts/SourceHanSerifSC-Heavy.otf?v=${fontVersion}") format("opentype");
      font-style: normal;
      font-weight: 900;
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
