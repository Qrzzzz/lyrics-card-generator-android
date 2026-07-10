export function installLocalFonts() {
  const stylesheet = document.createElement("style");
  stylesheet.dataset.rendererFonts = "true";
  stylesheet.textContent = `
    @font-face {
      font-family: "Source Han Sans Heavy Local";
      src: url("./fonts/SourceHanSansSC-Heavy.otf") format("opentype");
      font-style: normal;
      font-weight: 900;
      font-display: block;
    }
    @font-face {
      font-family: "Source Han Sans SC";
      src: url("./fonts/SourceHanSansSC-Heavy.otf") format("opentype");
      font-style: normal;
      font-weight: 100 900;
      font-display: block;
    }
    @font-face {
      font-family: "Source Han Serif Heavy Local";
      src: url("./fonts/SourceHanSerifSC-Heavy.otf") format("opentype");
      font-style: normal;
      font-weight: 900;
      font-display: block;
    }
    @font-face {
      font-family: "Source Han Serif SC";
      src: url("./fonts/SourceHanSerifSC-Heavy.otf") format("opentype");
      font-style: normal;
      font-weight: 100 900;
      font-display: block;
    }
  `;
  document.head.append(stylesheet);
}
