import { readdirSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { parseRenderSpec } from "../src/spec";
import type { RenderSpec } from "../src/types";

const rendererRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const fixtureDirectory = resolve(rendererRoot, "fixtures");
const goldenMatrix = JSON.parse(
  readFileSync(resolve(rendererRoot, "golden/cases.json"), "utf8"),
) as GoldenMatrix;
const goldenBase = JSON.parse(
  readFileSync(resolve(rendererRoot, goldenMatrix.baseFixture), "utf8"),
) as RenderSpec;
const goldenCases = goldenMatrix.cases.map((entry) => ({
  ...entry,
  spec: deepMerge(goldenBase, entry.overrides),
}));

describe("RenderSpec fixtures", () => {
  const fixtureNames = readdirSync(fixtureDirectory).filter((name) => name.endsWith(".json"));

  it("ships multiple layout and content fixtures", () => {
    expect(fixtureNames.length).toBeGreaterThanOrEqual(5);
  });

  it.each(fixtureNames)("validates %s against RenderSpec v1", (fixtureName) => {
    const raw = JSON.parse(readFileSync(resolve(fixtureDirectory, fixtureName), "utf8")) as unknown;
    const spec = parseRenderSpec(raw);
    expect(spec.schemaVersion).toBe(1);
    expect(spec.rendererVersion).toBe("android-alpha-renderer-1");
  });

  it("defines exactly 30 uniquely named release pixel cases", () => {
    expect(goldenCases).toHaveLength(30);
    expect(new Set(goldenCases.map((entry) => entry.id)).size).toBe(30);
    expect(goldenCases.every((entry) => /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(entry.id))).toBe(true);
  });

  it("audits every required release coverage dimension", () => {
    const declared = new Set(goldenCases.flatMap((entry) => entry.coverage));
    expect(goldenMatrix.requiredCoverage.filter((tag) => !declared.has(tag))).toEqual([]);

    const specs = goldenCases.map((entry) => entry.spec);
    expect(new Set(specs.map((spec) => spec.canvas.ratio))).toEqual(
      new Set(["1:1", "4:5", "9:16", "16:9", "21:9", "3:2", "custom"]),
    );
    expect(new Set(specs.map((spec) => spec.content.mode))).toEqual(new Set(["lyrics", "instrumental"]));
    expect(new Set(specs.map((spec) => spec.content.translationEnabled))).toEqual(new Set([true, false]));
    expect(new Set(specs.map((spec) => spec.visibility.showCover))).toEqual(new Set([true, false]));
    expect(new Set(specs.map((spec) => spec.typography.alignment))).toEqual(new Set(["left", "center", "right"]));
    expect(new Set(specs.map((spec) => spec.typography.textColorMode))).toEqual(new Set(["auto", "preset", "custom"]));
    expect(new Set(specs.map((spec) => spec.visual.backgroundMode))).toEqual(new Set(["palette", "gradient"]));
    expect(new Set(specs.map((spec) => spec.canvas.pixelRatio))).toEqual(new Set([1, 2]));
    expect(specs.some((spec) => spec.canvas.autoHeight)).toBe(true);
    expect(specs.some((spec) => spec.typography.fontScheme === "sans-heavy")).toBe(true);
    expect(specs.some((spec) => spec.typography.fontScheme === "serif-heavy")).toBe(true);
    expect(specs.some((spec) => spec.song.title.length > 60)).toBe(true);
    expect(specs.some((spec) => spec.song.artist.length > 50)).toBe(true);
    expect(specs.some((spec) => spec.song.album.length > 50)).toBe(true);
    expect(specs.some((spec) => /\p{Script=Han}/u.test(spec.content.lyrics))).toBe(true);
    expect(specs.some((spec) => /[A-Za-z]/.test(spec.content.lyrics))).toBe(true);
    expect(specs.some((spec) => /\p{Extended_Pictographic}/u.test(`${spec.song.title}\n${spec.content.lyrics}`))).toBe(true);
  });

  it.each(goldenCases)("validates release pixel case $id", ({ spec }) => {
    expect(parseRenderSpec(spec)).toEqual(spec);
  });
});

type GoldenCase = {
  id: string;
  coverage: string[];
  overrides: Partial<RenderSpec> & Record<string, unknown>;
};

type GoldenMatrix = {
  datasetVersion: string;
  baseFixture: string;
  requiredCoverage: string[];
  cases: GoldenCase[];
};

function deepMerge<T>(base: T, override: unknown): T {
  if (!override || typeof override !== "object" || Array.isArray(override)) {
    return (override ?? base) as T;
  }
  const result = { ...(base as Record<string, unknown>) };
  for (const [key, value] of Object.entries(override)) {
    const current = (base as Record<string, unknown>)[key];
    result[key] = value && typeof value === "object" && !Array.isArray(value)
      ? deepMerge(current ?? {}, value)
      : value;
  }
  return result as T;
}
