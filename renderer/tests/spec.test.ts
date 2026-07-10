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
    expect(() => parseRenderSpec({ ...DEFAULT_RENDER_SPEC, schemaVersion: 2 })).toThrow(InvalidRenderSpecError);
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
