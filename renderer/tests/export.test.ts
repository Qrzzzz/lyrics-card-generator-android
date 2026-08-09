import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createExportCanvasSurface,
  createFontEmbedCssCache,
  createRendererDomKey,
  createSvgSourceCache,
  createUsedFontFamilyKey,
  drawImageAndEncodePngAndClear,
  encodeCanvasAsPngAndClear,
  estimateExportBytes,
  ExportRenderError,
  sanitizeExportError
} from "../src/export";
import { DEFAULT_RENDER_SPEC } from "../src/defaultSpec";
import type { RenderSpec } from "../src/types";

describe("export sizing", () => {
  beforeEach(() => {
    vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    });
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("accounts for the squared PNG pixel ratio", () => {
    expect(estimateExportBytes(1080, 1350, 1)).toBe(5_832_000);
    expect(estimateExportBytes(1080, 1350, 2)).toBe(23_328_000);
  });

  it("clears reusable canvas pixels after successful PNG encoding", async () => {
    const png = new Blob(["png"], { type: "image/png" });
    const canvas = fakeCanvas((callback) => callback(png));

    await expect(encodeCanvasAsPngAndClear(canvas)).resolves.toBe(png);
    expectCanvasCleared(canvas);
  });

  it("clears reusable canvas pixels when PNG encoding returns no blob", async () => {
    const canvas = fakeCanvas((callback) => callback(null));

    await expect(encodeCanvasAsPngAndClear(canvas)).rejects.toThrow(
      "html-to-image returned an empty PNG"
    );
    expectCanvasCleared(canvas);
  });

  it("clears reusable canvas pixels when PNG encoding throws", async () => {
    const canvas = fakeCanvas(() => {
      throw new Error("encode failed");
    });

    await expect(encodeCanvasAsPngAndClear(canvas)).rejects.toThrow("encode failed");
    expectCanvasCleared(canvas);
  });

  it("releases the decoded SVG and clears canvas after successful PNG encoding", async () => {
    const png = new Blob(["png"], { type: "image/png" });
    const image = fakeImage();
    const canvas = fakeDrawableCanvas(image, (callback) => {
      expect(image.src).toBe("data:image/svg+xml,fixture");
      callback(png);
    });

    await expect(drawImageAndEncodePngAndClear(image, canvas)).resolves.toBe(png);
    expectImageReleasedAndCanvasCleared(image, canvas);
  });

  it("releases the decoded SVG and clears canvas when PNG encoding fails", async () => {
    const image = fakeImage();
    const canvas = fakeDrawableCanvas(image, (callback) => callback(null));

    await expect(drawImageAndEncodePngAndClear(image, canvas)).rejects.toThrow(
      "html-to-image returned an empty PNG"
    );
    expectImageReleasedAndCanvasCleared(image, canvas);
  });

  it("releases the decoded SVG and clears canvas pixels when drawing fails", async () => {
    const image = fakeImage();
    const canvas = fakeDrawableCanvas(image, () => undefined, () => {
      throw new Error("draw failed");
    });

    await expect(drawImageAndEncodePngAndClear(image, canvas)).rejects.toThrow("draw failed");
    expectImageReleasedAndCanvasCleared(image, canvas);
  });

  it("does not expose renderer input through unexpected export errors", () => {
    const sensitive = "decode failed for data:image/svg+xml,<text>private lyrics</text>";
    const error = sanitizeExportError(new Error(sensitive));

    expect(error.code).toBe("EXPORT_FAILED");
    expect(error.message).toBe("PNG export failed");
    expect(error.message).not.toContain("private lyrics");
    expect(sanitizeExportError(new ExportRenderError("Font loading exceeded 8 seconds", "FONT_LOAD_TIMEOUT")))
      .toMatchObject({ code: "FONT_LOAD_TIMEOUT", message: "Font loading exceeded 8 seconds" });
  });

  it("reuses one SVG source for the same DOM revision while PNG encoding still runs each time", async () => {
    const cache = createSvgSourceCache();
    let svgCreates = 0;
    let pngEncodes = 0;

    for (let attempt = 0; attempt < 2; attempt += 1) {
      await expect(cache.get(7, async () => `svg-${++svgCreates}`)).resolves.toBe("svg-1");
      const image = fakeImage();
      const canvas = fakeDrawableCanvas(image, (callback) => {
        pngEncodes += 1;
        callback(new Blob([`png-${pngEncodes}`], { type: "image/png" }));
      });
      await drawImageAndEncodePngAndClear(image, canvas);
    }

    expect(svgCreates).toBe(1);
    expect(pngEncodes).toBe(2);
  });

  it("reuses one canvas object for equal and changed export dimensions", () => {
    let creates = 0;
    const canvas = fakeReusableCanvas();
    const surface = createExportCanvasSurface(() => {
      creates += 1;
      return canvas;
    });

    expect(surface.acquire(1080, 1350, 1)).toBe(canvas);
    expect(surface.acquire(1080, 1350, 1)).toBe(canvas);
    expect(canvas.width).toBe(1080);
    expect(canvas.height).toBe(1350);
    expect(surface.acquire(1080, 1350, 2)).toBe(canvas);
    expect(canvas.width).toBe(2160);
    expect(canvas.height).toBe(2700);
    expect(creates).toBe(1);
  });

  it("releases reusable canvas backing only at renderer lifecycle end", () => {
    const canvases = [fakeReusableCanvas(), fakeReusableCanvas()];
    let creates = 0;
    const surface = createExportCanvasSurface(() => canvases[creates++]);
    const first = surface.acquire(1080, 1350, 2);

    surface.release();
    expect(first.width).toBe(0);
    expect(first.height).toBe(0);
    expect(surface.acquire(1080, 1350, 2)).toBe(canvases[1]);
    expect(creates).toBe(2);
  });

  it("normalizes only pixel ratio and property order in the renderer DOM key", () => {
    const oneX = withSpec({ canvas: { ...DEFAULT_RENDER_SPEC.canvas, pixelRatio: 1 } });
    const twoX = withSpec({ canvas: { ...DEFAULT_RENDER_SPEC.canvas, pixelRatio: 2 } });
    const reordered = Object.fromEntries(Object.entries(twoX).reverse()) as unknown as RenderSpec;

    expect(createRendererDomKey(oneX)).toBe(createRendererDomKey(twoX));
    expect(createRendererDomKey(reordered)).toBe(createRendererDomKey(twoX));
  });

  it("changes the renderer DOM key for content, auto-height geometry, and cover assets", () => {
    const baseline = createRendererDomKey(DEFAULT_RENDER_SPEC);
    const variants = [
      withSpec({ content: { ...DEFAULT_RENDER_SPEC.content, lyrics: "changed" } }),
      withSpec({ canvas: { ...DEFAULT_RENDER_SPEC.canvas, autoHeight: true } }),
      withSpec({ canvas: { ...DEFAULT_RENDER_SPEC.canvas, height: DEFAULT_RENDER_SPEC.canvas.height + 1 } }),
      withSpec({ song: { ...DEFAULT_RENDER_SPEC.song, coverAssetId: "cover-new" } })
    ];

    for (const variant of variants) {
      expect(createRendererDomKey(variant)).not.toBe(baseline);
    }
  });

  it("does not cache a failed SVG source creation", async () => {
    const cache = createSvgSourceCache();
    let attempts = 0;

    await expect(cache.get(3, async () => {
      attempts += 1;
      throw new Error("source failed");
    })).rejects.toThrow("source failed");
    await expect(cache.get(3, async () => `svg-${++attempts}`)).resolves.toBe("svg-2");
    expect(attempts).toBe(2);
  });

  it("keeps only the current SVG revision and clears it at renderer lifecycle end", async () => {
    const cache = createSvgSourceCache();
    let creates = 0;
    const create = async () => `svg-${++creates}`;

    await expect(cache.get(1, create)).resolves.toBe("svg-1");
    await expect(cache.get(2, create)).resolves.toBe("svg-2");
    await expect(cache.get(1, create)).resolves.toBe("svg-3");
    cache.clear();
    await expect(cache.get(1, create)).resolves.toBe("svg-4");
    expect(creates).toBe(4);
  });

  it("reuses font CSS for one family, replaces it on switch, and clears it on lifecycle end", async () => {
    const cache = createFontEmbedCssCache();
    const sansKey = createUsedFontFamilyKey(fakeFontNode('"Source Han Sans SC", sans-serif'));
    const serifKey = createUsedFontFamilyKey(fakeFontNode('"Source Han Serif SC", serif'));
    let creates = 0;
    const create = async () => `font-css-${++creates}`;

    await expect(cache.get(sansKey, create)).resolves.toBe("font-css-1");
    await expect(cache.get(sansKey, create)).resolves.toBe("font-css-1");
    await expect(cache.get(serifKey, create)).resolves.toBe("font-css-2");
    await expect(cache.get(sansKey, create)).resolves.toBe("font-css-3");
    cache.clear();
    await expect(cache.get(sansKey, create)).resolves.toBe("font-css-4");
    expect(creates).toBe(4);
  });

  it("does not cache failed font CSS and includes descendant font overrides in its key", async () => {
    const cache = createFontEmbedCssCache();
    const key = createUsedFontFamilyKey(fakeFontNode(
      '"Source Han Serif SC", serif',
      [fakeFontNode("sans-serif")]
    ));
    expect(key.split("\u0000")).toEqual(["Source Han Serif SC", "sans-serif", "serif"]);

    await expect(cache.get(key, async () => {
      throw new Error("font failed");
    })).rejects.toThrow("font failed");
    await expect(cache.get(key, async () => "font-css-ok")).resolves.toBe("font-css-ok");
  });
});

