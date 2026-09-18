import { describe, expect, it } from "vitest";
import { DEFAULT_RENDER_SPEC } from "../src/defaultSpec";
import { InvalidRenderSpecError, parseRenderSpec, resolveCoverAssetUrl } from "../src/spec";

describe("RenderSpec v1 validation", () => {
  it("accepts and clones the renderer default", () => {
    const parsed = parseRenderSpec(DEFAULT_RENDER_SPEC);
    expect(parsed).toEqual(DEFAULT_RENDER_SPEC);
    expect(parsed).not.toBe(DEFAULT_RENDER_SPEC);
  });

  it("rejects a mismatched protocol schema", () => {
    expect(() => parseRenderSpec({ ...DEFAULT_RENDER_SPEC, schemaVersion: 1 })).toThrow(InvalidRenderSpecError);
  });

  it("rejects portrait specs with a landscape ratio", () => {
    const invalid = {
      ...DEFAULT_RENDER_SPEC,
      canvas: { ...DEFAULT_RENDER_SPEC.canvas, ratio: "16:9", width: 1920, height: 1080 }
    };
    expect(() => parseRenderSpec(invalid)).toThrow(/ratio/);
  });

  it("rejects preset dimensions that do not match the Windows baseline", () => {
    const invalid = {
      ...DEFAULT_RENDER_SPEC,
      canvas: { ...DEFAULT_RENDER_SPEC.canvas, width: 1000 }
    };
    expect(() => parseRenderSpec(invalid)).toThrow(/width/);
  });

  it("only maps safe logical asset IDs", () => {
    expect(resolveCoverAssetUrl("cover-abc_01.webp")).toBe("../media/cover-abc_01.webp");
    expect(resolveCoverAssetUrl("../secret")).toBeNull();
    expect(resolveCoverAssetUrl("https://example.com/cover.png")).toBeNull();
  });
});


describe("landscape requested and measured dimensions", () => {
  const short = { ...DEFAULT_RENDER_SPEC, canvas: {
    ...DEFAULT_RENDER_SPEC.canvas, layoutMode: "landscape", ratio: "custom",
    width: 1227, height: 697, autoHeight: true,
    landscape: { autoLyricsWidth: true, lyricsWidth: 880, autoHeight: true, requestedHeight: 720 }
  }};
  it("accepts short actual output without padding the requested height", () => {
    expect(parseRenderSpec(short).canvas.height).toBe(697);
  });
  it.each([0, 639, 6401, 697.5])("rejects invalid measured height %s", height => {
    expect(() => parseRenderSpec({ ...short, canvas: { ...short.canvas, height } })).toThrow();
  });
  it.each([719, 3601])("rejects invalid requested height %s", requestedHeight => {
    expect(() => parseRenderSpec({ ...short, canvas: { ...short.canvas,
      landscape: { ...short.canvas.landscape, requestedHeight } } })).toThrow();
  });
  it.each([1079, 3001])("rejects invalid measured width %s", width => {
    expect(() => parseRenderSpec({ ...short, canvas: { ...short.canvas, width } })).toThrow();
  });
});
