import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const source = readFileSync(resolve(import.meta.dirname, "../src/fonts.ts"), "utf8");

describe("local font loading", () => {
  it("shows fallback text immediately and versions cached font URLs", () => {
    expect(source.match(/font-display: swap/g)).toHaveLength(4);
    expect(source).toContain("manifest.fontManifestHash");
    expect(source.match(/\?v=\$\{fontVersion\}/g)).toHaveLength(4);
  });
});
