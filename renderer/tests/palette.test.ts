import { describe, expect, it } from "vitest";
import { samplePalette } from "../src/palette";

describe("palette sampling", () => {
  it("returns three stable local colors from sampled pixels", () => {
    const pixels = new Uint8ClampedArray([
      20, 50, 90, 255,
      20, 50, 90, 255,
      200, 80, 40, 255,
      50, 160, 130, 255,
      20, 50, 90, 255,
      200, 80, 40, 255,
      50, 160, 130, 255,
      20, 50, 90, 255,
      200, 80, 40, 255,
      50, 160, 130, 255,
      20, 50, 90, 255,
      200, 80, 40, 255,
      50, 160, 130, 255,
      20, 50, 90, 255,
      200, 80, 40, 255,
      50, 160, 130, 255
    ]);
    const palette = samplePalette(pixels);
    expect(palette.dominant).toMatch(/^#[0-9A-F]{6}$/);
    expect(palette.secondary).toMatch(/^#[0-9A-F]{6}$/);
    expect(palette.accent).toMatch(/^#[0-9A-F]{6}$/);
  });
});