function withSpec(overrides: Partial<RenderSpec>): RenderSpec {
  return {
    ...DEFAULT_RENDER_SPEC,
    ...overrides
  };
}

function fakeFontNode(fontFamily: string, children: HTMLElement[] = []): HTMLElement {
  return {
    style: { fontFamily },
    children
  } as unknown as HTMLElement;
}

function fakeCanvas(
  encode: (callback: BlobCallback, type?: string, quality?: number) => void
): HTMLCanvasElement {
  const canvas = {
    width: 2160,
    height: 2700,
    clearCount: 0,
    getContext: () => ({
      clearRect: () => {
        canvas.clearCount += 1;
      }
    }),
    toBlob: encode
  };
  return canvas as unknown as HTMLCanvasElement;
}

function fakeDrawableCanvas(
  expectedImage: HTMLImageElement,
  encode: (callback: BlobCallback, type?: string, quality?: number) => void,
  draw: () => void = () => undefined
): HTMLCanvasElement {
  const canvas = {
    width: 2160,
    height: 2700,
    clearCount: 0,
    getContext: () => ({
      drawImage: (image: HTMLImageElement) => {
        expect(image).toBe(expectedImage);
        draw();
      },
      clearRect: () => {
        canvas.clearCount += 1;
      }
    }),
    toBlob: encode
  };
  return canvas as unknown as HTMLCanvasElement;
}

function fakeImage(): HTMLImageElement {
  const image = {
    src: "data:image/svg+xml,fixture",
    onload: () => undefined,
    onerror: () => undefined,
    removeAttribute(name: string) {
      expect(name).toBe("src");
      image.src = "";
    }
  };
  return image as unknown as HTMLImageElement;
}

function expectImageReleasedAndCanvasCleared(
  image: HTMLImageElement,
  canvas: HTMLCanvasElement
) {
  expect(image.src).toBe("");
  expect(image.onload).toBeNull();
  expect(image.onerror).toBeNull();
  expectCanvasCleared(canvas);
}

function expectCanvasCleared(canvas: HTMLCanvasElement) {
  expect((canvas as HTMLCanvasElement & { clearCount: number }).clearCount).toBe(1);
  expect(canvas.width).toBe(2160);
  expect(canvas.height).toBe(2700);
}

function fakeReusableCanvas(): HTMLCanvasElement {
  return {
    width: 0,
    height: 0,
    style: { width: "", height: "" },
    getContext: () => ({ clearRect: () => undefined })
  } as unknown as HTMLCanvasElement;
}
